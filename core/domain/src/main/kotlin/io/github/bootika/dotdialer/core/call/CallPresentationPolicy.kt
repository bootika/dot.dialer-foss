package io.github.bootika.dotdialer.core.call

object CallPresentationPolicy {
    const val DEFAULT_ALWAYS_FULL_SCREEN = true
    const val OUTGOING_SESSION_GRACE_MILLIS = 3_000L

    fun shouldLaunchCallActivity(
        isIncomingRinging: Boolean,
        alwaysFullScreen: Boolean = DEFAULT_ALWAYS_FULL_SCREEN,
    ): Boolean = !isIncomingRinging || alwaysFullScreen

    /**
     * A ringing notification may alert only while the call activity is not already presenting the
     * same incoming call. The foreground-service notification remains published at low importance.
     */
    fun shouldAlertIncomingNotification(
        isIncomingRinging: Boolean,
        suppressIncomingAlert: Boolean,
    ): Boolean = isIncomingRinging && !suppressIncomingAlert

    fun shouldAttachFullScreenIntent(
        isIncomingRinging: Boolean,
        alwaysFullScreen: Boolean,
        suppressIncomingAlert: Boolean,
    ): Boolean = alwaysFullScreen && shouldAlertIncomingNotification(
        isIncomingRinging = isIncomingRinging,
        suppressIncomingAlert = suppressIncomingAlert,
    )

    /** A suppressed incoming notification must never regain heads-up behavior via a HIGH channel. */
    fun shouldUseHighPriorityCallChannel(
        isIncomingRinging: Boolean,
        suppressIncomingAlert: Boolean,
        requestedHighPriority: Boolean,
    ): Boolean = requestedHighPriority && !(isIncomingRinging && suppressIncomingAlert)

    /** Only managed calls or an unexpired outgoing launch can keep our call screen alive. */
    fun shouldFinishBeforeRendering(
        repositoryHasActiveCall: Boolean,
        pendingOutgoingLaunch: Boolean,
    ): Boolean = !pendingOutgoingLaunch && !repositoryHasActiveCall

    fun missingSessionDismissDelayMillis(awaitingOutgoingSession: Boolean): Long =
        if (awaitingOutgoingSession) OUTGOING_SESSION_GRACE_MILLIS else 400L
}
