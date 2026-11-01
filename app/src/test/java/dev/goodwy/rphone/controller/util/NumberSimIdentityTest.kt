package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.ComponentName
import android.telecom.PhoneAccountHandle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[29,36],application=Application::class)
class NumberSimIdentityTest {
    private val context get()=RuntimeEnvironment.getApplication()
    private fun prefs(values:Map<String,Any> = emptyMap()):PreferenceManager = PreferenceManager(context).also {
        runBlocking { it.restoreAllPreferences(mapOf(PreferenceManager.KEY_OUTGOING_NUMBER_REGION_ISO to "RO")+values,replacePortable=true) }
    }
    private fun fresh(p:PreferenceManager):PreferenceManager {
        runBlocking { p.getPersistedPreferences() };return PreferenceManager(context)
    }
    private fun account(id:String):CallingAccountInfo {
        val h=PhoneAccountHandle(ComponentName("example.phone","Calls"),id)
        return CallingAccountInfo(h,h.toPreferenceKey(),"Network $id",null,0,null,true)
    }
    @Test fun nationalInternationalAndSpacingShareOnePersistedChoice() {
        val p=prefs();p.setFavoriteSimForNumber("0723 123 456","A")
        val saved=fresh(p)
        for(n in listOf("0723123456","+40723123456","+40 (723) 123-456","0040723123456")) assertEquals(n,"A",saved.getFavoriteSimForNumber(n))
        saved.setFavoriteSimForNumber("+40723123456","B")
        assertEquals("B",fresh(saved).getFavoriteSimForNumber("0723123456"))
    }
    @Test fun oldNationalAndInternationalSelectionsAreReadWithoutWrites() {
        for(key in listOf("favorite_sim_number_0723123456","favorite_sim_number_40723123456")) {
            val p=prefs(mapOf(key to "A"));val before=runBlocking { p.getPersistedPreferences() }
            assertEquals("A",p.getFavoriteSimForNumber("0723123456"));assertEquals("A",p.getFavoriteSimForNumber("+40723123456"))
            assertEquals(before,runBlocking { p.getPersistedPreferences() })
        }
    }
    @Test fun conflictingOldChoicesAskInsteadOfUsingContactOrGlobalAndExplicitChoiceResolvesThem() {
        val a=account("A");val b=account("B")
        val p=prefs(mapOf("favorite_sim_number_0723123456" to a.key,"favorite_sim_number_40723123456" to b.key,
            "favorite_sim_contact" to a.key,PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT to b.key))
        for(n in listOf("0723123456","+40723123456")) {
            assertEquals(NumberSimIdentities.CONFLICT,p.getFavoriteSimForNumber(n))
            assertNull(preferredOutgoingAccount(context,p,n,"contact",listOf(a,b,account(NumberSimIdentities.CONFLICT))))
        }
        p.setFavoriteSimForNumber("0723123456",a.key);val next=fresh(p)
        assertEquals(a.handle,preferredOutgoingAccount(context,next,"+40723123456","contact",listOf(b,a)))
        val stored=runBlocking { next.getPersistedPreferences() }
        assertFalse(stored.containsKey("favorite_sim_number_0723123456"));assertFalse(stored.containsKey("favorite_sim_number_40723123456"))
    }
    @Test fun resetThroughEitherFormDoesNotResurrectLegacyOverrides() {
        val p=prefs(mapOf("favorite_sim_number_0723123456" to "A","favorite_sim_number_40723123456" to "A"))
        p.setFavoriteSimForNumber("+40723123456","B");val a=fresh(p)
        a.setFavoriteSimForNumber("0723123456",null);val b=fresh(a)
        assertNull(b.getFavoriteSimForNumber("0723123456"));assertNull(b.getFavoriteSimForNumber("+40723123456"))
    }
    @Test fun countriesAndFullLengthCannotCollideAndTruncatedLegacyIsNotGuessed() {
        val a="+4915123456789";val b="+44915123456789"
        assertEquals(a.filter(Char::isDigit).takeLast(12),b.filter(Char::isDigit).takeLast(12))
        val p=prefs(mapOf("favorite_sim_number_"+a.filter(Char::isDigit).takeLast(12) to "old"))
        assertEquals(NumberSimIdentities.CONFLICT,p.getFavoriteSimForNumber(a));assertEquals(NumberSimIdentities.CONFLICT,p.getFavoriteSimForNumber(b))
        p.setFavoriteSimForNumber(a,"A");p.setFavoriteSimForNumber(b,"B");val s=fresh(p)
        assertEquals("A",s.getFavoriteSimForNumber(a));assertEquals("B",s.getFavoriteSimForNumber(b))
        s.setFavoriteSimForNumber(a,null);val cleared=fresh(s)
        assertNull(cleared.getFavoriteSimForNumber(a));assertEquals("B",cleared.getFavoriteSimForNumber(b))
        assertNotEquals(NumberSimIdentities.resolve("0723123456","RO")?.key,NumberSimIdentities.resolve("0723123456","DE")?.key)
    }
    @Test fun codesPostdialAndUnknownCountryStayExact() {
        val p=prefs();for((i,n) in listOf("222","*222","*#222#","0723123456,12","0723123456;12").withIndex())p.setFavoriteSimForNumber(n,"A$i")
        val s=fresh(p);for((i,n) in listOf("222","*222","*#222#","0723123456,12","0723123456;12").withIndex())assertEquals("A$i",s.getFavoriteSimForNumber(n))
        assertNotEquals(NumberSimIdentities.resolve("0723123456",null)?.key,NumberSimIdentities.resolve("+40723123456",null)?.key)
        assertNull(NumberSimIdentities.resolve("  ","RO"))
    }
    @Test fun globalAndOnceKeepNumberExceptionAndPortableRestoreKeepsCanonicalChoice() {
        val a=account("A");val b=account("B");val p=prefs()
        rememberOutgoingAccount(p,"0723123456",a.key,1);val s=fresh(p)
        rememberOutgoingAccount(s,"+40723123456",b.key,2);rememberOutgoingAccount(s,"+40723123456",b.key,0)
        val fresh=fresh(s);assertEquals(a.handle,preferredOutgoingAccount(context,fresh,"+40723123456",null,listOf(a,b)))
        assertEquals(b.handle,preferredOutgoingAccount(context,fresh,"+40723123457",null,listOf(a,b)))
        val backup=runBlocking { fresh.getPersistedPreferences() };val restored=prefs(backup)
        assertEquals(a.key,restored.getFavoriteSimForNumber("0723123456"))
    }
    @Test fun repeatedVisibleNumbersShareRegionLookupAndExplicitRefreshInvalidatesIt() {
        var reads=0;var revision=0
        val store=NumberSimPreferences(getSharedDataStore(context), { androidx.datastore.preferences.core.emptyPreferences() }, { reads++;"RO" }, { revision })
        repeat(100) { store.get("0723123456") }
        assertEquals(1,reads)
        revision++;store.get("+40723123456");assertEquals(2,reads)
        store.refreshRegion();store.get("0723123456");assertEquals(3,reads)
    }
    @Test fun nativeAndInternationalMatchForOtherCountryPlans() {
        for((region,national,international) in listOf(Triple("DE","03614283555","+493614283555"),Triple("FR","0612345678","+33612345678"),Triple("GB","02079460018","+442079460018"),Triple("IT","0212345678","+390212345678"))) {
            assertEquals(region,NumberSimIdentities.resolve(international,region)?.key,NumberSimIdentities.resolve(national,region)?.key)
        }
    }
}
