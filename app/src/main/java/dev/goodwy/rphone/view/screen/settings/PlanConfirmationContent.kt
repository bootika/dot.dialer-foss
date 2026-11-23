package dev.goodwy.rphone.view.screen.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.license.PlanConfirmation
import dev.goodwy.rphone.controller.license.PlanConfirmationSource
import dev.goodwy.rphone.view.screen.onboarding.onboardingPrimaryButtonColors
import dev.goodwy.rphone.view.theme.DotJade
import dev.goodwy.rphone.view.theme.accentText
import io.github.bootika.dotdialer.core.license.AppPlan
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun PlanConfirmationHost(visible: Boolean, contentWhenClear: @Composable (Boolean) -> Unit) {
    val manager = rememberPlanAccessManager()
    val pending by manager.confirmation.collectAsStateWithLifecycle()
    val confirmation = pending
    val scope = rememberCoroutineScope()
    if (visible && confirmation != null) {
        val acknowledge: () -> Unit = { scope.launch { manager.acknowledgeConfirmation(confirmation) } }
        Dialog(onDismissRequest = acknowledge, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        )) {
            PlanConfirmationContent(confirmation, acknowledge)
        }
    } else if (confirmation == null) {
        contentWhenClear(manager.lastConfirmedPlan == manager.effective)
    }
}

