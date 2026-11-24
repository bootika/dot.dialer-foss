package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.StarRate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.goodwy.rphone.R
import dev.goodwy.rphone.view.theme.accentText

@Composable
fun RatingDialog(
    onDismiss: () -> Unit,
    onRate: (Int) -> Unit
) {
    var selectedRating by remember { mutableIntStateOf(0) }
    var hoveredRating by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.StarRate,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.rate_app),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.rating_feedback_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Star rating
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    for (i in 1..5) {
                        val isSelected = i <= (if (hoveredRating > 0) hoveredRating else selectedRating)
                        IconButton(
                            onClick = {
                                selectedRating = i
                                if (i == 5) {
                                    onRate(5)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                if (isSelected) Icons.Rounded.StarRate else Icons.Rounded.StarOutline,
                                contentDescription = stringResource(R.string.rating_star, i),
                                modifier = Modifier.size(40.dp),
                                tint = if (isSelected)
                                    Color(0xFFFFD700)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                }

                // Rating labels
                Text(
                    text = when (selectedRating) {
                        1 -> stringResource(R.string.rating_terrible)
                        2 -> stringResource(R.string.rating_bad)
                        3 -> stringResource(R.string.rating_okay)
                        4 -> stringResource(R.string.rating_good)
                        5 -> stringResource(R.string.rating_excellent) + " 🎉"
                        else -> stringResource(R.string.rating_prompt)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selectedRating == 5)
                        MaterialTheme.colorScheme.accentText
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selectedRating == 5) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Rating message for non-5 stars
                if (selectedRating in 1..4) {
                    Text(
                        text = stringResource(R.string.rating_appreciation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                if (selectedRating == 5) {
                    Text(
                        text = stringResource(R.string.rating_thanks),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.accentText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Close button
                TextButton(
                    onClick = {
                        if (selectedRating in 1..4) {
                            onRate(selectedRating)
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)
                ) {
                    Text(
                        text = when {
                            selectedRating in 1..5 -> stringResource(R.string.rating_submit)
                            else -> stringResource(R.string.rating_later)
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
