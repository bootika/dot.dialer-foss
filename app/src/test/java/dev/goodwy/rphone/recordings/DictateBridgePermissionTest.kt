package dev.goodwy.rphone.recordings

import android.app.Application
import android.content.ContextWrapper
import android.content.ServiceConnection
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.os.IBinder
import android.content.pm.SigningInfo
import android.content.pm.Signature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import io.github.bootika.dotdialer.core.recordings.DictateCapabilities
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 30, 36], application = Application::class)
class DictateBridgePermissionTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val normal = PermissionInfo.PROTECTION_NORMAL
    private fun failure(permission: String?, owner: String? = "android",
        level: Int = normal, granted: Boolean = true) =
        DictateBridgePermission.failure(permission, owner, level, granted)

    private fun installService(permission: String?) {
        val info = ApplicationInfo().apply { packageName = DictateProtocol.PACKAGE }
        shadowOf(app.packageManager).installPackage(PackageInfo().apply {
            packageName = DictateProtocol.PACKAGE
            applicationInfo = info
            signingInfo = ReflectionHelpers.callConstructor(SigningInfo::class.java).also {
                shadowOf(it).setSignatures(arrayOf(Signature(byteArrayOf(1, 2, 3))))
            }
            services = arrayOf(ServiceInfo().apply {
                packageName = DictateProtocol.PACKAGE; name = DictateProtocol.SERVICE
                applicationInfo = info; exported = true; this.permission = permission
            })
        })
    }

    // The standalone Robolectric Application omits normal framework PermissionInfo entries.
    // Supply platform metadata only; production permission/grant checks remain unchanged.
    private fun installFrameworkPermissionMetadata() {
        val permission = ReflectionHelpers.callConstructor(PermissionInfo::class.java).apply {
            name = DictateBridgePermission.REQUIRED; packageName = "android"
        }
        ReflectionHelpers.setField(permission, "protectionLevel", normal)
        shadowOf(app.packageManager).addPermissionInfo(permission)
    }

    @Test fun `legacy provider does not require a new permission grant`() {
        assertNull(failure(null, owner = null, granted = false))
        assertNull(failure("", owner = null, granted = false))
        installService(null)
        DictateBridgePermission.check(app)
    }

    @Test fun `expected permission and granted state are accepted without replacing caller authentication`() {
        assertNull(failure(DictateBridgePermission.REQUIRED))
        assertEquals("android.permission.INTERNET", DictateBridgePermission.REQUIRED)
    }

    @Test fun `missing grant is recoverable and cannot pass the gate`() {
        assertEquals("BRIDGE_PERMISSION_REQUIRED", failure(DictateBridgePermission.REQUIRED, granted = false))
    }

    @Test fun `unexpected permission owner name or protection cannot become an accepted fallback`() {
        val required = DictateBridgePermission.REQUIRED
        assertEquals("UNTRUSTED_SERVICE", failure("android.permission.ACCESS_NETWORK_STATE"))
        assertEquals("UNTRUSTED_SERVICE", failure(required, owner = "example.other"))
        assertEquals("UNTRUSTED_SERVICE", failure(required, owner = DictateProtocol.PACKAGE))
        assertEquals("UNTRUSTED_SERVICE", failure("ro.dotdesign.dotdictate.permission.DIALER_BRIDGE"))
        assertEquals("UNTRUSTED_SERVICE", failure(required, level = PermissionInfo.PROTECTION_DANGEROUS))
        assertEquals("UNTRUSTED_SERVICE", failure(required, level = PermissionInfo.PROTECTION_SIGNATURE))
    }

    @Test fun `installed expected normal permission uses the actual package grant`() {
        val required = DictateBridgePermission.REQUIRED
        installService(required)
        installFrameworkPermissionMetadata()
        val permission = app.packageManager.getPermissionInfo(required, 0)
        assertEquals("android", permission.packageName)
        assertEquals(normal, permission.protection)
        shadowOf(app).denyPermissions(required)
        try { DictateBridgePermission.check(app); fail("Missing grant accepted") }
        catch (error: DictateFailure) { assertEquals("BRIDGE_PERMISSION_REQUIRED", error.code) }
        shadowOf(app).grantPermissions(required)
        DictateBridgePermission.check(app)
    }

    @Test fun `framework permission is packaged without phantom custom permission requests`() {
        val permissions = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty().toSet()
        assertTrue(DictateBridgePermission.REQUIRED in permissions)
        assertFalse("ro.dotdesign.dotdictate.permission.DIALER_BRIDGE" in permissions)
        assertFalse("ro.dotdesign.dotdictation.permission.DIALER_BRIDGE" in permissions)
    }
    @Test fun `failed preflight clears a cached binding capabilities and pending requests`() = runBlocking {
        val required = DictateBridgePermission.REQUIRED
        installService(required)
        installFrameworkPermissionMetadata()
        val permission = app.packageManager.getPermissionInfo(required, 0)
        assertEquals("android", permission.packageName)
        assertEquals(normal, permission.protection)
        shadowOf(app).denyPermissions(required)
        val digest = MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1, 2, 3))
            .joinToString("") { "%02x".format(it) }
        app.getSharedPreferences(DictateProtocol.PAIRING_PREFERENCES, 0).edit().putString("certificate", digest).commit()
        var unbound = false
        val context = object : ContextWrapper(app) {
            override fun unbindService(connection: ServiceConnection) { unbound = true }
        }
        val client = DictateBridgeClient(context)
        ReflectionHelpers.setField(client, "remote", Messenger(Handler(Looper.getMainLooper())))
        ReflectionHelpers.setField(client, "connection", object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) = Unit
            override fun onServiceDisconnected(name: ComponentName) = Unit
        })
        ReflectionHelpers.getField<MutableStateFlow<DictateCapabilities>>(client, "mutable").value = DictateCapabilities(connected = true)
        val previous = CompletableDeferred<Bundle>()
        val pending = ReflectionHelpers.getField<ConcurrentHashMap<String, Pair<Int, CompletableDeferred<Bundle>>>>(client, "pending")
        pending["previous-request"] = DictateProtocol.STATUS to previous
        try { client.request(DictateProtocol.CAPABILITIES, Bundle()); fail("Cached binding bypassed missing grant") }
        catch (error: DictateFailure) { assertEquals("BRIDGE_PERMISSION_REQUIRED", error.code) }
        assertTrue(unbound)
        assertFalse(client.capabilities.value.connected)
        assertNull(ReflectionHelpers.getField<Messenger?>(client, "remote"))
        assertTrue(pending.isEmpty())
        assertTrue(previous.isCompleted)
        try { previous.await(); fail("Pending request remained successful") }
        catch (error: DictateFailure) { assertEquals("DISCONNECTED", error.code) }
        assertTrue("Permission failure must preserve explicit pairing", client.trusted())
    }
    @Test fun `actual installed service permission is inspected before binding`() {
        installService("android.permission.ACCESS_NETWORK_STATE")
        try { DictateBridgePermission.check(app); fail("Unexpected service permission accepted") }
        catch (error: DictateFailure) { assertEquals("UNTRUSTED_SERVICE", error.code) }
    }

    @Test fun `missing service fails as unsupported instead of binding an implicit substitute`() {
        try { DictateBridgePermission.check(app); fail("Missing service accepted") }
        catch (error: DictateFailure) { assertEquals("UNSUPPORTED", error.code) }
    }
}