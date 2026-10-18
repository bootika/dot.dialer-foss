package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.controlLabel
import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R

@Composable
internal fun DotSettingsTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        },
        navigationIcon = {
            Surface(
                onClick = onBack,
                modifier = Modifier.padding(start = 12.dp).size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
            }
        },
    )
}

@Composable
internal fun DotSettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column { content() }
    }
}

@Composable
internal fun DotSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    )
}

@Composable
internal fun DotSettingsSwitchRow(
    title: String,
    supporting: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    contentActive: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsSearchTarget(title)
            .alpha(if (contentActive) 1f else .45f)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.controlLabel)
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        )
    }
}

internal enum class DotSettingsChoicePresentation {
    Segmented,
    InlineSelect,
    Dialog,
}

internal fun dotSettingsChoicePresentation(choiceCount: Int): DotSettingsChoicePresentation =
    when (choiceCount) {
        2, 3 -> DotSettingsChoicePresentation.Segmented
        in 1..8 -> DotSettingsChoicePresentation.InlineSelect
        else -> DotSettingsChoicePresentation.Dialog
    }

@Composable
private fun <T> DotSettingsSegmentedChoice(
    title: String,
    choices: List<Pair<String, T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().settingsSearchTarget(title).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(title, style = MaterialTheme.typography.controlLabel)
        val shape = RoundedCornerShape(14.dp)
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val density = androidx.compose.ui.platform.LocalDensity.current
        val labelStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val dividers = with(density) { 1.dp.roundToPx() } * (choices.size - 1)
            val labelWidth = ((constraints.maxWidth - dividers) / choices.size - with(density) { 16.dp.roundToPx() }).coerceAtLeast(1)
            val labelHeight = choices.maxOf { measurer.measure(it.first, labelStyle,
                constraints = androidx.compose.ui.unit.Constraints(maxWidth = labelWidth)).size.height }
            val rowHeight = with(density) { labelHeight.toDp() + 16.dp }.coerceAtLeast(48.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.58f),
                    shape = shape,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            choices.forEachIndexed { index, (label, choiceValue) ->
                val selected = choiceValue == selectedValue
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surface,
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onValueChange(choiceValue) },
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = Int.MAX_VALUE,
                    )
                }
                if (index < choices.lastIndex) {
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun <T> DotSettingsInlineSelector(
    title: String,
    value: String,
    choices: List<Pair<String, T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    mutedValues: Set<T> = emptySet(),
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().settingsSearchTarget(title).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(title, style = MaterialTheme.typography.controlLabel)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val menuWidth = maxWidth
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = value,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = Int.MAX_VALUE,
                    )
                    Spacer(Modifier.width(8.dp))
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
                choices.forEach { (label, choiceValue) ->
                    val selected = choiceValue == selectedValue
                    DropdownMenuItem(
                        modifier = Modifier.alpha(if (choiceValue in mutedValues) .45f else 1f),
                        text = {
                            Text(
                                text = label,
                                fontWeight = FontWeight.Medium,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.accentText
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        },
                        onClick = {
                            onValueChange(choiceValue)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun DotSettingsChoiceRow(
    title: String,
    value: String,
    choices: List<Pair<String, Int>>,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    mutedValues: Set<Int> = emptySet(),
) {
    when (dotSettingsChoicePresentation(choices.size)) {
        DotSettingsChoicePresentation.Segmented -> {
            DotSettingsSegmentedChoice(title, choices, selectedValue, onValueChange)
            return
        }
        DotSettingsChoicePresentation.InlineSelect -> {
            DotSettingsInlineSelector(title, value, choices, selectedValue, onValueChange, mutedValues)
            return
        }
        DotSettingsChoicePresentation.Dialog -> Unit
    }
    var dialogVisible by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsSearchTarget(title)
            .clickable { dialogVisible = true }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsChoiceSummary(title, value, Modifier.weight(1f))
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text(title) },
            text = {
                Column {
                    choices.forEach { (label, choiceValue) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(choiceValue)
                                    dialogVisible = false
                                }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = choiceValue == selectedValue, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.controlLabel)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogVisible = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
internal fun DotSettingsStringChoiceRow(
    title: String,
    value: String,
    choices: List<Pair<String, String>>,
    selectedValue: String,
    onValueChange: (String) -> Unit,
) {
    when (dotSettingsChoicePresentation(choices.size)) {
        DotSettingsChoicePresentation.Segmented -> {
            DotSettingsSegmentedChoice(title, choices, selectedValue, onValueChange)
            return
        }
        DotSettingsChoicePresentation.InlineSelect -> {
            DotSettingsInlineSelector(title, value, choices, selectedValue, onValueChange)
            return
        }
        DotSettingsChoicePresentation.Dialog -> Unit
    }
    var dialogVisible by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val showSearch = choices.size > 8
    val filteredChoices = remember(choices, query) {
        if (!showSearch) choices
        else choices.filter { (label, code) ->
            query.isBlank() || label.contains(query, ignoreCase = true) || code.contains(query, ignoreCase = true)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsSearchTarget(title)
            .clickable {
                query = ""
                dialogVisible = true
            }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsChoiceSummary(title, value, Modifier.weight(1f))
        Spacer(Modifier.width(5.dp))
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
    }
    if (dialogVisible) {
        AlertDialog(
            onDismissRequest = { dialogVisible = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(if (showSearch) 10.dp else 0.dp)) {
                    if (showSearch) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text(stringResource(R.string.search)) },
                            trailingIcon = { dev.goodwy.rphone.view.components.VoiceSearchButton(onResult = { query = it }) },
                        )
                    }
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(filteredChoices.size) { index ->
                            val (label, choiceValue) = filteredChoices[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onValueChange(choiceValue)
                                        dialogVisible = false
                                    }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = choiceValue == selectedValue, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label, style = MaterialTheme.typography.controlLabel)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { dialogVisible = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
