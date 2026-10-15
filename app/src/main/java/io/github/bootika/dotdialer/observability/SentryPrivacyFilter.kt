package io.github.bootika.dotdialer.observability

import io.sentry.SentryEvent

/**
 * Last-line privacy boundary for every event leaving the device.
 *
 * Dot Dialer deliberately keeps stack traces and coarse device/app context, but removes values
 * that could identify a person or expose phone/contact content. Only breadcrumbs produced from
 * typed [io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent] values are retained.
 */
internal object SentryPrivacyFilter {
    private const val SAFE_BREADCRUMB_CATEGORY = "dot_dialer.lifecycle"
    private const val REDACTED_EVENT_MESSAGE = "Dot Dialer diagnostic event"
    private const val REDACTED_EXCEPTION_MESSAGE = "[redacted]"

    fun scrub(event: SentryEvent): SentryEvent = event.apply {
        user = null
        request = null
        serverName = null
        extras = emptyMap()

        breadcrumbs = breadcrumbs
            ?.filter { breadcrumb -> breadcrumb.category == SAFE_BREADCRUMB_CATEGORY }

        message?.apply {
            message = REDACTED_EVENT_MESSAGE
            formatted = null
            params = emptyList()
        }

        exceptions?.forEach { exception ->
            exception.value = REDACTED_EXCEPTION_MESSAGE
        }

        contexts.device?.apply {
            id = null
            name = null
        }
    }
}