@Composable
internal fun PlanConfirmationContent(confirmation: PlanConfirmation, onDone: () -> Unit) {
    val trial = confirmation.source == PlanConfirmationSource.TRIAL
    val plan = confirmation.plan.displayName
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val date = remember(confirmation.until, locale) {
        confirmation.until?.let {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
                .format(Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()))
        }
    }
    val title = stringResource(if (trial) R.string.confirmation_trial_title else R.string.confirmation_paid_title)
    val recovery = stringResource(when (confirmation.source) {
        PlanConfirmationSource.TRIAL -> R.string.confirmation_recover_trial
        PlanConfirmationSource.PLAY_SUBSCRIPTION, PlanConfirmationSource.PLAY_LIFETIME -> R.string.confirmation_recover_play
        PlanConfirmationSource.GIFT -> R.string.confirmation_recover_gift
        PlanConfirmationSource.DESK, PlanConfirmationSource.INVITATION -> R.string.confirmation_recover_grant
    })
    var recoveryOpen by rememberSaveable(confirmation) { mutableStateOf(false) }
    BackHandler(enabled = recoveryOpen) { recoveryOpen = false }
    Surface(
        modifier = Modifier.fillMaxSize().testTag("plan-confirmation"),
        color = if (referencePalette()) Color(0xFFF3F2EF) else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            val compact = maxHeight < 605.dp || LocalDensity.current.fontScale >= 1.25f
            val dense = maxHeight < 605.dp
            val landscape = maxHeight < 420.dp && maxWidth >= 520.dp
            val side = if (dense) 14.dp else 20.dp
            val intro = stringResource(when {
                trial && dense -> R.string.confirmation_trial_intro_compact
                trial -> R.string.confirmation_trial_intro
                else -> R.string.confirmation_paid_intro
            }, plan)
            val hero: @Composable () -> Unit = {
                ConfirmationHero(title, intro, dense, compact)
            }
            val details: @Composable () -> Unit = {
                ConfirmationCard(confirmation, date, dense)
                if (trial) Text(
                    stringResource(R.string.confirmation_trial_charge),
                    modifier = Modifier.fillMaxWidth().padding(top = if (dense) 4.dp else 9.dp),
                    textAlign = TextAlign.Center,
                    style = if (dense) MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp)
                        else MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (landscape && !recoveryOpen) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = side, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { hero() }
                    Column(Modifier.weight(1.25f), horizontalAlignment = Alignment.CenterHorizontally) {
                        details()
                        ConfirmationActions(dense, { recoveryOpen = true }, onDone)
                    }
                }
            } else {
                Column(
                    Modifier.widthIn(max = 420.dp).fillMaxSize().padding(horizontal = side),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(if (dense) 4.dp else if (compact) 8.dp else 24.dp))
                    if (recoveryOpen) {
                        Spacer(Modifier.weight(1f))
                        ConfirmationHero(stringResource(R.string.confirmation_recovery_title), recovery, dense, compact)
                        Spacer(Modifier.weight(1f))
                        ConfirmationActions(dense, null) { recoveryOpen = false }
                    } else {
                        hero()
                        details()
                        Spacer(Modifier.weight(1f))
                        ConfirmationActions(dense, { recoveryOpen = true }, onDone)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationHero(title: String, intro: String, dense: Boolean, compact: Boolean) {
    val body = MaterialTheme.typography.bodyMedium
    Column(
        Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (dense) 3.dp else 7.dp),
    ) {
        ConfirmationArt(if (dense) 32.dp else if (compact) 68.dp else 92.dp)
        Text(
            "dot.dialer", fontSize = if (dense) 10.sp else 13.sp, fontWeight = FontWeight.Bold,
            color = confirmationAccent(),
        )
        Text(
            title, modifier = Modifier.semantics { heading() }.testTag("confirmation-title"),
            fontSize = if (dense) 20.sp else if (compact) 27.sp else 31.sp,
            lineHeight = if (dense) 22.sp else if (compact) 29.sp else 35.sp,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, letterSpacing = (-0.65).sp,
        )
        Text(
            intro, modifier = Modifier.padding(bottom = if (dense) 6.dp else 14.dp),
            style = if (dense) body.copy(fontSize = 10.sp, lineHeight = 12.sp) else body,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConfirmationCard(confirmation: PlanConfirmation, date: String?, dense: Boolean) {
    val trial = confirmation.source == PlanConfirmationSource.TRIAL
    val recurring = confirmation.source == PlanConfirmationSource.PLAY_SUBSCRIPTION
    val colors = MaterialTheme.colorScheme
    val divider = if (referencePalette()) Color(0xFFDFE3DA) else colors.outlineVariant
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("confirmation-details"),
        color = if (referencePalette()) Color(0xFFFAFAF7) else colors.surfaceBright, shape = RoundedCornerShape(22.dp),
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, divider),
    ) {
        Column(Modifier.padding(horizontal = if (dense) 9.dp else 13.dp)) {
            ConfirmationRow(
                Icons.Outlined.CalendarToday,
                title = date ?: stringResource(if (confirmation.permanent) R.string.confirmation_no_expiry else R.string.confirmation_access_active),
                caption = stringResource(when {
                    trial -> R.string.confirmation_trial_until
                    recurring && date != null -> R.string.confirmation_paid_until
                    date != null -> R.string.confirmation_access_until
                    else -> R.string.confirmation_duration
                }),
                dense = dense,
            )
            HorizontalDivider(color = divider)
            ConfirmationRow(
                Icons.Outlined.AutoAwesome,
                stringResource(if (trial) R.string.confirmation_try else R.string.confirmation_included),
                body = stringResource(when {
                    confirmation.plan == AppPlan.PLUS && dense -> R.string.confirmation_plus_features_compact
                    confirmation.plan == AppPlan.PLUS -> R.string.plan_plus_summary
                    dense -> R.string.confirmation_complete_features_compact
                    else -> R.string.confirmation_complete_features
                }),
                dense = dense,
            )
            if (trial || recurring) {
                HorizontalDivider(color = divider)
                ConfirmationRow(
                    Icons.Outlined.Info,
                    stringResource(if (trial) R.string.confirmation_after_trial else R.string.confirmation_subscription),
                    body = when {
                        !trial -> stringResource(R.string.confirmation_manage_subscription)
                        confirmation.basePlan == AppPlan.FREE -> stringResource(
                            if (dense) R.string.confirmation_after_free_compact else R.string.confirmation_after_free,
                        )
                        else -> stringResource(R.string.confirmation_after_plan, confirmation.basePlan.displayName)
                    },
                    dense = dense,
                )
            }
        }
    }
}

@Composable
private fun ConfirmationRow(icon: ImageVector, title: String, caption: String? = null, body: String? = null, dense: Boolean) {
    val colors = MaterialTheme.colorScheme
    val textSize = if (dense) 11.sp else 13.sp
    Row(
        Modifier.fillMaxWidth().padding(vertical = if (dense) 3.dp else 11.dp),
        horizontalArrangement = Arrangement.spacedBy(if (dense) 8.dp else 10.dp),
    ) {
        Box(
            Modifier.size(if (dense) 27.dp else 33.dp).background(colors.primary.copy(alpha = .12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(if (dense) 15.dp else 17.dp), tint = confirmationAccent())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (caption != null) Text(caption, fontSize = textSize, lineHeight = textSize * 1.18f, color = colors.onSurfaceVariant)
            Text(title, fontSize = if (dense) 12.sp else 14.sp, lineHeight = if (dense) 14.sp else 17.sp, fontWeight = FontWeight.Bold)
            if (body != null) Text(body, fontSize = textSize, lineHeight = textSize * 1.28f, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConfirmationActions(dense: Boolean, onRecovery: (() -> Unit)?, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = if (dense) 6.dp else 10.dp)) {
        if (onRecovery != null) TextButton(
            onClick = onRecovery, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("confirmation-recovery"),
            colors = ButtonDefaults.textButtonColors(contentColor = confirmationAccent()),
        ) {
            Text(stringResource(R.string.confirmation_recovery_title), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                fontSize = if (dense) 12.sp else 14.sp)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp))
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().heightIn(min = if (dense) 48.dp else 50.dp).testTag("confirmation-done"),
            shape = CircleShape, colors = onboardingPrimaryButtonColors(),
        ) {
            Text(
                stringResource(if (onRecovery == null) R.string.back else R.string.confirmation_done),
                textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = if (dense) 13.sp else 16.sp,
            )
        }
    }
}

@Composable
private fun ConfirmationArt(size: Dp) {
    val colors = MaterialTheme.colorScheme
    val brand = confirmationAccent()
    Box(Modifier.size(size).background(colors.primary.copy(alpha = .12f), CircleShape)) {
        Surface(
            Modifier.align(Alignment.Center).offset(y = -(size * .045f)).rotate(-12f).size(size * .45f, size * .68f),
            color = if (referencePalette()) Color(0xFFF3F2EF) else colors.background, shape = RoundedCornerShape(size * .11f),
            border = BorderStroke(size * .022f, brand),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Spacer(Modifier.align(Alignment.TopCenter).padding(top = size * .09f).width(size * .12f)
                    .height(size * .02f).background(brand.copy(alpha = .42f), CircleShape))
                Icon(Icons.Outlined.Phone, null, Modifier.size(size * .23f), tint = brand)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val width = this.size.width
            val spark = Offset(width * .88f, width * .15f)
            val arm = width * .046f
            drawLine(brand, spark - Offset(arm, 0f), spark + Offset(arm, 0f), width * .014f, StrokeCap.Round)
            drawLine(brand, spark - Offset(0f, arm), spark + Offset(0f, arm), width * .014f, StrokeCap.Round)
            drawCircle(brand.copy(alpha = .44f), width * .026f, Offset(width * .14f, width * .40f))
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(end = size * .045f, bottom = size * .055f)
                .size(size * .31f).background(brand, CircleShape), contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(size * .18f), tint = colors.background)
        }
    }
}

@Composable
private fun referencePalette(): Boolean = with(MaterialTheme.colorScheme) {
    primary == DotJade && background.luminance() > 0.5f
}

@Composable
private fun confirmationAccent() = if (referencePalette()) Color(0xFF008057) else MaterialTheme.colorScheme.accentText
