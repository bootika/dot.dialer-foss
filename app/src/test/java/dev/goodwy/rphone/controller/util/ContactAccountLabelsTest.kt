package dev.goodwy.rphone.controller.util

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AuthenticatorDescription
import android.content.Context
import android.content.ContextWrapper
import dev.goodwy.rphone.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ContactAccountLabelsTest {
    private val email = "test.alexandru.butica@example.com"

    @Test fun `same address different provider is not a duplicate account`() {
        val google = Account(email, "com.google")
        val second = Account(email, "org.example.second")
        assertEquals(2, linkedSetOf(google, second, google).size)
        assertEquals("Google", contactAccountProviderName(google, null))
        assertEquals("Samsung account", contactAccountProviderName(second, "Samsung account"))
        assertNotEquals("${google.type}|${google.name}", "${second.type}|${second.name}")
    }

    @Test fun `unknown email does not imply google or samsung`() {
        val account = Account(email, "org.example.archive")
        assertEquals(account.type, contactAccountProviderName(account, null))
        assertEquals(account.type, contactAccountProviderName(account, "  "))
        assertEquals(account.type, contactAccountProviderName(account, email))
    }

    @Test fun `system label is trimmed and used independently of account name`() {
        val account = Account(email, "org.example.archive")
        assertEquals("Archive", contactAccountProviderName(account, "  Archive  "))
        assertEquals("Google localized", contactAccountProviderName(Account(email, "com.google"), "Google localized"))
    }

    @Test fun `existing providers keep known fallback labels`() {
        for ((type, label) in listOf("com.google" to "Google", "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram", "com.xiaomi" to "Mi Account", "SIM_1" to "SIM Card")) {
            assertEquals(label, contactAccountProviderName(Account(email, type), null))
        }
        assertEquals("Private (App Only)", ContactUtils.getFriendlyAccountName(null, true))
        assertEquals("Local (Device Only)", ContactUtils.getFriendlyAccountName(null))
    }

    @Test fun `app named accounts retain declared provider labels`() {
        for ((type, name) in listOf("com.whatsapp" to "WhatsApp",
            "com.google.android.apps.tachyon" to "Meet", "org.example.vendor" to "OPPO")) {
            assertEquals(name, contactAccountProviderName(Account(name, type), "  $name  "))
        }
        assertEquals("Profile sharing", contactAccountProviderName(Account(email, "org.example.profile"), "Profile sharing"))
    }

    @Test fun `known fallback is independent of app named account`() {
        for ((type, label) in listOf("com.google" to "Google", "com.whatsapp" to "WhatsApp",
            "org.telegram.messenger" to "Telegram", "com.xiaomi" to "Mi Account", "SIM_1" to "SIM Card")) {
            val account = Account(label, type)
            assertEquals(label, contactAccountProviderName(account, null))
            assertEquals(label, contactAccountProviderName(account, "  "))
            assertEquals(label, ContactUtils.getFriendlyAccountName(account))
        }
    }

    @Test fun `unknown account name is not evidence of provider`() {
        val account = Account("Meet", "org.example.archive")
        assertEquals(account.type, contactAccountProviderName(account, null))
        assertEquals("Meet", ContactUtils.getFriendlyAccountName(account))
    }

    @Test fun `observed Meet type has readable fallback without changing legacy results`() {
        val type = "com.google.android.apps.tachyon"
        for (name in listOf("Meet", email)) {
            val account = Account(name, type)
            assertEquals("Meet", contactAccountProviderName(account, null))
            assertEquals("Meet", contactAccountProviderName(account, "  "))
            assertEquals(name, ContactUtils.getFriendlyAccountName(account))
        }
    }

    @Test fun `email metadata is not a provider title`() {
        val otherAddress = "another.person@example.com"
        val unknown = Account("Archive", "org.example.archive")
        assertEquals(unknown.type, contactAccountProviderName(unknown, otherAddress))
        assertEquals("Google", contactAccountProviderName(Account(email, "com.google"), otherAddress))
    }

    @Test fun `loader reads authenticator label resources and ignores missing resources`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = shadowOf(AccountManager.get(context))
        manager.addAuthenticator(AuthenticatorDescription("org.example.available", context.packageName, R.string.app_name, 0, 0, 0))
        manager.addAuthenticator(AuthenticatorDescription("org.example.missing.package", "org.example.absent", 1, 0, 0, 0))
        manager.addAuthenticator(AuthenticatorDescription("org.example.missing.resource", context.packageName, 0x7fffffff, 0, 0, 0))
        manager.addAuthenticator(AuthenticatorDescription("org.example.empty", context.packageName, 0, 0, 0, 0))
        assertEquals(mapOf("org.example.available" to context.getString(R.string.app_name)), loadContactAccountProviderLabels(context))
    }

    @Test fun `denied authenticator access yields safe fallback without accounts writes`() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getSystemService(name: String): Any? {
                if (name == Context.ACCOUNT_SERVICE) throw SecurityException("Unavailable metadata")
                return super.getSystemService(name)
            }
        }
        assertTrue(loadContactAccountProviderLabels(context).isEmpty())
    }
}
