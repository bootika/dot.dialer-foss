package dev.goodwy.rphone.view.screen.onboarding

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import org.koin.compose.koinInject
import kotlin.math.cos
import kotlin.math.sin

data class MorphingPage(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val shapeCornerPercent: Int,
    val rotation: Float,
    val scale: Float,
    val teethCount: Int,
    val showPhotoAuthorization: Boolean = false,
)

@Composable
fun MorphingOnboardingScreen(onFinished: () -> Unit) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val pages = listOf(MorphingPage(
        icon = Icons.Default.Dialpad,
        title = stringResource(R.string.onboarding_essential_title),
        description = stringResource(if (dev.goodwy.rphone.BuildConfig.FLAVOR == "gplay") R.string.onboarding_essential_body_gplay else R.string.onboarding_essential_body,
            stringResource(R.string.advanced_settings), stringResource(R.string.diagnostics)),
        shapeCornerPercent = 40, rotation = 12f, scale = 1f, teethCount = 12,
    ))
    val cornerPercent = 40f
    val rotation = 0f
    val scale = 1f
    val shapeSize = (140 * scale).dp
    val teethCount = 12

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background morphing shapes
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .offset(x = (-50).dp, y = 100.dp)
                    .rotate(rotation * 2)
                    .clip(RoundedCornerShape(cornerPercent.toInt()))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .size(150.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 50.dp, y = 200.dp)
                    .rotate(-rotation)
                    .clip(RoundedCornerShape((50 - cornerPercent / 2).toInt()))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
            )

            Box(
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.CenterStart)
                    .offset(x = 20.dp, y = (140).dp)
                    .rotate(rotation * 1.5f)
                    .scale(scale)
                    .clip(wavyCircleShape(teethCount)) //GearShape(teethCount = teethCount)
                    .background(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                    )
            )

            Column(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Spacer(Modifier.height(24.dp))
                    Box(
                        Modifier.size(shapeSize).rotate(rotation)
                            .clip(RoundedCornerShape(cornerPercent.toInt()))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(pages[0].icon, null, Modifier.size(56.dp).rotate(-rotation),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(if (isLandscape) 24.dp else 40.dp))
                    Text(pages[0].title, style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(pages[0].description, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                }
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = RoundedCornerShape(28.dp)) {
                    Text(stringResource(R.string.onboarding_start))
                }
            }
        }
    }
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
