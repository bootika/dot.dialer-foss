package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R

@Composable
internal fun BrowseContactsTitle() {
    Text(
        text = stringResource(R.string.contacts),
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(
            start = 20.dp,
            end = 20.dp,
            top = BROWSE_CONTACTS_TITLE_TOP_PADDING_DP.dp,
            bottom = 4.dp,
        ),
    )
}

@Composable
internal fun HomeAlphabetHeader(letter: String) {
    Text(
        text = letter,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.accentText,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 6.dp),
    )
}

@Composable
internal fun HomeSectionHeader(title: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 11.dp, bottom = 6.dp),
        )
    }
}

@Composable
internal fun FavoritesHeader(
    editing: Boolean,
    hasFavorites: Boolean,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.favorites),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        if (!hasFavorites || editing) {
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_favorites))
            }
        }
        if (hasFavorites) {
            IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                Icon(
                    if (editing) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = if (editing) {
                        stringResource(R.string.done)
                    } else {
                        stringResource(R.string.edit)
                    },
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
