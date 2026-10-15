package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import dev.goodwy.rphone.view.components.CallControlIcons
import dev.goodwy.rphone.view.components.callPressFeedback
import dev.goodwy.rphone.view.components.rememberCallPressColor
import dev.goodwy.rphone.view.components.rememberCallTapHaptic
import dev.goodwy.rphone.view.components.rememberCallTapFeedback
import dev.goodwy.rphone.view.components.rememberEnabledCallAction

import android.telecom.Call
import android.media.AudioManager
import android.media.ToneGenerator
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.automirrored.rounded.PhoneForwarded
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.shadow.Shadow
import dev.goodwy.rphone.view.components.RillIconBox
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.rememberWhiteContactPhotoRequest
import dev.goodwy.rphone.view.components.DotUnknownCallerIcon
import dev.goodwy.rphone.view.screen.onboarding.wavyCircleShape
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.MyColors.dialpadKeyColor
import dev.goodwy.rphone.view.theme.color_call_button
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.view.theme.CallActionColors
import androidx.compose.ui.platform.testTag
import dev.goodwy.rphone.view.theme.customColors
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

@Composable
fun KeypadButton(
    modifier: Modifier,
    key: Char,
    style: Int = 0,
    onClick: () -> Unit,
    keyHeightDp: Float = 64f,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = rememberCallTapFeedback(interactionSource)
    val isPressed = feedback.pressed
    val haptic = rememberCallTapHaptic()

    val cornerRadius by animateDpAsState(
        targetValue = when (style) {
            1 -> 50.dp // Circular
            2 -> 0.dp  // Minimal
            else -> if (isPressed) 16.dp else 32.dp // Modern
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "ButtonShape"
    )

    val containerColor = rememberCallPressColor(when (style) {
        2 -> if (isPressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else dialpadKeyColor
        else -> dialpadKeyColor
    }, isPressed)

    Surface(
        onClick = rememberEnabledCallAction(true) { feedback.acknowledge(); haptic(); onClick() },
        modifier = modifier.height(keyHeightDp.dp).callPressFeedback(isPressed, fillAlpha = containerColor.alpha),
        shape = if (style == 1) CircleShape else RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = if (style == 2 && !isPressed) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) else null,
        interactionSource = interactionSource
    ) {
        InCallKeyLabel(key, if (style == 1) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun PocketModeOverlay(
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.customColors.colorIndigo,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.ScreenLockPortrait,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.customColors.colorDarkIndigo
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.pocket_mode_active),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.pocket_mode_active_description),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            FilledTonalButton(
                onClick = onDismiss,
                shape = CircleShape
            ) {
                Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pocket_mode_dismiss))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickResponsesBottomSheet(
    phoneNumber: String,
    contactName: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
    onOpenSmsApp: () -> Unit,
//    onScheduleReminder: (Long, String) -> Unit
) {
    val prefs = koinInject<PreferenceManager>()
    val responses = remember { prefs.getQuickResponses() }
    var customText by remember { mutableStateOf("") }
    var isCustomVisible by remember { mutableStateOf(false) }

//    val tomorrowMorningMinutes = remember {
//        val now = Calendar.getInstance()
//        val tomorrow = Calendar.getInstance().apply {
//            add(Calendar.DAY_OF_YEAR, 1)
//            set(Calendar.HOUR_OF_DAY, 9)
//            set(Calendar.MINUTE, 0)
//            set(Calendar.SECOND, 0)
//            set(Calendar.MILLISECOND, 0)
//        }
//        val diff = tomorrow.timeInMillis - now.timeInMillis
//        (diff / (1000 * 60)).coerceAtLeast(15)
//    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag handle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(width = 36.dp, height = 4.dp)
                ) {}
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.quick_response),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.decline_call_and_reply_to, contactName.ifBlank { phoneNumber }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
//                IconButton(onClick = onOpenSmsApp) {
//                    Icon(
//                        Icons.AutoMirrored.Rounded.OpenInNew,
//                        contentDescription = "Open SMS app",
//                        tint = MaterialTheme.colorScheme.primary
//                    )
//                }
            }

            Spacer(Modifier.height(16.dp))

            responses.forEach { responseText ->
                Surface(
                    onClick = { onSend(responseText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = responseText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (!isCustomVisible) {
                Button(
                    onClick = { isCustomVisible = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.write_your_own))
                }
            } else {
                TextField(
                    value = customText,
                    onValueChange = { customText = it },
                    placeholder = { Text(stringResource(R.string.type_message)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent
                    ),
                    trailingIcon = {
                        IconButton(
                            modifier = Modifier.width(72.dp).padding(horizontal = 8.dp),
                            onClick = {
                                if (customText.isNotBlank()) {
                                    onSend(customText.trim())
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (customText.isNotBlank()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = CircleShape,
                            enabled = customText.isNotBlank()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                modifier = Modifier.padding(start = 2.dp),
                                contentDescription = stringResource(R.string.send),
                            )
                        }
                    },
                )
            }

//            Spacer(Modifier.height(16.dp))
//            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
//            Spacer(Modifier.height(12.dp))
//
//            Text(
//                text = "Remind Me to Call Back",
//                style = MaterialTheme.typography.titleMedium,
//                fontWeight = FontWeight.SemiBold
//            )
//            Spacer(Modifier.height(8.dp))
//
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                listOf(
//                    "In 15m" to 15L,
//                    "In 1h" to 60L,
//                    "Tomorrow 9 AM" to tomorrowMorningMinutes
//                ).forEach { (label, minutes) ->
//                    FilledTonalButton(
//                        onClick = { onScheduleReminder(minutes, label) },
//                        modifier = Modifier.weight(1f),
//                        shape = RoundedCornerShape(12.dp),
//                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
//                    ) {
//                        Icon(Icons.Rounded.Alarm, contentDescription = null, modifier = Modifier.size(14.dp))
//                        Spacer(Modifier.width(4.dp))
//                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
//                    }
//                }
//            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun IncomingCallSecondaryActions(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    showMore: Boolean,
    onMessage: () -> Unit,
    onRemind: () -> Unit,
    onMore: () -> Unit,
) {
    CallActionGrid(rowCounts = listOf(if (showMore) 3 else 2), modifier = Modifier.fillMaxWidth()) {
        IncomingCallSecondaryActionCells(useLg, useBlur, blurIntensity, showMore, onMessage, onRemind, onMore)
    }
}

@Composable
internal fun IncomingCallSecondaryActionCells(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    showMore: Boolean,
    onMessage: () -> Unit,
    onRemind: () -> Unit,
    onMore: () -> Unit,
) {
        CallScreenActionButton(
            label = stringResource(R.string.message),
            icon = CallControlIcons.Message,
            useLiquidGlass = useLg,
            useBlur = useBlur,
            blurIntensity = blurIntensity,
            onClick = onMessage,
        )
        CallScreenActionButton(
            label = stringResource(R.string.remind_me),
            icon = CallControlIcons.Reminder,
            useLiquidGlass = useLg,
            useBlur = useBlur,
            blurIntensity = blurIntensity,
            onClick = onRemind,
        )
        if (showMore) {
            CallScreenActionButton(
                label = stringResource(R.string.more),
                icon = CallControlIcons.MoreHorizontal,
                useLiquidGlass = useLg,
                useBlur = useBlur,
                blurIntensity = blurIntensity,
                onClick = onMore,
            )
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallBackReminderBottomSheet(
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit,
    onChooseTime: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val tomorrowAtNine = remember(now) {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.call_back_reminder),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            ReminderChoiceRow(
                label = stringResource(R.string.remind_in_15_minutes),
                icon = Icons.Rounded.Timer,
                onClick = { onSchedule(now + 15 * 60 * 1000L) },
            )
            ReminderChoiceRow(
                label = stringResource(R.string.remind_in_1_hour),
                icon = Icons.Rounded.Schedule,
                onClick = { onSchedule(now + 60 * 60 * 1000L) },
            )
            ReminderChoiceRow(
                label = stringResource(R.string.remind_tomorrow_morning),
                icon = Icons.Rounded.WbSunny,
                onClick = { onSchedule(tomorrowAtNine) },
            )
            ReminderChoiceRow(
                label = stringResource(R.string.choose_time),
                icon = Icons.Rounded.EditCalendar,
                onClick = onChooseTime,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ReminderChoiceRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnknownCallerActionsBottomSheet(
    onDismiss: () -> Unit,
    onAddToContacts: () -> Unit,
    onBlockAndReport: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.unknown_caller_actions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            ReminderChoiceRow(
                label = stringResource(R.string.create_contact),
                icon = Icons.Rounded.PersonAdd,
                onClick = onAddToContacts,
            )
            Surface(
                onClick = onBlockAndReport,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Report,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.block_and_report_spam),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

internal fun callWaveActivity(callState: Int): Float = when (callState) {
    Call.STATE_RINGING -> 1f
    Call.STATE_DIALING, Call.STATE_CONNECTING -> 0.78f
    Call.STATE_ACTIVE -> 0.62f
    Call.STATE_HOLDING -> 0.28f
    Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> 0.16f
    else -> 0.48f
}

/**
 * Pure wave model used by the renderer and unit tests. Each layer has its own
 * frequency, direction and slow amplitude envelope, so the field does not
 * look like copies of one contour scaled concentrically.
 */
internal fun callWaveRadiusFactor(
    angle: Float,
    phase: Float,
    layer: Int,
    activity: Float,
    motion: Float = 0f,
): Float {
    val normalizedLayer = layer.mod(CALL_WAVE_LAYER_COUNT)
    val motionEnergy = motion.coerceIn(0f, 1f)
    val frequency = CALL_WAVE_FREQUENCIES[normalizedLayer] +
        motionEnergy * (1.2f + (normalizedLayer % 3) * 0.55f)
    val baseAmplitude = CALL_WAVE_AMPLITUDES[normalizedLayer]
    val amplitude = baseAmplitude * activity.coerceIn(0f, 1f) * (1f + motionEnergy * 0.48f)
    val envelopeFrequency = 1.35f + (normalizedLayer % 4) * 0.47f
    val envelope = 0.58f + 0.42f * sin(angle * envelopeFrequency - phase * 0.22f + normalizedLayer * 0.91f)
    val primary = sin(angle * frequency + phase + normalizedLayer * 0.67f)
    val harmonic = 0.31f * sin(angle * (frequency * 0.57f) - phase * 0.71f + normalizedLayer * 0.41f)
    val fineDetail = 0.12f * sin(angle * (frequency * 1.43f) + phase * 0.38f - normalizedLayer * 0.29f)
    return 1f + amplitude * envelope * (primary + harmonic + fineDetail)
}

internal fun callAvatarRadiusFactor(
    angle: Float,
    phase: Float,
    activity: Float,
    motion: Float = 0f,
): Float {
    val energy = activity.coerceIn(0f, 1f)
    val motionEnergy = motion.coerceIn(0f, 1f)
    val amplitude = (0.015f + 0.010f * energy) * (1f + motionEnergy * 0.32f)
    val envelope = 0.72f + 0.28f * sin(angle * 2.15f - phase * 0.34f)
    val primary = sin(angle * (14.5f + motionEnergy * 2.2f) + phase * 0.92f)
    val harmonic = 0.24f * sin(angle * (21.5f + motionEnergy * 2.8f) - phase * 0.61f)
    val irregularDrift = 0.18f * sin(angle * 2.35f + phase * 0.22f)
    return 1f + amplitude * envelope * (primary + harmonic + irregularDrift)
}

private fun animatedCallAvatarShape(
    phase: Float,
    activity: Float,
    motion: Float,
): Shape = GenericShape { size, _ ->
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val maxAmplitude = CALL_AVATAR_MAX_AMPLITUDE
    val baseRadius = minOf(size.width, size.height) / 2f / (1f + maxAmplitude)
    val sampleCount = 180
    for (sample in 0..sampleCount) {
        val angle = sample.toFloat() / sampleCount * (PI * 2.0).toFloat()
        val radius = baseRadius * callAvatarRadiusFactor(angle, phase, activity, motion)
        val x = centerX + cos(angle) * radius
        val y = centerY + sin(angle) * radius
        if (sample == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private data class CallWaveMotion(
    val tilt: Offset = Offset.Zero,
    val intensity: Float = 0f,
)

@Composable
private fun rememberCallWaveMotion(enabled: Boolean): CallWaveMotion {
    val context = LocalContext.current
    var motion by remember { mutableStateOf(CallWaveMotion()) }

    DisposableEffect(context, enabled) {
        if (!enabled) {
            motion = CallWaveMotion()
            onDispose { }
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            var gravityX = 0f
            var gravityY = 0f
            var gravityZ = SensorManager.GRAVITY_EARTH
            var filteredIntensity = 0f
            var gravityInitialized = false
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (!gravityInitialized) {
                        gravityX = event.values[0]
                        gravityY = event.values[1]
                        gravityZ = event.values[2]
                        gravityInitialized = true
                        motion = CallWaveMotion(
                            tilt = Offset(
                                x = (-gravityX / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f),
                                y = (gravityY / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f),
                            ),
                        )
                        return
                    }
                    // Gravity is the slow signal used for tilt. The remaining high-pass
                    // component is actual device motion and drives wave energy/frequency.
                    gravityX = gravityX * 0.88f + event.values[0] * 0.12f
                    gravityY = gravityY * 0.88f + event.values[1] * 0.12f
                    gravityZ = gravityZ * 0.88f + event.values[2] * 0.12f
                    val linearX = event.values[0] - gravityX
                    val linearY = event.values[1] - gravityY
                    val linearZ = event.values[2] - gravityZ
                    val linearMagnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)
                    val normalizedIntensity = (linearMagnitude / 3.8f).coerceIn(0f, 1f)
                    filteredIntensity = filteredIntensity * 0.86f + normalizedIntensity * 0.14f
                    motion = CallWaveMotion(
                        tilt = Offset(
                            x = (-gravityX / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f),
                            y = (gravityY / SensorManager.GRAVITY_EARTH).coerceIn(-1f, 1f),
                        ),
                        intensity = filteredIntensity,
                    )
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            if (accelerometer != null) {
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose { sensorManager?.unregisterListener(listener) }
        }
    }
    return motion
}

@Composable
fun CallWaveAvatar(
    photoUri: String?,
    callState: Int,
    modifier: Modifier = Modifier,
    displayName: String = "",
    fieldSize: Dp = CALL_SCREEN_WAVE_FIELD_DP.dp,
    avatarSize: Dp = CALL_SCREEN_WAVE_AVATAR_DP.dp,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val waveColor = Color.White
    val waveContrastColor = MaterialTheme.colorScheme.onSurface
    val activityTarget = callWaveActivity(callState)
    val activity by animateFloatAsState(
        targetValue = activityTarget,
        animationSpec = spring(
            dampingRatio = 0.88f,
            stiffness = 38f,
        ),
        label = "callWaveEnergy",
    )
    val sensedMotion = rememberCallWaveMotion(enabled = callState != Call.STATE_DISCONNECTED)
    val tilt by animateOffsetAsState(
        targetValue = sensedMotion.tilt,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = 52f,
        ),
        label = "callWaveSensorEase",
    )
    val motionEnergy by animateFloatAsState(
        targetValue = sensedMotion.intensity,
        animationSpec = spring(
            dampingRatio = 0.86f,
            stiffness = 44f,
        ),
        label = "callWaveMotionDecay",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "callWaveField")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2.0).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "callWavePhase",
    )
    val motionPhase = phase * (1f + motionEnergy * 0.28f)
    val avatarShape = remember(motionPhase, activity, motionEnergy) {
        animatedCallAvatarShape(
            phase = motionPhase * 0.86f,
            activity = activity,
            motion = motionEnergy,
        )
    }

    Box(
        modifier = modifier.size(fieldSize),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val minDimension = minOf(size.width, size.height)
            val avatarRadiusFraction = avatarSize.value / fieldSize.value / 2f
            val avatarRadius = minDimension * avatarRadiusFraction
            val innerRadius = minDimension * (avatarRadiusFraction + 0.026f)
            val outerRadius = minDimension * 0.472f
            val tiltMagnitude = minDimension * 0.016f

            // A restrained echo of the actual animated mask visually couples the portrait
            // to the field. It prevents the first independent contour from reading as a
            // static decorative flower placed behind a circular photo.
            val maskEchoPath = Path()
            val maskEchoBaseRadius = avatarRadius / (1f + CALL_AVATAR_MAX_AMPLITUDE) + 3.5.dp.toPx()
            for (sample in 0..CALL_WAVE_SAMPLE_COUNT) {
                val angle = (sample.toFloat() / CALL_WAVE_SAMPLE_COUNT) * (PI * 2.0).toFloat()
                val radius = maskEchoBaseRadius * callAvatarRadiusFactor(
                    angle = angle,
                    phase = motionPhase * 0.86f,
                    activity = activity,
                    motion = motionEnergy,
                )
                val point = Offset(
                    x = center.x + tilt.x * tiltMagnitude * 0.28f + cos(angle) * radius,
                    y = center.y + tilt.y * tiltMagnitude * 0.28f + sin(angle) * radius,
                )
                if (sample == 0) maskEchoPath.moveTo(point.x, point.y)
                else maskEchoPath.lineTo(point.x, point.y)
            }
            maskEchoPath.close()
            if (!isDark) drawPath(
                path = maskEchoPath,
                color = waveContrastColor.copy(alpha = 0.07f),
                style = Stroke(
                    width = 1.45.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(
                path = maskEchoPath,
                color = waveColor.copy(alpha = if (isDark) 0.24f else 0.24f),
                style = Stroke(
                    width = 0.9.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            repeat(CALL_WAVE_LAYER_COUNT) { layer ->
                val progress = CALL_WAVE_RADIAL_FRACTIONS[layer]
                val baseRadius = innerRadius + (outerRadius - innerRadius) * progress
                val layerCenter = center + Offset(
                    x = tilt.x * tiltMagnitude * (0.35f + progress * 0.65f),
                    y = tilt.y * tiltMagnitude * (0.35f + progress * 0.65f),
                )
                val layerPhase = motionPhase * CALL_WAVE_PHASE_SPEEDS[layer] + layer * 0.49f
                val path = Path()
                for (sample in 0..CALL_WAVE_SAMPLE_COUNT) {
                    val angle = (sample.toFloat() / CALL_WAVE_SAMPLE_COUNT) * (PI * 2.0).toFloat()
                    val radius = baseRadius * callWaveRadiusFactor(
                        angle = angle,
                        phase = layerPhase,
                        layer = layer,
                        activity = activity,
                        motion = motionEnergy,
                    )
                    val point = Offset(
                        x = layerCenter.x + cos(angle) * radius,
                        y = layerCenter.y + sin(angle) * radius,
                    )
                    if (sample == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()

                val isPrimaryContour = layer == 1 || layer == 4 || layer == 7
                val outerFade = 1f - progress * 0.4f
                val alpha = (if (isDark) {
                    if (isPrimaryContour) 0.46f else 0.20f
                } else {
                    if (isPrimaryContour) 0.30f else 0.11f
                }) * outerFade
                val strokeWidth = (if (isPrimaryContour) 1.85f else 0.78f).dp.toPx()
                val contourBrush = Brush.sweepGradient(
                    colors = listOf(
                        waveColor.copy(alpha = alpha * 0.46f),
                        waveColor.copy(alpha = alpha),
                        waveColor.copy(alpha = alpha * 0.62f),
                        waveColor.copy(alpha = alpha * 0.28f),
                        waveColor.copy(alpha = alpha * 0.82f),
                        waveColor.copy(alpha = alpha * 0.46f),
                    ),
                    center = layerCenter,
                )
                if (isPrimaryContour) {
                    drawPath(
                        path = path,
                        color = (if (isDark) waveColor else waveContrastColor).copy(alpha = alpha * 0.09f),
                        style = Stroke(
                            width = 5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                if (!isDark) {
                    drawPath(
                        path = path,
                        color = waveContrastColor.copy(alpha = alpha * 0.38f),
                        style = Stroke(
                            width = strokeWidth + 0.65.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
                drawPath(
                    path = path,
                    brush = contourBrush,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        Box(
            modifier = Modifier
                .size(avatarSize)
                .graphicsLayer {
                    translationX = tilt.x * 3.dp.toPx()
                    translationY = tilt.y * 3.dp.toPx()
                    shape = avatarShape
                    clip = true
                }
                .border(
                    width = 1.9.dp,
                    color = if (isDark) waveColor.copy(alpha = 0.16f)
                    else waveContrastColor.copy(alpha = 0.11f),
                    shape = avatarShape,
                )
                .border(1.dp, waveColor.copy(alpha = if (isDark) 0.54f else 0.46f), avatarShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoUri.isNullOrEmpty()) {
                AsyncImage(
                    model = rememberWhiteContactPhotoRequest(photoUri),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = displayName.trim().firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/**
 * The compact circular action used by the approved Dot Dialer call screen.
 * Keeping the circle independent from the label prevents longer translations
 * from changing the geometry of the action grid.
 */
@Composable
fun CallScreenActionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isActive: Boolean = false,
    isResumeAction: Boolean = false,
    useLiquidGlass: Boolean = false,
    useBlur: Boolean = false,
    blurIntensity: Float = 20f,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val feedback = rememberCallTapFeedback(interaction, enabled)
    val isPressed = feedback.pressed
    val haptic = rememberCallTapHaptic()

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val glassShape = CircleShape
    val baseContainerColor = when {
        isResumeAction && isPressed -> CallActionColors.answerCommitted
        isResumeAction -> CallActionColors.answerIdle
        isActive -> MaterialTheme.colorScheme.onSurface
        else -> Color.White.copy(alpha = if (isDark) 0.10f else 0.45f)
    }
    val contentColor = when {
        isResumeAction -> CallActionColors.onAction
        isActive -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.onSurface
    }
    val containerColor = rememberCallPressColor(baseContainerColor, isPressed, enabled)

    Column(
        modifier = modifier
            .width(88.dp)
            .alpha(if (enabled) 1f else 0.5f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = rememberEnabledCallAction(enabled) { feedback.acknowledge(); haptic(); onClick() },
            enabled = enabled,
            modifier = Modifier
                .size(CALL_SCREEN_ACTION_DIAMETER_DP.dp)
                .testTag("callRoundActionSurface")
                .callPressFeedback(isPressed, enabled, containerColor.alpha)
                .then(
                    when {
                        !isActive && !isResumeAction && useLiquidGlass && globalBackdrop != null -> Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { glassShape },
                            shadow = { Shadow(radius = 6.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.2f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 12f * d, refractionAmount = 34f * d)
                            },
                            highlight = { Highlight.Default },
                        )
                        !isActive && !isResumeAction && useBlur && globalBackdrop != null -> Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape = { glassShape },
                            shadow = { Shadow(radius = 6.dp) },
                            effects = { blur(blurIntensity * density) },
                        )
                        else -> Modifier
                    },
                ),
            shape = glassShape,
            color = containerColor,
            interactionSource = interaction,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = if (isActive || isResumeAction) null else BorderStroke(
                1.dp,
                if (isDark) Color.White.copy(alpha = 0.20f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(callActionIconSize(icon)),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Static call-screen identity. Every size uses the same circular crop so changing the responsive
 * size never changes the visual language or reveals the fallback colour beside a real photo.
 */
@Composable
fun CalmCallAvatar(
    photoUri: String?,
    displayName: String,
    phoneNumber: String,
    avatarSize: Dp,
    modifier: Modifier = Modifier,
    colorSeed: String? = null,
) {
    val normalizedName = (colorSeed ?: displayName).trim()
    val normalizedNumber = phoneNumber.trim()
    val isUnknown = normalizedName.isBlank() || normalizedName == normalizedNumber

    RillAvatar(
        name = if (isUnknown) "" else normalizedName,
        colorSeed = dev.goodwy.rphone.view.components.avatarIdentityColorSeed(colorSeed ?: normalizedName, normalizedNumber),
        photoUri = photoUri,
        icon = if (isUnknown) DotUnknownCallerIcon else null,
        // Constrain the maximum first, then choose a square within it. aspectRatio before size
        // ignores the requested diameter; a fixed size before it can produce an oval on short
        // screens. This preserves both the measured clearance and a circular mask.
        modifier = modifier
            .sizeIn(maxWidth = avatarSize, maxHeight = avatarSize)
            .aspectRatio(1f),
        shape = CircleShape,
        photoContentScale = ContentScale.Crop,
    )
}

@Composable
fun PulsingAvatar(photoUri: String?) {
    CallWaveAvatar(photoUri = photoUri, callState = Call.STATE_RINGING)
}

@Composable
fun HeroAvatar(photoUri: String?, avatarSize: Dp = 160.dp, wavy: Boolean = false) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val avatarShape = remember(settingsState) {
        if (wavy) wavyCircleShape(waveAmplitude = 0.024f)
        else {
            val shapeVal = prefs.getInt(PreferenceManager.KEY_AVATAR_SHAPE, 1)
            when (shapeVal) {
                0 -> RoundedCornerShape(20.dp)
                1 -> CircleShape
                2 -> RoundedCornerShape(0.dp)
                else -> CircleShape
            }
        }
    }
    val avatarFrame = prefs.getBoolean(PreferenceManager.KEY_AVATAR_FRAME, false)
    val borderColor =  MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .then(
                if (avatarFrame) Modifier
                    .drawBehind {
                        val borderWidth = size.width * 0.08f // 8% of the width
                        drawOutline(
                            outline = avatarShape.createOutline(size, layoutDirection, this),
                            color = borderColor,
                            style = Stroke(width = borderWidth)
                        )
                    }
                else Modifier
            )
            .size(avatarSize)
            .clip(avatarShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUri.isNullOrEmpty()) {
            AsyncImage(
                model = rememberWhiteContactPhotoRequest(photoUri),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(avatarShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun AnimatedCallButton(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    btnColor: Color = Color.White.copy(0.12f),
    activeBtnColor: Color = Color.White,
    fgColor: Color = Color.White,
    activeFgColor: Color = Color.Black,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (isActive || isPressed) 20.dp else 42.dp, spring(stiffness = Spring.StiffnessMedium), label = "btnRadius")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(onClick = onClick,
            modifier = Modifier.height(68.dp).fillMaxWidth(),
            shape = RoundedCornerShape(radius),
            color = if (isActive) activeBtnColor else btnColor,
            interactionSource = interaction
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeFgColor else fgColor,
                    modifier = Modifier.size(callActionIconSize(icon))
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = MaterialTheme.typography.labelMedium.fontFamily,
            color = fgColor.copy(0.7f),
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun HorizontalSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    answerOnStart: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    var thresholdHapticSent by remember { mutableStateOf(false) }

    val trackHeight = 96.dp
    val handleWidth = 110.dp
    val handleHeight = 72.dp
    val handleWidthPx = with(localDensity) { handleWidth.toPx() }
    val handleInset = (trackHeight - handleHeight) / 2
    val handleInsetPx = with(localDensity) { handleInset.toPx() }
    var trackWidthPx by remember { mutableFloatStateOf(0f) }

    val maxDrag by remember(trackWidthPx, handleWidthPx, handleInsetPx) {
        derivedStateOf {
            incomingCenteredSliderTravelPx(
                trackWidthPx = trackWidthPx,
                handleWidthPx = handleWidthPx,
                horizontalInsetPx = handleInsetPx,
            )
        }
    }
    val triggerThreshold = maxDrag * INCOMING_ACTION_TRIGGER_FRACTION

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val dragNormal = remember { derivedStateOf { abs(dragProgress.value) } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val handlePulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "handlePulse"
    )

    val hintAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hintAlpha"
    )

    val answerGreen = color_call_button
    val declineRed = color_call_end
    val leftColor = if (answerOnStart) answerGreen else declineRed
    val rightColor = if (answerOnStart) declineRed else answerGreen

    val handleBgColor by animateColorAsState(
        targetValue = when {
            dragProgress.value > 0f -> incomingActionSurfaceColor(rightColor, dragNormal.value)
            dragProgress.value < 0f -> incomingActionSurfaceColor(leftColor, dragNormal.value)
            else -> CallActionColors.neutralIdle
        },
        label = "handleColor"
    )

    val iconTint by animateColorAsState(
        targetValue = incomingActionColor(CallActionColors.onNeutral, CallActionColors.onAction, dragNormal.value),
        label = "iconTint"
    )

    val iconRotation by remember { derivedStateOf { dragProgress.value * 135f } }
    val buttonBgColor = if (useLg || useBlur) bottomBarColor.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant

    Surface(
        shape           = CircleShape,
        color           = buttonBgColor,
        shadowElevation = 0.dp,
        tonalElevation  = 0.dp,
        modifier = Modifier
            .padding(bottom = 36.dp)
            .fillMaxWidth()
            .height(trackHeight)
            .padding(horizontal = 4.dp)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .then(
                if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                    backdrop = globalBackdrop,
                    shape = { CircleShape },
                    shadow = { Shadow(radius = 8.dp) },
                    effects = {
                        val d = density
                        colorControls(saturation = 1.3f)
                        blur(blurIntensity * d)
                        lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                    },
                    highlight = { Highlight.Default }
                )
                else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                    backdrop = globalBackdrop,
                    shape    = { CircleShape },
                    shadow = { Shadow(radius = 8.dp) },
                    effects  = { blur(blurIntensity * density) }
                )
                else Modifier
            )
    ) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(if (answerOnStart) R.string.answer else R.string.decline),
                        modifier = Modifier
                            .padding(start = 20.dp, end = 4.dp)
                            .alpha((1f - (dragProgress.value * -2f).coerceIn(0f, 1f)) * hintAlpha),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = leftColor.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(handleWidth))
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        text = stringResource(if (answerOnStart) R.string.decline else R.string.answer),
                        modifier = Modifier
                            .padding(start = 4.dp, end = 20.dp)
                            .alpha((1f - (dragProgress.value * 2f).coerceIn(0f, 1f)) * hintAlpha),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = rightColor.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                    .graphicsLayer {
                        val idleFactor = (1f - dragNormal.value * 5f).coerceIn(0f, 1f)
                        scaleX = 1f + (handlePulseScale - 1f) * idleFactor
                        scaleY = 1f + (handlePulseScale - 1f) * idleFactor
                    }
                    .width(handleWidth)
                    .height(handleHeight)
                    .testTag("callAnswerHandle")
                    .clip(CircleShape)
                    .background(handleBgColor)
                    .incomingResponseSemantics(onAnswer, onDecline)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { thresholdHapticSent = false },
                            onDragEnd = {
                                coroutineScope.launch {
                                    when {
                                        offsetX.value > 0f && incomingActionCommitReached(
                                            offsetX.value,
                                            triggerThreshold,
                                        ) -> {
                                            offsetX.animateTo(
                                                maxDrag,
                                                tween(
                                                    durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                            performIncomingCommitHaptic(view, answer = !answerOnStart)
                                            if (answerOnStart) onDecline() else onAnswer()
                                        }

                                        offsetX.value < 0f && incomingActionCommitReached(
                                            offsetX.value,
                                            triggerThreshold,
                                        ) -> {
                                            offsetX.animateTo(
                                                -maxDrag,
                                                tween(
                                                    durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                            performIncomingCommitHaptic(view, answer = answerOnStart)
                                            if (answerOnStart) onAnswer() else onDecline()
                                        }

                                        else -> offsetX.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = 0.75f,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                    thresholdHapticSent = false
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(-maxDrag, maxDrag)
                                    offsetX.snapTo(newOffset)
                                    val armed = abs(newOffset) >= triggerThreshold
                                    if (armed && !thresholdHapticSent) {
                                        performIncomingThresholdHaptic(view)
                                        thresholdHapticSent = true
                                    } else if (!armed) {
                                        thresholdHapticSent = false
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                val icon = when {
                    dragProgress.value > 0.1f && answerOnStart -> DotDialerHangupIcon
                    dragProgress.value < -0.1f && !answerOnStart -> DotDialerHangupIcon
                    else -> DotDialerHandsetIcon
                }

                Crossfade(
                    targetState = icon,
                    animationSpec = tween(150),
                    label = "icon"
                ) { targetIcon ->
                    Icon(
                        targetIcon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier
                            .size(callActionIconSize(targetIcon))
                            .graphicsLayer { rotationZ = iconRotation }
                    )
                }
            }
        }
    }
}

/** Accessibility activation only: pointer taps still cannot commit a drag control. */
@Composable
private fun Modifier.incomingResponseSemantics(
    onAnswer: () -> Unit,
    onDecline: (() -> Unit)? = null,
): Modifier {
    val answerLabel = stringResource(R.string.answer)
    val declineLabel = stringResource(R.string.decline)
    val answer by rememberUpdatedState(onAnswer)
    val decline by rememberUpdatedState(onDecline)
    val view = LocalView.current
    return semantics {
        role = Role.Button
        contentDescription = answerLabel
        onClick(label = answerLabel) {
            performIncomingCommitHaptic(view, answer = true)
            answer()
            true
        }
        if (decline != null) {
            customActions = listOf(CustomAccessibilityAction(declineLabel) {
                performIncomingCommitHaptic(view, answer = false)
                decline?.invoke()
                true
            })
        }
    }
}

@Composable
fun VerticalSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    answerOnStart: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    var thresholdHapticSent by remember { mutableStateOf(false) }

    val handleSize = 80.dp
    val maxDrag = with(localDensity) { 100.dp.toPx() }
    val triggerThreshold = maxDrag * 0.7f

    val dragProgress = remember { derivedStateOf { offsetY.value / maxDrag } }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    val textColor = MaterialTheme.colorScheme.onSurface

    val arrowOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrowBounce"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-110).dp)
                .graphicsLayer {
                    alpha = (0.4f + (dragProgress.value * -1.8f)).coerceIn(0f, 1f)
                    translationY = -arrowOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.KeyboardArrowUp, null, tint = textColor, modifier = Modifier.size(36.dp))
            Text(
                stringResource(if (answerOnStart) R.string.swipe_up_to_answer else R.string.swipe_up_to_reject),
                style = MaterialTheme.typography.titleMedium,
                color = textColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 110.dp)
                .graphicsLayer {
                    alpha = (0.4f + (dragProgress.value * 1.8f)).coerceIn(0f, 1f)
                    translationY = arrowOffset
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                stringResource(if (answerOnStart) R.string.swipe_down_to_reject else R.string.swipe_down_to_answer),
                style = MaterialTheme.typography.titleMedium,
                color = textColor.copy(alpha = 0.9f),
                fontWeight = FontWeight.Light,
                fontStyle = FontStyle.Italic
            )
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = textColor, modifier = Modifier.size(36.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            if (abs(offsetY.value) < 5f) {
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale)
                        .background(textColor.copy(alpha = pulseAlpha * 0.4f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(handleSize)
                        .scale(pulseScale * 1.4f)
                        .border(1.dp, textColor.copy(alpha = pulseAlpha * 0.2f), CircleShape)
                )
            }

            val actionProgress = abs(dragProgress.value)
            val verticalTriggerFraction = 0.7f
            val handleBgColor by animateColorAsState(
                targetValue = when {
                    offsetY.value < 0f -> incomingActionSurfaceColor(
                        if (answerOnStart) color_call_button else color_call_end,
                        actionProgress,
                        verticalTriggerFraction,
                    )
                    offsetY.value > 0f -> incomingActionSurfaceColor(
                        if (answerOnStart) color_call_end else color_call_button,
                        actionProgress,
                        verticalTriggerFraction,
                    )
                    else -> CallActionColors.neutralIdle
                },
                label = "bgColor"
            )

            val iconTint by animateColorAsState(
                targetValue = incomingActionColor(
                    color_call_button,
                    Color.White,
                    actionProgress,
                    verticalTriggerFraction,
                ),
                label = "iconTint"
            )
            Surface(
                shape           = CircleShape,
                color = handleBgColor,
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .size(handleSize)
                    .shadow(if (abs(offsetY.value) > 5f) 12.dp else 4.dp, CircleShape)
                    .testTag("callAnswerHandle")
                    .incomingResponseSemantics(onAnswer, onDecline)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { thresholdHapticSent = false },
                            onDragEnd = {
                                coroutineScope.launch {
                                    when {
                                        offsetY.value < 0f && incomingActionCommitReached(
                                            offsetY.value,
                                            triggerThreshold,
                                        ) -> {
                                            offsetY.animateTo(
                                                -maxDrag,
                                                tween(
                                                    durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                            performIncomingCommitHaptic(view, answer = answerOnStart)
                                            if (answerOnStart) onAnswer() else onDecline()
                                        }

                                        offsetY.value > 0f && incomingActionCommitReached(
                                            offsetY.value,
                                            triggerThreshold,
                                        ) -> {
                                            offsetY.animateTo(
                                                maxDrag,
                                                tween(
                                                    durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                            performIncomingCommitHaptic(view, answer = !answerOnStart)
                                            if (answerOnStart) onDecline() else onAnswer()
                                        }

                                        else -> offsetY.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = 0.7f,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                    thresholdHapticSent = false
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset =
                                        (offsetY.value + dragAmount).coerceIn(-maxDrag, maxDrag)
                                    offsetY.snapTo(newOffset)
                                    val armed = abs(newOffset) >= triggerThreshold
                                    if (armed && !thresholdHapticSent) {
                                        performIncomingThresholdHaptic(view)
                                        thresholdHapticSent = true
                                    } else if (!armed) {
                                        thresholdHapticSent = false
                                    }
                                }
                            }
                        )
                    }
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when {
                        offsetY.value < -5f && !answerOnStart -> DotDialerHangupIcon
                        offsetY.value > 5f && answerOnStart -> DotDialerHangupIcon
                        else -> DotDialerHandsetIcon
                    }

                    Crossfade(targetState = icon, label = "icon") { targetIcon ->
                        Icon(
                            targetIcon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(callActionIconSize(targetIcon))
                        )
                    }
                }
            }
        }
    }
}

/**
 * Two-ended incoming-call slider. Answer starts on the configured answer side
 * and travels across the track; Decline starts at the opposite end and travels
 * back. Both handles retain click semantics for Switch Access and TalkBack.
 */
@Composable
fun DualActionHorizontalSlider(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    answerOnStart: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val answerProgress = remember(answerOnStart) { Animatable(0f) }
    val declineProgress = remember(answerOnStart) { Animatable(0f) }
    var answerThresholdHaptic by remember { mutableStateOf(false) }
    var declineThresholdHaptic by remember { mutableStateOf(false) }

    val trackHeight = 92.dp
    val handleSize = 76.dp
    val handlePadding = 8.dp
    val triggerProgress = 0.82f
    val trackColor = if (useLg || useBlur) {
        bottomBarColor.copy(alpha = 0.42f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.16f else 0.08f)
    }

    fun confirm(answer: Boolean) {
        performIncomingCommitHaptic(view, answer)
        if (answer) onAnswer() else onDecline()
    }

    Column(
        modifier = Modifier.padding(bottom = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .widthIn(max = 328.dp)
                .fillMaxWidth()
                .height(trackHeight + 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val trackWidth = maxWidth
            val handlePaddingPx = with(localDensity) { handlePadding.toPx() }
            val travelPx = with(localDensity) {
                incomingSliderTravelPx(
                    trackWidth.toPx(),
                    handleSize.toPx(),
                    handlePaddingPx,
                ).coerceAtLeast(1f)
            }
            val answerStartsLeft = answerOnStart
            val declineStartsLeft = !answerOnStart

            Surface(
                shape = CircleShape,
                color = trackColor,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .width(trackWidth)
                    .height(trackHeight)
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.15f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 12f * d, refractionAmount = 38f * d)
                            },
                            highlight = { Highlight.Default },
                        ) else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = { blur(blurIntensity * density) },
                        ) else Modifier,
                    ),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (answerOnStart) "›  ›  ›" else "‹  ‹  ‹",
                            color = color_call_button.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                if (answerOnStart) R.string.slide_right_to_answer
                                else R.string.slide_left_to_answer,
                            ),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            @Composable
            fun SliderHandle(
                answer: Boolean,
                startsLeft: Boolean,
                progress: Animatable<Float, AnimationVector1D>,
                thresholdHapticSent: Boolean,
                setThresholdHapticSent: (Boolean) -> Unit,
            ) {
                val intendedDirection = if (startsLeft) 1f else -1f
                val translationX = incomingSliderOffsetPx(
                    progress.value,
                    startsLeft,
                    travelPx,
                    handlePaddingPx,
                )
                val actionColor = if (answer) color_call_button else color_call_end
                val label = stringResource(if (answer) R.string.answer else R.string.decline)
                Surface(
                    shape = CircleShape,
                    color = incomingActionSurfaceColor(
                        actionColor,
                        progress.value,
                        triggerProgress,
                    ),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset(translationX.roundToInt(), 0) }
                        .size(handleSize)
                        .semantics {
                            role = Role.Button
                            onClick {
                                confirm(answer)
                                true
                            }
                        }
                        .pointerInput(answer, startsLeft, travelPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { setThresholdHapticSent(false) },
                                onDragCancel = {
                                    setThresholdHapticSent(false)
                                    scope.launch { progress.animateTo(0f, spring(dampingRatio = 0.8f)) }
                                },
                                onDragEnd = {
                                    if (incomingActionCommitReached(progress.value, triggerProgress)) {
                                        scope.launch {
                                            progress.animateTo(
                                                1f,
                                                tween(
                                                    durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                            confirm(answer)
                                        }
                                    } else {
                                        scope.launch {
                                            progress.animateTo(0f, spring(dampingRatio = 0.8f))
                                        }
                                    }
                                    setThresholdHapticSent(false)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        val delta = dragAmount * intendedDirection / travelPx
                                        val next = (progress.value + delta).coerceIn(0f, 1f)
                                        progress.snapTo(next)
                                        if (!thresholdHapticSent && next >= triggerProgress) {
                                            setThresholdHapticSent(true)
                                            performIncomingThresholdHaptic(view)
                                        } else if (thresholdHapticSent && next < triggerProgress) {
                                            setThresholdHapticSent(false)
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (answer) DotDialerHandsetIcon else DotDialerHangupIcon,
                            contentDescription = label,
                            tint = CallActionColors.onAction,
                            modifier = Modifier.size(callActionIconSize(if (answer) DotDialerHandsetIcon else DotDialerHangupIcon)),
                        )
                    }
                }
            }

            SliderHandle(
                answer = true,
                startsLeft = answerStartsLeft,
                progress = answerProgress,
                thresholdHapticSent = answerThresholdHaptic,
                setThresholdHapticSent = { answerThresholdHaptic = it },
            )
            SliderHandle(
                answer = false,
                startsLeft = declineStartsLeft,
                progress = declineProgress,
                thresholdHapticSent = declineThresholdHaptic,
                setThresholdHapticSent = { declineThresholdHaptic = it },
            )
        }
    }
}

@Composable
fun IPhoneSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onMessage: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    var thresholdHapticSent by remember { mutableStateOf(false) }

    val trackWidth = 320.dp
    val trackHeight = 94.dp
    val handleSize = 78.dp
    val handlePadding = 8.dp

    var trackWidthPx by remember { mutableFloatStateOf(with(localDensity) { trackWidth.toPx() }) }
    val handleSizePx = with(localDensity) { handleSize.toPx() }
    val handlePaddingPx = with(localDensity) { handlePadding.toPx() }

    val maxDrag = incomingSliderTravelPx(trackWidthPx, handleSizePx, handlePaddingPx)
    val offsetX = remember { Animatable(0f) }
    var previousMaxDrag by remember { mutableFloatStateOf(maxDrag) }
    LaunchedEffect(maxDrag) {
        offsetX.snapTo(
            incomingSliderOffsetAfterResize(
                offsetX.value,
                previousMaxDrag,
                maxDrag,
                startsAtStart = true,
            ),
        )
        previousMaxDrag = maxDrag
    }

    val trackBgColor =
        if (useLg || useBlur) bottomBarColor else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val buttonIconColor = MaterialTheme.colorScheme.onSurface
    val handleBgColor = incomingActionSurfaceColor(color_call_button, 0f)

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val dragProgress = remember { derivedStateOf { if (maxDrag > 0f) offsetX.value / maxDrag else 0f } }
    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f
    } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(42.dp),
        modifier = Modifier.padding(bottom = 42.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(0.75f),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val declineInteraction = remember { MutableInteractionSource() }
                val declinePressed by declineInteraction.collectIsPressedAsState()
                IconButton(
                    onClick = {
                        performIncomingCommitHaptic(view, answer = false)
                        onDecline()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (declinePressed) color_call_end else incomingActionSurfaceColor(color_call_end, 0f),
                    ),
                    interactionSource = declineInteraction,
                    modifier = Modifier
                        .size(60.dp)
                        .testTag("callRoundActionSurface")
                        .callPressFeedback(declinePressed)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier
                        )
                ) {
                    Icon(
                        DotDialerHangupIcon,
                        contentDescription = stringResource(R.string.decline),
                        tint = CallActionColors.onAction,
                        modifier = Modifier.size(callActionIconSize(DotDialerHangupIcon)),
                    )
                }
                Text(
                    stringResource(R.string.decline),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onMessage,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = trackBgColor.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .size(60.dp)
                        .then(
                            if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default }
                            )
                            else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape    = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects  = { blur(blurIntensity * density) }
                            )
                            else Modifier.background(
                                trackBgColor,
                                CircleShape
                            )
                        )
                ) {
                    Icon(
                        imageVector = CallControlIcons.Message,
                        contentDescription = stringResource(R.string.message),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(callActionIconSize(CallControlIcons.Message)),
                    )
                }
                Text(
                    stringResource(R.string.message),
                    style = MaterialTheme.typography.labelMedium,
                    color = buttonContentColor,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = trackWidth)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .onSizeChanged { trackWidthPx = it.width.toFloat() },
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape           = CircleShape,
                color           = trackBgColor.copy(alpha = 0.35f),
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .height(trackHeight)
                    .align(Alignment.CenterEnd)
                    .width(
                        with(localDensity) {
                            val width = trackWidthPx - offsetX.value
                            width.coerceAtLeast(0f).toDp()
                        }
                    )
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {}

            val baseTextColor = MaterialTheme.colorScheme.onSurface
            val shimmerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, baseTextColor, shimmerColor),
                start = Offset(trackWidthPx * shimmerOffset - 150f, 0f),
                end = Offset(trackWidthPx * shimmerOffset + 150f, 0f)
            )

            Text(
                text = stringResource(R.string.slide_to_answer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = handleSize)
                    .graphicsLayer {
                        alpha = (1f - dragProgress.value * 2f).coerceIn(0f, 1f)
                    },
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = brush,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )

            val answerHandleColor = incomingActionColor(
                handleBgColor,
                color_call_button,
                dragProgress.value,
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((handlePaddingPx + offsetX.value).roundToInt(), 0)
                    }
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(answerHandleColor)
                    .testTag("callAnswerHandle")
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { thresholdHapticSent = false },
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (incomingActionCommitReached(
                                            offsetX.value,
                                            maxDrag * INCOMING_ACTION_TRIGGER_FRACTION,
                                        )) {
                                        offsetX.animateTo(
                                            maxDrag,
                                            tween(
                                                durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                        performIncomingCommitHaptic(view, answer = true)
                                        onAnswer()
                                    } else {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDrag)
                                    offsetX.snapTo(newOffset)
                                    val armed = newOffset >= maxDrag * INCOMING_ACTION_TRIGGER_FRACTION
                                    if (armed && !thresholdHapticSent) {
                                        performIncomingThresholdHaptic(view)
                                        thresholdHapticSent = true
                                    } else if (!armed) {
                                        thresholdHapticSent = false
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    DotDialerHandsetIcon,
                    contentDescription = null,
                    tint = CallActionColors.onAction,
                    modifier = Modifier
                        .size(callActionIconSize(DotDialerHandsetIcon))
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@Composable
fun IncomingCallButtons(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    answerOnStart: Boolean,
    onHoldAndAnswer: () -> Unit,
    onDecline: () -> Unit,
    secondaryActions: (@Composable () -> Unit)? = null,
    secondaryCount: Int = 2,
) {
    val answerLabel = stringResource(R.string.answer)

    val topCount = if (secondaryActions == null) 0 else secondaryCount
    CallActionGrid(
        rowCounts = if (topCount > 0) listOf(topCount, 2) else listOf(2),
        circleDiametersDp = List(topCount) { CALL_SCREEN_ACTION_DIAMETER_DP } + listOf(76, 76),
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
    ) {
        secondaryActions?.invoke()
        if (answerOnStart) {
            IncomingWaitingDecisionButton(
                label = answerLabel,
                icon = DotDialerHandsetIcon,
                tone = IncomingWaitingActionTone.Answer,
                useLg = useLg,
                useBlur = useBlur,
                blurIntensity = blurIntensity,
                onClick = onHoldAndAnswer,
            )
        } else {
            IncomingWaitingDecisionButton(
                label = stringResource(R.string.decline),
                icon = DotDialerHangupIcon,
                tone = IncomingWaitingActionTone.Decline,
                useLg = useLg,
                useBlur = useBlur,
                blurIntensity = blurIntensity,
                onClick = onDecline,
            )
        }

        if (answerOnStart) {
            IncomingWaitingDecisionButton(
                label = stringResource(R.string.decline),
                icon = DotDialerHangupIcon,
                tone = IncomingWaitingActionTone.Decline,
                useLg = useLg,
                useBlur = useBlur,
                blurIntensity = blurIntensity,
                onClick = onDecline,
            )
        } else {
            IncomingWaitingDecisionButton(
                label = answerLabel,
                icon = DotDialerHandsetIcon,
                tone = IncomingWaitingActionTone.Answer,
                useLg = useLg,
                useBlur = useBlur,
                blurIntensity = blurIntensity,
                onClick = onHoldAndAnswer,
            )
        }
    }
}

private enum class IncomingWaitingActionTone { Answer, Decline }

@Composable
private fun IncomingWaitingDecisionButton(
    label: String,
    icon: ImageVector,
    tone: IncomingWaitingActionTone,
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()

    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val idleContainer = when (tone) {
        IncomingWaitingActionTone.Answer -> incomingActionSurfaceColor(color_call_button, 0f)
        IncomingWaitingActionTone.Decline -> incomingActionSurfaceColor(color_call_end, 0f)
    }
    val pressedContainer = when (tone) {
        IncomingWaitingActionTone.Answer -> color_call_button
        IncomingWaitingActionTone.Decline -> color_call_end
    }

    Column(
        modifier = Modifier.width(CALL_ACTION_CELL_WIDTH_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            onClick = {
                performIncomingCommitHaptic(view, answer = tone != IncomingWaitingActionTone.Decline)
                onClick()
            },
            modifier = Modifier
                .size(76.dp)
                .testTag("callRoundActionSurface")
                .callPressFeedback(isPressed)
                .then(
                    when {
                        useLg && globalBackdrop != null ->
                            Modifier.drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.3f)
                                    blur(blurIntensity * d)
                                    lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                                },
                                highlight = { Highlight.Default },
                            )
                        useBlur && globalBackdrop != null ->
                            Modifier.drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape = { CircleShape },
                                shadow = { Shadow(radius = 8.dp) },
                                effects = { blur(blurIntensity * density) },
                            )
                        else -> Modifier
                    },
                ),
            shape = CircleShape,
            color = if (isPressed) pressedContainer else idleContainer,
            interactionSource = interaction,
            tonalElevation = 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = CallActionColors.onAction,
                    modifier = Modifier.size(callActionIconSize(icon)),
                )
            }
        }
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 9.dp, start = 3.dp, end = 3.dp)
                .widthIn(max = 116.dp),
        )
    }
}

@Composable
fun DefaultSwipeToAnswer(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    answerOnStart: Boolean,
    actionGap: Dp,
    bottomPadding: Dp,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    secondaryActions: (@Composable () -> Unit)? = null,
    secondaryCount: Int = 2,
) {
    val coroutineScope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    var thresholdHapticSent by remember { mutableStateOf(false) }

    val trackWidth = DEFAULT_INCOMING_SWIPE_TRACK_WIDTH_DP.dp
    val trackHeight = 80.dp
    val handleHeight = 68.dp
    val handleWidth = 96.dp
    val handlePadding = 6.dp

    var trackWidthPx by remember { mutableFloatStateOf(with(localDensity) { trackWidth.toPx() }) }
    val handleWidthPx = with(localDensity) { handleWidth.toPx() }
    val handlePaddingPx = with(localDensity) { handlePadding.toPx() }

    val maxDrag = incomingSliderTravelPx(trackWidthPx, handleWidthPx, handlePaddingPx)
    val startOffset = if (answerOnStart) 0f else maxDrag
    val offsetX = remember(answerOnStart) { Animatable(startOffset) }
    var previousMaxDrag by remember(answerOnStart) { mutableFloatStateOf(maxDrag) }
    LaunchedEffect(maxDrag) {
        offsetX.snapTo(
            incomingSliderOffsetAfterResize(
                offsetX.value,
                previousMaxDrag,
                maxDrag,
                startsAtStart = answerOnStart,
            ),
        )
        previousMaxDrag = maxDrag
    }

    val buttonBgColor =
        if (useLg || useBlur) bottomBarColor else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val buttonIconColor = MaterialTheme.colorScheme.onSurface
    val idleAnswerColor = incomingActionSurfaceColor(color_call_button, 0f)
    val idleDeclineColor = incomingActionSurfaceColor(color_call_end, 0f)

    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val dragProgress = remember {
        derivedStateOf {
            if (maxDrag <= 0f) 0f
            else if (answerOnStart) offsetX.value / maxDrag
            else (maxDrag - offsetX.value) / maxDrag
        }
    }
    val iconRotation by remember { derivedStateOf {
        dragProgress.value * 135f * if (answerOnStart) 1f else -1f
    } }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(actionGap),
        modifier = Modifier.padding(bottom = bottomPadding),
    ) {
        val topCount = if (secondaryActions == null) 0 else secondaryCount
        CallActionGrid(
            rowCounts = if (topCount > 0) listOf(topCount, 1) else listOf(1),
            circleDiametersDp = List(topCount) { CALL_SCREEN_ACTION_DIAMETER_DP } + listOf(60),
            modifier = Modifier.fillMaxWidth(),
        ) {
            secondaryActions?.invoke()
            DefaultIncomingDeclineAction(useLg, useBlur, blurIntensity, onDecline)
        }

        Box(
            modifier = Modifier
                .widthIn(max = trackWidth)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .onSizeChanged { trackWidthPx = it.width.toFloat() },
            contentAlignment = Alignment.CenterStart
        ) {
            Surface(
                shape           = CircleShape,
                color           = buttonBgColor.copy(alpha = 0.35f),
                shadowElevation = 0.dp,
                tonalElevation  = 0.dp,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                            backdrop = globalBackdrop,
                            shape = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects = {
                                val d = density
                                colorControls(saturation = 1.3f)
                                blur(blurIntensity * d)
                                lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                            },
                            highlight = { Highlight.Default }
                        )
                        else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                            backdrop = globalBackdrop,
                            shape    = { CircleShape },
                            shadow = { Shadow(radius = 8.dp) },
                            effects  = { blur(blurIntensity * density) }
                        )
                        else Modifier
                    )
            ) {}

            val shimmerColor = buttonIconColor.copy(alpha = 0.4f)

            val brush = Brush.linearGradient(
                colors = listOf(shimmerColor, buttonIconColor, shimmerColor),
                start = Offset(trackWidthPx * shimmerOffset - 150f, 0f),
                end = Offset(trackWidthPx * shimmerOffset + 150f, 0f)
            )

            Text(
                text = stringResource(R.string.slide_to_answer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (answerOnStart) handleWidth else 56.dp,
                        end = if (answerOnStart) 56.dp else handleWidth,
                    )
                    .graphicsLayer {
                        alpha = (1f - dragProgress.value * 2f).coerceIn(0f, 1f)
                    },
                style = MaterialTheme.typography.titleMedium.copy(
                    brush = brush,
                    fontWeight = FontWeight.Medium
                ),
                textAlign = TextAlign.Center
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.3f),
                modifier = Modifier
                    .padding(if (answerOnStart) PaddingValues(end = 28.dp) else PaddingValues(start = 28.dp))
                    .size(46.dp)
                    .align(if (answerOnStart) Alignment.CenterEnd else Alignment.CenterStart)
                    .graphicsLayer { scaleX = if (answerOnStart) 1f else -1f }
            )
            Icon(
                Icons.AutoMirrored.Rounded.ArrowRight,
                contentDescription = null,
                tint = Color.White.copy(0.6f),
                modifier = Modifier
                    .padding(if (answerOnStart) PaddingValues(end = 8.dp) else PaddingValues(start = 8.dp))
                    .size(46.dp)
                    .align(if (answerOnStart) Alignment.CenterEnd else Alignment.CenterStart)
                    .graphicsLayer { scaleX = if (answerOnStart) 1f else -1f }
            )

            val answerHandleColor = incomingActionColor(
                idleAnswerColor,
                color_call_button,
                dragProgress.value,
                triggerFraction = DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION,
            )
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((handlePaddingPx + offsetX.value).roundToInt(), 0)
                    }
                    .size(handleWidth, handleHeight)
                    .clip(CircleShape)
                    .background(answerHandleColor)
                    .testTag("callAnswerHandle")
                    .incomingResponseSemantics(onAnswer)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { thresholdHapticSent = false },
                            onDragEnd = {
                                coroutineScope.launch {
                                    if (incomingActionCommitReached(
                                            dragProgress.value,
                                            DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION,
                                        )
                                    ) {
                                        offsetX.animateTo(
                                            if (answerOnStart) maxDrag else 0f,
                                            tween(
                                                durationMillis = INCOMING_ACTION_COMPLETION_DURATION_MS,
                                                easing = FastOutSlowInEasing,
                                            ),
                                        )
                                        performIncomingCommitHaptic(view, answer = true)
                                        onAnswer()
                                    } else {
                                        offsetX.animateTo(startOffset, spring(dampingRatio = 0.8f))
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (offsetX.value + dragAmount).coerceIn(0f, maxDrag)
                                    offsetX.snapTo(newOffset)
                                    val progress = if (maxDrag <= 0f) 0f else if (answerOnStart) {
                                        newOffset / maxDrag
                                    } else {
                                        (maxDrag - newOffset) / maxDrag
                                    }
                                    val armed = progress >= DEFAULT_INCOMING_SWIPE_TRIGGER_FRACTION
                                    if (armed && !thresholdHapticSent) {
                                        performIncomingThresholdHaptic(view)
                                        thresholdHapticSent = true
                                    } else if (!armed) {
                                        thresholdHapticSent = false
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    DotDialerHandsetIcon,
                    contentDescription = null,
                    tint = CallActionColors.onAction,
                    modifier = Modifier
                        .size(callActionIconSize(DotDialerHandsetIcon))
                        .graphicsLayer { rotationZ = iconRotation }
                )
            }
        }
    }
}

@Composable
private fun DefaultIncomingDeclineAction(
    useLg: Boolean,
    useBlur: Boolean,
    blurIntensity: Float,
    onDecline: () -> Unit,
) {
    val view = LocalView.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val idleDeclineColor = incomingActionSurfaceColor(color_call_end, 0f)
    val buttonBgColor = if (useLg || useBlur) bottomBarColor else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.15f else 0.1f)
    val buttonContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    Column(modifier = Modifier.width(CALL_ACTION_CELL_WIDTH_DP.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val declineInteraction = remember { MutableInteractionSource() }
        val declinePressed by declineInteraction.collectIsPressedAsState()
        IconButton(
            onClick = {
                performIncomingCommitHaptic(view, answer = false)
                onDecline()
            },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (declinePressed) color_call_end else idleDeclineColor,
            ),
            interactionSource = declineInteraction,
            modifier = Modifier
                .size(60.dp)
                .testTag("callRoundActionSurface")
                .callPressFeedback(declinePressed)
                .then(
                    if (useLg && globalBackdrop != null) Modifier.drawBackdrop(
                        backdrop = globalBackdrop,
                        shape = { CircleShape },
                        shadow = { Shadow(radius = 8.dp) },
                        effects = {
                            val d = density
                            colorControls(saturation = 1.3f)
                            blur(blurIntensity * d)
                            lens(refractionHeight = 18f * d, refractionAmount = 52f * d)
                        },
                        highlight = { Highlight.Default }
                    )
                    else if (useBlur && globalBackdrop != null) Modifier.drawPlainBackdrop(
                        backdrop = globalBackdrop,
                        shape    = { CircleShape },
                        shadow = { Shadow(radius = 8.dp) },
                        effects  = { blur(blurIntensity * density) }
                    )
                    else Modifier.background(
                        buttonBgColor,
                        CircleShape
                    )
                )
        ) {
            Icon(
                DotDialerHangupIcon,
                contentDescription = stringResource(R.string.decline),
                tint = CallActionColors.onAction,
                modifier = Modifier.size(callActionIconSize(DotDialerHangupIcon)),
            )
        }
        Text(
            stringResource(R.string.decline),
            style = MaterialTheme.typography.labelMedium,
            color = buttonContentColor,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoreItem(
    headline: String,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val feedback = rememberCallTapFeedback(interactionSource, enabled)
    val isPressed = feedback.pressed
    val haptic = rememberCallTapHaptic()
    val fill = rememberCallPressColor(cardColor, isPressed, enabled)
    val click = rememberEnabledCallAction(enabled) { feedback.acknowledge(); haptic(); onClick() }

    Surface(
        color = fill,
        shape = RoundedCornerShape(cardCornerExtraSmall),
        modifier = Modifier
            .fillMaxWidth()
            .callPressFeedback(isPressed, enabled, fill.alpha),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (enabled) {
                        Modifier
                            .alpha(1f)
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = ripple(),
                                onClick = click,
                            )
                    } else {
                        Modifier.alpha(0.5f)
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                RillIconBox(
                    icon = leadingIcon,
                    iconContainerColor = MaterialTheme.colorScheme.onSurface,
                    iconBgContainerColor = MaterialTheme.colorScheme.surface,
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                modifier = Modifier.weight(1f),
                text = headline,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodySmall.lineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    trailingIcon, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
    }
}

@Composable
fun InCallKeypad(
    call: Call,
    typedDigits: String,
    onDigitClick: (Char) -> Unit
) {
    val windowSize = LocalWindowInfo.current.containerSize
    val keyHeight = with(LocalDensity.current) {
        inCallKeyHeightDp(windowSize.width.toDp().value, windowSize.height.toDp().value)
    }
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_DTMF, 80) }
    val dialpadStyle by remember(settingsState) {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_DIALPAD_STYLE, 3))
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val typedDigitsLength = typedDigits.length
        val fontSize = (
                (when {
                    typedDigitsLength > 42 -> 12
                    typedDigitsLength > 38 -> 14
                    typedDigitsLength > 34 -> 16
                    typedDigitsLength > 30 -> 18
                    typedDigitsLength > 25 -> 20
                    typedDigitsLength > 20 -> 24
                    typedDigitsLength > 16 -> 28
                    else -> 36
                }))
        val textStyle = MaterialTheme.typography.displaySmall.copy(
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Light
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .animateContentSize()
        ) { InCallPanelLabel(typedDigits, textStyle.copy(color = MaterialTheme.colorScheme.onSurface)) }

        val keys = listOf(
            listOf('1', '2', '3'),
            listOf('4', '5', '6'),
            listOf('7', '8', '9'),
            listOf('*', '0', '#')
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { key ->
                        KeypadButton(
                            modifier = Modifier.weight(1f).testTag("inCallKey-$key"),
                            key = key,
                            style = dialpadStyle,
                            keyHeightDp = keyHeight,
                            onClick = {
                                if (prefs.getBoolean(PreferenceManager.KEY_DTMF_TONE, true)) {
                                    val toneType = when (key) {
                                        '1' -> ToneGenerator.TONE_DTMF_1
                                        '2' -> ToneGenerator.TONE_DTMF_2
                                        '3' -> ToneGenerator.TONE_DTMF_3
                                        '4' -> ToneGenerator.TONE_DTMF_4
                                        '5' -> ToneGenerator.TONE_DTMF_5
                                        '6' -> ToneGenerator.TONE_DTMF_6
                                        '7' -> ToneGenerator.TONE_DTMF_7
                                        '8' -> ToneGenerator.TONE_DTMF_8
                                        '9' -> ToneGenerator.TONE_DTMF_9
                                        '0' -> ToneGenerator.TONE_DTMF_0
                                        '*' -> ToneGenerator.TONE_DTMF_S
                                        '#' -> ToneGenerator.TONE_DTMF_P
                                        else -> -1
                                    }
                                    if (toneType != -1) {
                                        toneGenerator.startTone(toneType, 120)
                                    }
                                }
                                call.playDtmfTone(key)
                                call.stopDtmfTone()
                                onDigitClick(key)
                            }
                        )
                    }
                }
            }
        }
    }
}



