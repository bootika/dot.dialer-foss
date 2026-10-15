package dev.goodwy.rphone.view.screen

import android.telecom.CallAudioState
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.RillSelectionDialog
import kotlin.math.roundToInt

@Composable
internal fun CallAudioRoutePicker(
    audioState: CallAudioState?,
    onDismiss: () -> Unit,
    onRouteSelected: (Int) -> Unit,
) {
    val view = LocalView.current
    val supported = audioState?.supportedRouteMask ?: 0
    val handsetLabel = stringResource(R.string.audio_route_handset)
    val speakerLabel = stringResource(R.string.audio_route_speaker)
    val headsetLabel = stringResource(R.string.audio_route_headset)
    val bluetoothLabel = stringResource(R.string.audio_route_bluetooth)
    val options = remember(supported, handsetLabel, speakerLabel, headsetLabel, bluetoothLabel) {
        buildList {
            if ((supported and CallAudioState.ROUTE_EARPIECE) != 0) add(handsetLabel to CallAudioState.ROUTE_EARPIECE)
            if ((supported and CallAudioState.ROUTE_SPEAKER) != 0) add(speakerLabel to CallAudioState.ROUTE_SPEAKER)
            if ((supported and CallAudioState.ROUTE_WIRED_HEADSET) != 0) add(headsetLabel to CallAudioState.ROUTE_WIRED_HEADSET)
            if ((supported and CallAudioState.ROUTE_BLUETOOTH) != 0) {
                val deviceName = try {
                    audioState?.activeBluetoothDevice?.name
                } catch (error: SecurityException) {
                    null
                }
                add((deviceName ?: bluetoothLabel) to CallAudioState.ROUTE_BLUETOOTH)
            }
        }
    }

    RillSelectionDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.audio_output_title),
        items = options,
        itemLabel = { it.first },
        onItemSelected = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onRouteSelected(it.second)
        },
        isSelected = { it.second == audioState?.route },
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        itemIcon = { callAudioRouteIcon(it.second) },
    )
}

@Composable
internal fun CallAppearanceMenu(
    expanded: Boolean,
    appearance: String,
    dropletsEnabled: Boolean,
    onDropletsChanged: (Boolean) -> Unit,
    anchorPosition: androidx.compose.ui.geometry.Offset,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    if (!expanded) return

    val density = androidx.compose.ui.platform.LocalDensity.current
    val positionProvider = remember(anchorPosition, density) {
        CallAppearancePopupPositionProvider(
            anchor = IntOffset(anchorPosition.x.roundToInt(), anchorPosition.y.roundToInt()),
            gapPx = with(density) { 10.dp.roundToPx() },
            marginPx = with(density) { 8.dp.roundToPx() },
        )
    }
    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            clippingEnabled = true,
        ),
    ) {
        CallAppearanceMenuContent(
            appearance = appearance,
            dropletsEnabled = dropletsEnabled,
            onDropletsChanged = onDropletsChanged,
            onSelect = onSelect,
        )
    }
}

@Composable
internal fun CallAppearanceMenuContent(
    appearance: String,
    dropletsEnabled: Boolean,
    onDropletsChanged: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 288.dp)
                .width(IntrinsicSize.Max)
                .verticalScroll(rememberScrollState())
                .padding(4.dp),
        ) {
            CallAppearanceMenuItem(
                label = stringResource(R.string.call_appearance_circular_avatar),
                selected = appearance == PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR,
                onClick = { onSelect(PreferenceManager.CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR) },
            )
            CallAppearanceMenuItem(
                label = stringResource(R.string.call_appearance_full_photo),
                selected = appearance == PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO,
                onClick = { onSelect(PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .toggleable(
                        value = dropletsEnabled,
                        role = Role.Switch,
                        onValueChange = onDropletsChanged,
                    )
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.ambient_droplets),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(12.dp))
                Switch(checked = dropletsEnabled, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun CallAppearanceMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "callAppearanceItemColor",
    ).value
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private class CallAppearancePopupPositionProvider(
    private val anchor: IntOffset,
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: androidx.compose.ui.unit.IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = callAppearancePopupPosition(
        anchor = anchor,
        windowSize = windowSize,
        popupSize = popupContentSize,
        gapPx = gapPx,
        marginPx = marginPx,
    )
}

internal fun callAppearancePopupPosition(
    anchor: IntOffset,
    windowSize: IntSize,
    popupSize: IntSize,
    gapPx: Int,
    marginPx: Int,
): IntOffset {
    val maxX = (windowSize.width - popupSize.width - marginPx).coerceAtLeast(marginPx)
    val x = (anchor.x - popupSize.width / 2).coerceIn(marginPx, maxX)
    val below = anchor.y + gapPx
    val maximumY = (windowSize.height - popupSize.height - marginPx).coerceAtLeast(marginPx)
    val y = if (below <= maximumY) {
        below
    } else {
        (anchor.y - popupSize.height - gapPx).coerceIn(marginPx, maximumY)
    }
    return IntOffset(x, y)
}
