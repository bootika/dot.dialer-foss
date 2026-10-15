package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import dev.goodwy.rphone.BuildConfig
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=Application::class)
class ContactPhotoAvailabilityTest {
    @Suppress("DEPRECATION") // API29-compatible PackageManager overload, independently inspect merged manifest.
    @Test fun shippedManifestAndRuntimeAvailabilityAgreeWithoutChangingPhotoProfiles() {
        val context=RuntimeEnvironment.getApplication()
        val present=try {
            context.packageManager.getServiceInfo(ComponentName(context,WhatsAppAvatarNotificationListener::class.java),0)
            true
        } catch (_: PackageManager.NameNotFoundException) { false }
        assertEquals(BuildConfig.NOTIFICATION_AVATARS_AVAILABLE,present)
        assertEquals(present,ContactOptimizationAccess.supported(context))
        val profiles=ContactOptimizationProfiles(context)
        val before=profiles.settings("test@example.test")
        if(!present) {
            assertFalse(ContactOptimizationAccess.granted(context))
            ContactOptimizationAccess.open(context)
            assertNull(org.robolectric.Shadows.shadowOf(context).nextStartedActivity)
        }
        assertEquals(before,profiles.settings("test@example.test"))
        assertTrue(before.gravatar && before.business && before.google)
    }
}
