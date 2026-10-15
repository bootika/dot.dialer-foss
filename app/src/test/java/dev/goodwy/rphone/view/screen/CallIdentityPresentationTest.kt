package dev.goodwy.rphone.view.screen

import org.junit.Assert.*
import org.junit.Test

class CallIdentityPresentationTest {
    @Test fun missingFullPhotoAlwaysKeepsIdentityAcrossSavedVisibilityOptions() {
        for (show in listOf(false, true)) for (hide in listOf(false, true)) for (background in listOf(false, true)) {
            assertTrue(showCallIdentityAvatar(true, false, show, hide, background))
            assertFalse(showCallIdentityAvatar(true, true, show, hide, background))
        }
    }
    @Test fun pendingFullPhotoNeverShowsAnInitialCircle() {
        for (show in listOf(false,true)) for (hide in listOf(false,true)) for (background in listOf(false,true)) {
            assertFalse(showCallIdentityAvatar(true,false,show,hide,background,photoLookupComplete=false))
        }
    }
    @Test fun normalAppearanceRetainsVisibilityAndBackgroundPreferences() {
        for (photo in listOf(false, true)) for (show in listOf(false, true))
            for (hide in listOf(false, true)) for (background in listOf(false, true)) {
                assertEquals(show && !(hide && background && photo),
                    showCallIdentityAvatar(false, photo, show, hide, background))
            }
    }
}
