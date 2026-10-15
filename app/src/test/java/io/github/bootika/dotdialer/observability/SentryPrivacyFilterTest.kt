package io.github.bootika.dotdialer.observability

import io.sentry.Breadcrumb
import io.sentry.SentryEvent
import io.sentry.protocol.Message
import io.sentry.protocol.Request
import io.sentry.protocol.SentryException
import io.sentry.protocol.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SentryPrivacyFilterTest {
    @Test
    fun `scrubber removes identity request extras and unsafe breadcrumbs`() {
        val event = SentryEvent().apply {
            user = User().apply { email = "person@example.test" }
            request = Request().apply { url = "tel:+40123456789" }
            serverName = "personal-device-name"
            extras = mapOf("phone_number" to "+40123456789")
            breadcrumbs = listOf(
                Breadcrumb().apply {
                    category = "ui.click"
                    message = "Call +40123456789"
                },
                Breadcrumb().apply {
                    category = "dot_dialer.lifecycle"
                    message = "call_removed | session=call-1 | remaining_calls=0"
                },
            )
        }

        val scrubbed = SentryPrivacyFilter.scrub(event)

        assertNull(scrubbed.user)
        assertNull(scrubbed.request)
        assertNull(scrubbed.serverName)
        assertEquals(emptyMap<String, Any>(), scrubbed.extras)
        assertEquals(1, scrubbed.breadcrumbs?.size)
        assertEquals("dot_dialer.lifecycle", scrubbed.breadcrumbs?.single()?.category)
    }

    @Test
    fun `scrubber redacts exception and message values but preserves event shape`() {
        val event = SentryEvent().apply {
            message = Message().apply {
                message = "Failure while calling +40123456789"
                formatted = "Failure while calling %s"
                params = listOf("+40123456789")
            }
            exceptions = listOf(
                SentryException().apply {
                    type = "IllegalStateException"
                    value = "Contact Jane Doe at +40123456789"
                }
            )
        }

        val scrubbed = SentryPrivacyFilter.scrub(event)

        assertEquals("Dot Dialer diagnostic event", scrubbed.message?.message)
        assertNull(scrubbed.message?.formatted)
        assertEquals(emptyList<String>(), scrubbed.message?.params)
        assertEquals("IllegalStateException", scrubbed.exceptions?.single()?.type)
        assertEquals("[redacted]", scrubbed.exceptions?.single()?.value)
    }
}
