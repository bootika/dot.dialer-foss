package dev.goodwy.rphone.controller.util

import android.app.Application
import android.content.ContextWrapper
import dev.goodwy.rphone.R
import io.github.bootika.dotdialer.core.preferences.PreferenceBackupPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupRulesAndTypesTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `Android cloud and device transfer include actual storage and exclude secrets and unrelated data`() {
        val required = setOf("device_file:datastore/rill_prefs.preferences_pb", "file:datastore/rill_prefs.preferences_pb",
            "device_file:call_backgrounds/", "file:call_backgrounds/", "external:Notes/")
        for (resource in listOf(R.xml.backup_rules, R.xml.data_extraction_rules)) {
            val groups = linkedMapOf<String, MutableSet<String>>()
            var group = "legacy"
            context.resources.getXml(resource).use { parser ->
                while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        if (parser.name in setOf("cloud-backup", "device-transfer")) group = parser.name
                        if (parser.name == "include") groups.getOrPut(group) { mutableSetOf() }
                            .add("${parser.getAttributeValue(null, "domain")}:${parser.getAttributeValue(null, "path")}")
                    }
                    parser.next()
                }
            }
            assertEquals(if (resource == R.xml.backup_rules) setOf("legacy") else setOf("cloud-backup", "device-transfer"), groups.keys)
            groups.values.forEach { assertEquals(required, it) }
        }
    }

    @Test fun `device file storage migrates CE bytes durably and preserves newer DE data`() {
        val root = File(context.cacheDir, "migration-case").apply { mkdirs() }
        val credential = File(root, "credential").apply { mkdirs() }
        val device = File(root, "device").apply { mkdirs() }
        val deviceContext = object : ContextWrapper(context) { override fun getFilesDir() = device }
        val app = object : ContextWrapper(context) {
            override fun getApplicationContext() = this
            override fun getFilesDir() = credential
            override fun createDeviceProtectedStorageContext() = deviceContext
        }
        val source = File(credential, DevicePreferenceStorage.RELATIVE_PATH).apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
        val migrated = DevicePreferenceStorage.file(app)
        assertEquals(File(device, DevicePreferenceStorage.RELATIVE_PATH), migrated)
        assertArrayEquals(byteArrayOf(1, 2, 3), migrated.readBytes())
        assertFalse(source.exists())
        migrated.writeBytes(byteArrayOf(4, 5))
        source.writeBytes(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(4, 5), DevicePreferenceStorage.file(app).readBytes())
        root.deleteRecursively()
    }

    @Test fun `declared preference keys and dynamic families never silently lose supported types`() {
        val keys = PreferenceManager::class.java.fields.filter { it.name.startsWith("KEY_") && it.type == String::class.java }
            .map { it.get(null) as String }.toSet()
        assertTrue(keys.size >= 200)
        val values = listOf<Any>(false, Int.MIN_VALUE, 1L, Long.MAX_VALUE, 0f, 1f, 0.25, "Test Alexandru Butica", setOf("RO", "US"))
        val dynamic = setOf("favorite_number_42", "favorite_email_42", "favorite_sim_42", "last_used_number_42",
            "favorite_sim_number_447700900123", "contact_background_42", "contact_background_num_700900123")
        for (value in values) {
            val source = (keys + dynamic).filter(PreferenceBackupPolicy::isPortable).associateWith { value }
            val actual = BackupPreferencesJson.decode(BackupPreferencesJson.encode(source))
            assertEquals(source, actual)
            if (value !is Set<*>) actual.values.forEach { assertEquals(value.javaClass, it.javaClass) }
        }
    }

    @Test fun `malformed types platform and missing assets fail before preferences change`() = runBlocking<Unit> {
        val prefs = PreferenceManager(context)
        prefs.restoreAllPreferences(mapOf("retained" to "Test Alexandru Butica"))
        for (entries in listOf(
            mapOf("prefs.json" to """{"data":{"retained":1},"meta":{"retained":"string"},"complete":true}"""),
            mapOf("prefs.json" to """{"data":{"retained":"changed"},"meta":{},"complete":"yes"}"""),
            mapOf("prefs.json" to """{"retained":"changed"}""", "platform.json" to """{"application_locales":123}"""),
            mapOf("prefs.json" to """{"contact_background_default":"backup-background:00000000-0000-0000-0000-000000000000.jpg"}""")
        )) {
            val archive = File.createTempFile("invalid_", ".rphone", context.cacheDir)
            ZipOutputStream(archive.outputStream()).use { zip -> entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data.toByteArray()); zip.closeEntry()
            } }
            assertFalse(BackupManager.restoreBackup(context, archive))
            assertEquals("Test Alexandru Butica", prefs.getString("retained", null))
            archive.delete()
        }
    }

    @Test fun `ignored traversal entry is bounded and never writes outside notes`() = runBlocking<Unit> {
        val archive = File.createTempFile("bounded_", ".rphone", context.cacheDir)
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("prefs.json")); zip.write("{}".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("notes/../../invalid.txt")); zip.write(ByteArray(1024 * 1024 + 1)); zip.closeEntry()
        }
        assertFalse(BackupManager.restoreBackup(context, archive))
        assertFalse(File(context.filesDir, "invalid.txt").exists())
        archive.delete()
    }
}
