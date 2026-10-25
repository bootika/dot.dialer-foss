package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import dev.goodwy.rphone.controller.MainViewModel
import dev.goodwy.rphone.controller.NavigationTarget
import org.junit.Assert.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class ExternalTelNumberTest {
    @Test fun visitedCountryOnlyOverridesHomeSimForExternalNationalInput() {
        assertEquals("DE",externalTelRegion("de","ro","RO"))
        assertEquals("DE",externalTelRegion("DE",null,"RO"))
        assertEquals("RO",externalTelRegion(null,"RO","RO"))
        assertEquals("RO",externalTelRegion("ZZ","RO","RO"))
        assertEquals("IT",externalTelRegion("RO","RO","IT"))
    }
    @Test fun actualAndroidFormatterHandlesBothReportedGermanNumbersAndItalianZero() {
        assertEquals("+493614283555",normalizeExternalTelNumber("03614283555",true,"DE","RO","RO",PhoneNumberUtils::formatNumberToE164))
        assertEquals("+4930266424242",normalizeExternalTelNumber("030266424242",true,"DE","RO","RO",PhoneNumberUtils::formatNumberToE164))
        assertEquals("+390612345678",normalizeExternalTelNumber("0612345678",true,"IT","RO","RO",PhoneNumberUtils::formatNumberToE164))
    }
    @Test fun internationalShortServiceParameterAndDisabledInputsRemainUntouched() {
        for(number in listOf("+4930266424242"," +493614283555","00493614283555","112","911","*100#","*#06#","030266424242;123","030266424242,123","030266424242;phone-context=+49")) {
            assertEquals(number,normalizeExternalTelNumber(number,true,"DE","RO","RO") {_,_->fail("Do not rewrite this input");null})
        }
        assertEquals("030266424242",normalizeExternalTelNumber("030266424242",false,"DE","RO","RO") {_,_->fail("Disabled");null})
        assertEquals("030266424242",normalizeExternalTelNumber("030266424242",true,"DE","RO","RO") {_,_->null})
    }
    @Test fun formatterExceptionPreservesTheOriginalNumber() {
        assertEquals("030266424242", normalizeExternalTelNumber("030266424242",true,"DE","RO","RO") {_,_-> throw IllegalArgumentException("Unavailable formatter")})
    }
    @Test fun realIntentBoundaryUsesNetworkButContactOutgoingKeepsItsHomeRegion() = runBlocking {
        val app=RuntimeEnvironment.getApplication()
        val telephony=shadowOf(app.getSystemService(TelephonyManager::class.java))
        telephony.setSimCountryIso("ro");telephony.setNetworkCountryIso("de")
        val prefs=PreferenceManager(app)
        prefs.setString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,null)
        prefs.setBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,true)
        PreferenceWriteBarrier.awaitPending()
        withTimeout(5000) { prefs.settingsChanged.first { prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,null)==null && prefs.getBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,false) } }
        val vm=MainViewModel(prefs)
        for(action in listOf(Intent.ACTION_VIEW,Intent.ACTION_DIAL,Intent.ACTION_CALL)) {
            val target=vm.getNavigationTarget(Intent(action,Uri.parse("tel:030266424242")),app) as NavigationTarget.Dialpad
            assertEquals("+4930266424242",target.number)
        }
        assertEquals("+40722123456",normalizeOutgoingNumber(app,"0722123456",null,prefs))
        assertEquals("+4930266424242",normalizeOutgoingNumber(app,"+4930266424242",null,prefs))
        prefs.setString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,"RO")
        PreferenceWriteBarrier.awaitPending()
        withTimeout(5000) { prefs.settingsChanged.first { prefs.getString(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO,null)=="RO" } }
        assertEquals(PhoneNumberUtils.formatNumberToE164("030266424242","RO") ?: "030266424242",
            (vm.getNavigationTarget(Intent(Intent.ACTION_DIAL,Uri.parse("tel:030266424242")),app) as NavigationTarget.Dialpad).number)
        val prepared=Intent(Intent.ACTION_DIAL,Uri.parse("tel:030266424242")).putExtra(EXTRA_PREPARED_TEL_NUMBER,true)
        assertEquals("030266424242",(vm.getNavigationTarget(prepared,app) as NavigationTarget.Dialpad).number)
        prefs.setBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,false)
        PreferenceWriteBarrier.awaitPending()
        withTimeout(5000) { prefs.settingsChanged.first { !prefs.getBoolean(PreferenceManager.KEY_OUTGOING_NUMBER_NORMALIZATION,true) } }
        assertEquals("030266424242",(vm.getNavigationTarget(Intent(Intent.ACTION_DIAL,Uri.parse("tel:030266424242")),app) as NavigationTarget.Dialpad).number)
    }
}
