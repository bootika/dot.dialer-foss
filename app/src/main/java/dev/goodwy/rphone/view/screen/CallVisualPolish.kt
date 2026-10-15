package dev.goodwy.rphone.view.screen

import android.telecom.CallAudioState
import android.telecom.Call
import dev.goodwy.rphone.view.components.CallControlIcons
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ColorScheme
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon

/** Show the action of the next tap; the filled button separately indicates current mute state. */
internal fun callMuteActionIcon(isMuted: Boolean): ImageVector =
    if (isMuted) CallControlIcons.Mic else CallControlIcons.MicOff

internal fun callParticipantStatusColor(state: Int, scheme: ColorScheme): Color =
    if (state == Call.STATE_ACTIVE) scheme.onSurface else scheme.primary

internal fun callAudioRouteIcon(route: Int): ImageVector = when (route) {
    CallAudioState.ROUTE_BLUETOOTH -> CallControlIcons.Bluetooth
    CallAudioState.ROUTE_WIRED_HEADSET -> CallControlIcons.Headset
    CallAudioState.ROUTE_SPEAKER -> CallControlIcons.Speaker
    else -> DotDialerHandsetIcon
}

/** The edge scrim is tinted with surface, even when a caller photo is present. */
internal fun callSystemBarUsesLightIcons(surface: Color): Boolean {
    val luminance = surface.luminance()
    val whiteContrast = 1.05f / (luminance + 0.05f)
    val blackContrast = (luminance + 0.05f) / 0.05f
    return whiteContrast > blackContrast
}
