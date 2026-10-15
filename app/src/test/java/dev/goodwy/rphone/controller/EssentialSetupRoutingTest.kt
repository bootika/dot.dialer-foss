package dev.goodwy.rphone.controller

import android.app.Application
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.goodwy.rphone.controller.util.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class EssentialSetupRoutingTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val model = MainViewModel(PreferenceManager(context))
    @Test fun emptyDialOpensDialpad() {
        assertEquals(NavigationTarget.Dialpad(""), model.getNavigationTarget(Intent(Intent.ACTION_DIAL), context))
    }
    @Test fun addCallBackContractIsPreserved() {
        assertEquals(NavigationTarget.Recents, model.getNavigationTarget(
            Intent(Intent.ACTION_DIAL).putExtra(dev.goodwy.rphone.MainActivity.EXTRA_RETURN_TO_CALL_ON_BACK, true), context))
    }
    @Test fun phoneButtonOpensDialpadWithoutActiveCall() {
        assertEquals(NavigationTarget.Dialpad(""), model.getNavigationTarget(Intent(Intent.ACTION_CALL_BUTTON), context))
    }
    @Test fun searchReceivesTheSuppliedQuery() {
        assertEquals(NavigationTarget.Search("George"), model.getNavigationTarget(
            Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "George"), context))
    }
    @Test fun callLogAndTelephoneViewsHaveSeparateTargets() {
        assertEquals(NavigationTarget.Recents, model.getNavigationTarget(
            Intent(Intent.ACTION_VIEW, Uri.parse("content://call_log/calls/12")), context))
        assertEquals(NavigationTarget.Dialpad("+40712345678"), model.getNavigationTarget(
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:+40712345678")), context))
        assertNull(model.getNavigationTarget(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org/calls")), context))
        assertNull(model.getNavigationTarget(Intent(Intent.ACTION_VIEW, Uri.parse("content://other/calls")), context))
    }
    @Test fun callLogItemMimeWorks() {
        assertEquals(NavigationTarget.Recents, model.getNavigationTarget(
            Intent(Intent.ACTION_VIEW).setType("vnd.android.cursor.item/calls"), context))
    }
}
