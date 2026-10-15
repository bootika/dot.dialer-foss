package dev.goodwy.rphone.view.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.components.DotDialerHandsetIcon

private val SUPPORTED_DIAL_QUERY = Regex("""^\+?[0-9*#,;]+$""")

/**
 * Returns the dial string represented by a search query once it is actionable.
 *
 * A dial string may contain digits, star/hash, and pause/wait separators. A plus is valid only
 * as the first character. At least three actual digits are required so short search fragments do
 * not turn the search surface into a dial action bar.
 */
internal fun actionableDialQuery(query: String): String? {
    val candidate = query.trim()
    return candidate.takeIf {
        SUPPORTED_DIAL_QUERY.matches(it) && it.count(Char::isDigit) >= 3
    }
}

@Composable
internal fun PhoneSearchQuickActions(
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onAddToContacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceBright,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            PhoneSearchAction(
                label = stringResource(R.string.call),
                icon = DotDialerHandsetIcon,
                onClick = onCall,
                modifier = Modifier.weight(1f),
            )
            PhoneSearchAction(
                label = stringResource(R.string.message),
                icon = Icons.AutoMirrored.Filled.Message,
                onClick = onMessage,
                modifier = Modifier.weight(1f),
            )
            PhoneSearchAction(
                label = stringResource(R.string.add_to_contacts),
                icon = Icons.Default.PersonAdd,
                onClick = onAddToContacts,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PhoneSearchAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 62.dp),
        shape = RoundedCornerShape(17.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
