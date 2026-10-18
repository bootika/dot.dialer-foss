package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.Intent
import android.provider.CallLog
import dev.goodwy.rphone.controller.MainViewModel
import dev.goodwy.rphone.controller.NavigationTarget
import dev.goodwy.rphone.modal.data.CallLogEntry
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.screen.companyColleagues
import dev.goodwy.rphone.view.screen.matchingCallHistory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class QuickCallActionsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun log(id: Long, number: String, type: Int, date: Long) =
        CallLogEntry(id, number, null, type, date, 30, null, null)

    @Test fun copyIncludesOnlyChosenNameAndNumberAndDoesNotRepeatANumberOnlyIdentity() {
        assertEquals("Paul Ionescu\n+40700000101", contactIdentityText(" Paul Ionescu ", "+40700000101"))
        assertEquals("+40700000101", contactIdentityText("+40700000101", "+40700000101"))
        assertEquals("0700000101", contactIdentityText(null, "0700000101"))
    }

    @Test fun selectedTextValidatesOneNumberWithoutExecutingAnyTelecomAction() {
        assertEquals("+40700000101", selectedPhoneNumber(" +40 (700) 000-101 "))
        assertEquals("+40700000101", selectedPhoneNumber("(+40) 700 000 101"))
        assertEquals("+40700000101", selectedPhoneNumber("(+40)700000101"))
        assertEquals("+44700000101", selectedPhoneNumber("(+44) 700 000 101"))
        assertEquals("+12125550101", selectedPhoneNumber("(+1) 212 555 0101"))
        for (text in listOf("(+0) 700000101", "(+4000) 700000101", "((+40)) 700000101", "(+40 700000101", "(+40) +700000101", "(+40) 700000101 / 700000102", "(+40) *100#"))
            assertNull(text, selectedPhoneNumber(text))
        for (text in listOf("", "private", "*100#", "0700000101 / 0700000102", "0700000101 0700000102", "call Paul 0700000101", "+40+700000101"))
            assertNull(text, selectedPhoneNumber(text))
    }

    @Test fun processTextIsAbsentAndCannotRouteWhileOffThenRestoresAndDisablesAgain() = runBlocking {
        val prefs = PreferenceManager(context)
        prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_SELECTED_TEXT_DIALER to false))
        assertFalse(SelectedTextDialerIntegration.isEnabled(context))
        val model = MainViewModel(prefs)
        val input = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "+40 (700) 000-101")
        assertNull(model.getNavigationTarget(input, context))
        prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_SELECTED_TEXT_DIALER to true))
        assertTrue(SelectedTextDialerIntegration.isEnabled(context))
        SelectedTextDialerIntegration.setEnabled(context, false)
        prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_SELECTED_TEXT_DIALER to true))
        assertTrue("Same-value restore must repair the actual component", SelectedTextDialerIntegration.isEnabled(context))
        assertEquals(NavigationTarget.Dialpad("+40700000101"), model.getNavigationTarget(input, context))
        assertEquals(NavigationTarget.Dialpad("+40700000101"), model.getNavigationTarget(input.putExtra(Intent.EXTRA_PROCESS_TEXT,"(+40) 700 000 101"), context))
        assertEquals(NavigationTarget.Search("not a number"), model.getNavigationTarget(input.putExtra(Intent.EXTRA_PROCESS_TEXT,"not a number"), context))
        prefs.restoreAllPreferences(mapOf(PreferenceManager.KEY_SELECTED_TEXT_DIALER to false))
        assertFalse(SelectedTextDialerIntegration.isEnabled(context))
        assertNull(model.getNavigationTarget(input, context))
    }

    @Test fun companyMatchIsExactNormalizedAndExcludesCurrentHiddenPrivateAndDuplicateIds() {
        val rows = listOf(Contact("self", company="Atelier Nord"), Contact("1",givenName="Paul",company=" atelier   NORD "),
            Contact("2",company="Atelier Nord Est"),Contact("3",company="Atelier Nord",isHidden=true),
            Contact("4",company="Atelier Nord",isPrivate=true),Contact("1",company="Atelier Nord"))
        assertEquals(listOf("1"), companyColleagues(rows,"ATELIER NORD","self").map {it.id})
        assertTrue(companyColleagues(rows," ",null).isEmpty())
    }

    @Test fun historyMatchesCanonicalCountryNumbersWithoutLastDigitCollisions() {
        val rows = listOf(log(1,"+40700000101",1,10),log(2,"0700000101",2,20),
            log(3,"+44700000101",1,30),log(4,"101",1,40),log(5,"",1,50))
        assertEquals(listOf(2L,1L), matchingCallHistory(rows,listOf("+40700000101"),"RO").map {it.id})
        assertTrue(matchingCallHistory(rows,listOf(""),"RO").isEmpty())
    }
}
