package dev.goodwy.rphone.view.screen.onboarding

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.koin.compose.koinInject
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MorphingOnboardingScreen(onPlanSettings: (io.github.bootika.dotdialer.core.license.AppPlan) -> Unit = {}, onFinished: () -> Unit) {
    var setupStage by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    val prefs = koinInject<dev.goodwy.rphone.controller.util.PreferenceManager>()
    var swipeToCall by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(prefs.getInt(dev.goodwy.rphone.controller.util.PreferenceManager.KEY_RECENT_TAP_ACTION, -1) == dev.goodwy.rphone.controller.util.PreferenceManager.SWIPE_ACTION_CONTACT)
    }
    if (setupStage == 1) {
        CallStyleSetup(prefs, swipeToCall, { swipeToCall = it }, onBack = { setupStage = 0 },
            onFinished = { setupStage = 2 })
        return
    }
    val plans = dev.goodwy.rphone.view.screen.settings.rememberPlanAccessManager()
    val planState by plans.state.collectAsState()
    val revision by prefs.settingsChanged.collectAsState()
    val acknowledged = remember(revision) {io.github.bootika.dotdialer.core.license.AppPlan.entries.getOrElse(prefs.getInt(dev.goodwy.rphone.view.screen.settings.PLAN_GUIDE_ACK,0)) {io.github.bootika.dotdialer.core.license.AppPlan.FREE}}
    val pending = remember(planState,acknowledged) {io.github.bootika.dotdialer.core.license.PlanGuidePolicy.pending(plans.effective,acknowledged)}
    if (setupStage == 2 && pending.isEmpty()) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onFinished() }
        return
    }
    if(setupStage == 2 && pending.isNotEmpty()) {
        dev.goodwy.rphone.view.screen.settings.PlanGuideSequenceContent(pending, onFinished={
            prefs.setInt(dev.goodwy.rphone.view.screen.settings.PLAN_GUIDE_ACK,maxOf(acknowledged,pending.last()).ordinal)
            onFinished()
        },onMore={ tier ->
            prefs.setInt(dev.goodwy.rphone.view.screen.settings.PLAN_GUIDE_ACK,maxOf(acknowledged,tier).ordinal)
            onPlanSettings(tier); onFinished()
        },onBackAtStart={setupStage=1}, welcomePlan=plans.effective)
        return
    }
    EssentialOnboardingPage(onNext = { setupStage = 1 })
}

class GearShape(
    private val teethCount: Int = 8,
    private val toothRadius: Float = 0.85f,
    private val innerRadius: Float = 0.65f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val width = size.width
        val height = size.height
        val centerX = width / 2
        val centerY = height / 2
        val outerR = minOf(width, height) / 2
        val toothR = outerR * toothRadius
        val innerR = outerR * innerRadius

        val path = Path()
        val angleStep = 360.0 / teethCount

        for (i in 0 until teethCount) {
            val angle1 = i * angleStep
            val angle2 = angle1 + angleStep / 2
            val angle3 = angle1 + angleStep

            val rad1 = Math.toRadians(angle1)
            val rad2 = Math.toRadians(angle2)
            val rad3 = Math.toRadians(angle3)

            val x1 = centerX + innerR * cos(rad1).toFloat()
            val y1 = centerY + innerR * sin(rad1).toFloat()

            val x2 = centerX + toothR * cos(rad2).toFloat()
            val y2 = centerY + toothR * sin(rad2).toFloat()

            val x3 = centerX + innerR * cos(rad3).toFloat()
            val y3 = centerY + innerR * sin(rad3).toFloat()

            if (i == 0) path.moveTo(x1, y1)
            path.lineTo(x2, y2)
            path.lineTo(x3, y3)
        }
        path.close()

        return Outline.Generic(path)
    }
}

fun wavyCircleShape(
    waveCount: Int = 12,
    waveAmplitude: Float = 0.04f
): Shape = GenericShape { size, _ ->
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxRadius = minOf(size.width, size.height) / 2f
    val baseRadius = maxRadius * (1f - waveAmplitude)
    val steps = 720

    val startX = cx + baseRadius * (1f + waveAmplitude * sin(0f))
    val startY = cy
    moveTo(startX, startY)

    for (i in 1..steps) {
        val angle = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
        val r = baseRadius * (1f + waveAmplitude * sin(waveCount * angle))
        val x = cx + r * cos(angle)
        val y = cy + r * sin(angle)
        lineTo(x, y)
    }
    close()
}
