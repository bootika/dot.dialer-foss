package dev.goodwy.rphone.view.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import dev.goodwy.rphone.R

/** Fixed navigation follows the eligible sequence, never a hardcoded plan/page count. */
@Composable
internal fun GuideNavigationControls(isLast: Boolean, onNext: () -> Unit, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (onBack != null) {
            FilledIconButton(onClick = onBack, modifier = Modifier.size(56.dp).testTag("plan-guide-back"),
                shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), Modifier.size(24.dp))
            }
        }
        if (isLast) {
            Button(onClick = onNext, modifier = Modifier.weight(1f).heightIn(min = 56.dp).testTag("plan-guide-next")) {
                Text(stringResource(R.string.plan_guide_begin), textAlign = TextAlign.Center)
            }
        } else {
            Spacer(Modifier.weight(1f))
            FilledIconButton(onClick = onNext, modifier = Modifier.size(56.dp).testTag("plan-guide-next"),
                shape = CircleShape) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.next), Modifier.size(24.dp))
            }
        }
    }
}
