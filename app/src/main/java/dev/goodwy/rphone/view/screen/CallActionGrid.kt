package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.roundToInt

internal const val CALL_ACTION_CELL_WIDTH_DP = 88
internal const val CALL_ACTION_LABEL_GAP_DP = 6
internal const val CALL_ACTION_LABEL_CLEARANCE_DP = 4
internal const val CALL_ACTION_CONTROLS_INSET_DP = 24

internal data class CallActionGridGeometry(val columns: Int, val pitch: Int, val cellWidth: Int)

/** One pixel pitch for both axes; fewer columns rather than overlapping enlarged labels. */
internal fun callActionGridGeometry(width: Int, cellWidth: Int, minimumPitch: Int): CallActionGridGeometry {
    val cell = cellWidth.coerceIn(1, width.coerceAtLeast(1))
    for (columns in 3 downTo 2) {
        val maximumPitch = (width - cell) / (columns - 1)
        val pitch = maxOf(minimumPitch, ((width + cell).toFloat() / (columns + 1)).roundToInt())
        if (pitch <= maximumPitch) return CallActionGridGeometry(columns, pitch, cell)
    }
    return CallActionGridGeometry(1, minimumPitch, cell)
}

/** Children start with a circle then a label. Partial rows retain the pitch and stay centered. */
@Composable
internal fun CallActionGrid(
    rowCounts: List<Int>,
    modifier: Modifier = Modifier,
    circleDiametersDp: List<Int> = List(rowCounts.sum()) { CALL_SCREEN_ACTION_DIAMETER_DP },
    content: @Composable () -> Unit,
) {
    require(rowCounts.all { it > 0 } && circleDiametersDp.size == rowCounts.sum())
    val density = LocalDensity.current
    val labelHeight = rememberTextMeasurer().measure(
        text = "Ag",
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        maxLines = 1,
    ).size.height
    val cellWidth = with(density) { CALL_ACTION_CELL_WIDTH_DP.dp.roundToPx() }
    val clearance = with(density) { CALL_ACTION_LABEL_CLEARANCE_DP.dp.roundToPx() }
    // 4dp also covers the optical center difference between 68dp tools and 76dp answer buttons.
    val sharedMinimum = with(density) { (CALL_SCREEN_ACTION_DIAMETER_DP + 4 + CALL_ACTION_LABEL_GAP_DP).dp.toPx() } + labelHeight + clearance
    val diameters = circleDiametersDp.map { with(density) { it.dp.roundToPx() } }
    Box(modifier = modifier) {
    Layout(content = content, modifier = Modifier.fillMaxWidth().testTag("callRoundActionGrid")) { measurables, constraints ->
        check(measurables.size == rowCounts.sum())
        val width = constraints.maxWidth
        val anchorDiameter = diameters.max()
        val measuredCellWidth = maxOf(cellWidth, anchorDiameter).coerceAtMost(width)
        val children = measurables.map { it.measure(Constraints.fixedWidth(measuredCellWidth)) }
        val offsets = diameters.map { (anchorDiameter - it) / 2 }
        // Include measured labels of every row that has another row below it.
        val precedingCount = rowCounts.sum() - rowCounts.last()
        val measuredMinimum = (0 until precedingCount).maxOfOrNull { children[it].height + offsets[it] + clearance } ?: 0
        var geometry = callActionGridGeometry(width, measuredCellWidth,
            maxOf(ceil(sharedMinimum).toInt(), anchorDiameter + clearance, measuredMinimum))
        fun rowsFor(columns: Int) = rowCounts.flatMap { count ->
            List((count + columns - 1) / columns) { row -> minOf(columns, count - row * columns) }
        }
        // Reflow can make a formerly final cell precede another row. Include its label too.
        repeat(3) {
            val preceding = children.size - rowsFor(geometry.columns).last()
            val required = (0 until preceding).maxOfOrNull { children[it].height + offsets[it] + clearance } ?: 0
            if (required > geometry.pitch) geometry = callActionGridGeometry(width, measuredCellWidth, required)
        }
        val rows = rowsFor(geometry.columns)
        var lastStart = 0
        rows.dropLast(1).forEach { lastStart += it }
        val lastHeight = (lastStart until children.size).maxOf { children[it].height + offsets[it] }
        val height = geometry.pitch * (rows.size - 1) + lastHeight
        layout(width, constraints.constrainHeight(height)) {
            var index = 0
            rows.forEachIndexed { row, count ->
                val startCenter = (width - (count - 1) * geometry.pitch) / 2f
                repeat(count) { column ->
                    children[index].placeRelative(
                        x = (startCenter + column * geometry.pitch - children[index].width / 2f).roundToInt(),
                        y = row * geometry.pitch + offsets[index],
                    )
                    index++
                }
            }
        }
    }
    }
}
