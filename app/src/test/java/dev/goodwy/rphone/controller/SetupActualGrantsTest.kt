package dev.goodwy.rphone.controller

import android.app.Application
import android.app.role.RoleManager
import android.Manifest
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SetupPermissions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 36], application = Application::class)
class SetupActualGrantsTest {
    @Suppress("DEPRECATION") // Robolectric role shadow supports both tested SDKs through these helpers.
    @Test fun setupMarkerDoesNotReplaceActualRoleOrRuntimeGrants() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager(app)
        prefs.setBoolean(PreferenceManager.KEY_PERMISSION_SETUP_REVIEWED, true)
        runBlocking { prefs.getPersistedPreferences() }
        val roles = shadowOf(app.getSystemService(RoleManager::class.java))
        assertTrue(SetupPermissions.needsSetup(app, prefs))
        roles.addHeldRole(RoleManager.ROLE_DIALER)
        assertTrue(SetupPermissions.needsSetup(app, prefs))
        shadowOf(app).grantPermissions(*SetupPermissions.steps(36).filter { it.required }.map { it.name }.toTypedArray())
        assertFalse(SetupPermissions.needsSetup(app, prefs))
        shadowOf(app).denyPermissions(Manifest.permission.READ_CONTACTS)
        assertTrue(SetupPermissions.needsSetup(app, prefs))
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        roles.removeHeldRole(RoleManager.ROLE_DIALER)
        assertTrue(SetupPermissions.needsSetup(app, prefs))
    }
}
