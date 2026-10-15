package dev.goodwy.rphone.controller

import android.Manifest
import dev.goodwy.rphone.controller.util.SetupPermissions
import dev.goodwy.rphone.view.theme.isSetupTransition
import com.ramcosta.composedestinations.generated.destinations.DefaultDialerScreenDestination
import org.junit.Assert.*
import org.junit.Test

class SetupPermissionsTest {
    @Test fun recommendedQueueChecksEachActualGrantAndSupportsPlatformVersions() {
        for (api in listOf(29, 31, 33, 36, 37)) {
            val steps = SetupPermissions.steps(api)
            val grants = mutableSetOf<String>()
            assertEquals(if (api < 31) 7 else if (api < 33) 8 else 9, steps.size)
            for (step in steps) {
                assertEquals(step, SetupPermissions.next(api, grants::contains))
                grants += step.name
            }
            assertNull(SetupPermissions.next(api, grants::contains))
            assertFalse(steps.any { it.name == Manifest.permission.RECORD_AUDIO ||
                it.name == Manifest.permission.SYSTEM_ALERT_WINDOW || it.name == Manifest.permission.GET_ACCOUNTS })
        }
    }
    @Test fun groupedGrantsAreSkippedAndCoreCannotBeBypassed() {
        val steps = SetupPermissions.steps(37)
        val core = steps.filter { it.required }.map { it.name }.toSet()
        assertEquals(Manifest.permission.READ_CONTACTS, SetupPermissions.next(37, { false }, core)?.name)
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, SetupPermissions.next(37, core::contains)?.name)
        assertNull(SetupPermissions.next(37, core::contains, steps.filterNot { it.required }.map { it.name }.toSet()))
    }
    @Test fun onlySetupRoutesDisableNavigationAnimation() {
        val setup = DefaultDialerScreenDestination.route
        assertTrue(isSetupTransition(setup, "recent_screen"))
        assertTrue(isSetupTransition("recent_screen", "$setup?return=true"))
        assertFalse(isSetupTransition("recent_screen", "settings_screen"))
        assertFalse(isSetupTransition(null, null))
    }
}
