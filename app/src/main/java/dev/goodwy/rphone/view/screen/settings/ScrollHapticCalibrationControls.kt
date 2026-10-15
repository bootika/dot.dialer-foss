package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon

import android.content.Intent
import android.provider.Settings
import android.view.Surface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.SwipeVertical
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.AndroidHaptics
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.view.components.Title
import org.koin.compose.koinInject
import kotlin.math.roundToInt


@Composable
internal fun ScrollHapticCalibrationControls(prefs: PreferenceManager) {
var scrollCmPerHaptic by remember { mutableFloatStateOf(prefs.getFloat(PreferenceManager.KEY_SCROLL_CM_PER_HAPTIC, 1.5f)) }
var scrollHapticStrength by remember { mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_SCROLL_HAPTIC_STRENGTH, 60)) }
                                Column(
                                    modifier = Modifier
                                        .background(
                                            color = cardColor,
                                            shape = RoundedCornerShape(cardCornerExtraSmall)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // ── Slider: Haptic Interval ──
                                    // 1 haptic per X cm. Range 0.5–5.0 cm.
                                    val cmLabel = stringResource(R.string.haptic_interval_value).format(scrollCmPerHaptic)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            modifier = Modifier.weight(1f).padding(end = 8.dp).settingsSearchTarget(stringResource(R.string.haptic_interval)),
                                            text = stringResource(R.string.haptic_interval),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            modifier = Modifier.combinedClickable(
                                                onClick = { scrollCmPerHaptic = 1.5f },
                                                interactionSource = null,
                                                indication = null,
                                            ),
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = cmLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Slider(
                                        value = scrollCmPerHaptic,
                                        onValueChange = { v ->
                                            val snapped = (v * 10f).roundToInt() / 10f
                                            scrollCmPerHaptic = snapped
                                            prefs.setFloat(PreferenceManager.KEY_SCROLL_CM_PER_HAPTIC, snapped)
                                        },
                                        valueRange = 0.5f..5.0f,
                                        steps = 44,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    )

                                    // ── Slider: Haptic Strength ──
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            modifier = Modifier.weight(1f).padding(end = 8.dp).settingsSearchTarget(stringResource(R.string.haptic_strength)),
                                            text = stringResource(R.string.haptic_strength),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Surface(
                                            modifier = Modifier.combinedClickable(
                                                onClick = { scrollHapticStrength = 60 },
                                                interactionSource = null,
                                                indication = null,
                                            ),
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = scrollHapticStrength.toString(),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    Slider(
                                        value = scrollHapticStrength.toFloat(),
                                        onValueChange = { v ->
                                            val snapped = v.roundToInt().coerceIn(1, 255)
                                            scrollHapticStrength = snapped
                                            prefs.setInt(PreferenceManager.KEY_SCROLL_HAPTIC_STRENGTH, snapped)
                                        },
                                        valueRange = 1f..255f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
}
