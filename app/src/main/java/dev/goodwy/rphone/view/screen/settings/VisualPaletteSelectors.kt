package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.view.components.modernPastelAvatarColors
import dev.goodwy.rphone.view.theme.DotBlue
import dev.goodwy.rphone.view.theme.DotCoral
import dev.goodwy.rphone.view.theme.DotElectricIris
import dev.goodwy.rphone.view.theme.DotJade

internal const val AVATAR_PALETTE_MULTICOLOR = 0
internal const val AVATAR_PALETTE_APP_COLOR = 1
private const val AVATAR_PALETTE_PREVIEW_COLUMNS = 10

internal fun accentPreviewColor(key: String): Color = when (key) {
    "blue" -> DotBlue
    "violet" -> DotElectricIris
    "coral" -> DotCoral
    else -> DotJade
}

internal fun avatarPalettePreviewColors(
    palette: Int,
    primaryColor: Color,
): List<Color> = when (palette) {
    AVATAR_PALETTE_APP_COLOR -> modernPastelAvatarColors.map { it.first }
    else -> modernPastelAvatarColors.map { it.first }
}

@Composable
internal fun DotAccentColorChoiceRow(
    title: String,
    choices: List<Pair<String, String>>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
) {
    DotSettingsVisualChoiceRow(
        title = title,
        testTag = "accent-color-selector",
        choices = choices,
        selectedValue = selectedValue,
        onValueChange = onValueChange,
        preview = { key, selected -> AccentSwatch(accentPreviewColor(key), selected) },
    )
}

@Composable
private fun <T> DotSettingsVisualChoiceRow(
    title: String,
    testTag: String,
    choices: List<Pair<String, T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    preview: @Composable (T, Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedChoice = choices.firstOrNull { it.second == selectedValue } ?: choices.first()
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxWidth().settingsSearchTarget(title).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.size(9.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val menuWidth = maxWidth
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().testTag(testTag),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    preview(selectedChoice.second, true)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = selectedChoice.first,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(menuWidth),
                shape = RoundedCornerShape(16.dp),
            ) {
                DotVisualChoiceMenuRows(
                    choices = choices,
                    selectedValue = selectedValue,
                    onValueChange = {
                        onValueChange(it)
                        expanded = false
                    },
                    preview = preview,
                    modifier = Modifier.testTag("$testTag-menu"),
                )
            }
        }
    }
}

@Composable
internal fun <T> DotVisualChoiceMenuRows(
    choices: List<Pair<String, T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    preview: @Composable (T, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(modifier) {
        choices.forEach { (label, choiceValue) ->
            val selected = choiceValue == selectedValue
            val shape = RoundedCornerShape(12.dp)
            DropdownMenuItem(
                text = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        preview(choiceValue, selected)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                onClick = { onValueChange(choiceValue) },
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clip(shape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else Color.Transparent,
                    )
                    .then(
                        if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                        else Modifier,
                    )
                    .semantics { this.selected = selected },
            )
        }
    }
}

@Composable
internal fun AccentSwatch(color: Color, selected: Boolean) {
    val checkColor = if (color.luminance() > 0.48f) Color.Black else Color.White
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = checkColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
internal fun AvatarPalettePreview(colors: List<Color>) {
    Box(modifier = Modifier.width(92.dp).heightIn(min = 30.dp)) {
        colors.forEachIndexed { index, color ->
            val column = index % AVATAR_PALETTE_PREVIEW_COLUMNS
            val row = index / AVATAR_PALETTE_PREVIEW_COLUMNS
            Box(
                modifier = Modifier
                    .offset(x = (column * 8).dp, y = (row * 14).dp)
                    .zIndex(index.toFloat())
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
