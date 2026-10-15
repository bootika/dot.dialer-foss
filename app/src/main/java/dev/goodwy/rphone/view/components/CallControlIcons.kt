package dev.goodwy.rphone.view.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** One optical family for call controls. Sizes are viewports, not touch targets.
 * Compensate the vector stroke for viewport size so every rendered line is 2.5 dp.
 * Phone silhouettes remain the separately approved, shared brand handset pair.
 */
internal object CallControlIconStyle {
    const val StrokeWidthDp = 2.5f
    const val DefaultSizeDp = 27f
    const val PhoneSizeDp = 32f
}

internal object CallControlIcons {
    val Add = lineIcon("CallAdd", 33f, "M12,5 L12,19 M5,12 L19,12")
    val Mic = lineIcon("CallMic", 27f, "M9,6 C9,2 15,2 15,6 L15,12 C15,16 9,16 9,12 Z M5,11 L5,12 C5,21 19,21 19,12 L19,11 M12,19 L12,22")
    // Interrupted contour leaves breathing room around the diagonal rather than a heavy knot.
    val MicOff = lineIcon("CallMicOff", 27f, "M9,6 C9,2 15,2 15,6 L15,10 M9,10 L9,12 C9,15 12,16 14,14 M5,11 L5,12 C5,18 11,21 16,18 M19,11 L19,12 C19,13 19,14 18,15 M12,19 L12,22 M3,3 L21,21")
    val Note = lineIcon("CallNote", 27f, "M5,3 L19,3 Q21,3 21,5 L21,15 L15,21 L5,21 Q3,21 3,19 L3,5 Q3,3 5,3 Z M15,21 L15,15 L21,15 M7,8 L17,8 M7,12 L12,12", autoMirror = true)
    val Message = lineIcon("CallMessage", 27f, "M5,4 L19,4 Q21,4 21,6 L21,16 Q21,18 19,18 L9,18 L3,22 L3,6 Q3,4 5,4 Z")
    val Reminder = lineIcon("CallReminder", 27f, "M20,13 A8,8 0,1 1,4,13 A8,8 0,1 1,20,13 M12,8 L12,13 L15,15 M3,5 L6,2 M18,2 L21,5 M6,20 L4,22 M18,20 L20,22")
    val Speaker = lineIcon("CallSpeaker", 27f, "M3,9 L7,9 L12,5 L12,19 L7,15 L3,15 Z M16,8 C19,10 19,14 16,16 M19,4 C23,8 23,16 19,20", autoMirror = true)
    val Bluetooth = lineIcon("CallBluetooth", 29f, "M12,3 L18,8 L6,18 M12,3 L12,21 L18,16 L6,6")
    val Headset = lineIcon("CallHeadset", 27f, "M3,13 L3,11 C3,0 21,0 21,11 L21,13 M3,12 L7,12 L7,21 L3,21 Z M17,12 L21,12 L21,21 L17,21 Z")
    val Resume = DotDialerHandsetIcon
    val Swap = lineIcon("CallSwap", 28f, "M3,8 L7,4 L11,8 M7,4 L7,17 Q7,20 10,20 M13,16 L17,20 L21,16 M17,20 L17,7 Q17,4 14,4")
    val Keypad = dotsIcon("CallKeypad", 29f, listOf(4f, 9.5f, 15f).flatMap { y -> listOf(6.5f, 12f, 17.5f).map { x -> x to y } } + (12f to 20.5f))
    val More = dotsIcon("CallMore", 31f, listOf(12f to 5.5f, 12f to 12f, 12f to 18.5f))
    val MoreHorizontal = dotsIcon("CallMoreHorizontal", 31f, listOf(5.5f to 12f, 12f to 12f, 18.5f to 12f))
}

private fun lineIcon(name: String, sizeDp: Float, path: String, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(name, sizeDp.dp, sizeDp.dp, 24f, 24f, autoMirror = autoMirror).apply {
        addPath(
            pathData = PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = CallControlIconStyle.StrokeWidthDp * 24f / sizeDp,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

private fun dotsIcon(name: String, sizeDp: Float, centers: List<Pair<Float, Float>>): ImageVector =
    ImageVector.Builder(name, sizeDp.dp, sizeDp.dp, 24f, 24f).apply {
        val radius = 2.15f * 24f / sizeDp // same physical dot radius for keypad and both More orientations
        centers.forEach { (x, y) ->
            addPath(pathData = PathParser().parsePathString(
                "M${x-radius},$y A$radius,$radius 0,1 0,${x+radius},$y A$radius,$radius 0,1 0,${x-radius},$y Z",
            ).toNodes(), fill = SolidColor(Color.Black))
        }
    }.build()
