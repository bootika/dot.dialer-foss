package dev.goodwy.rphone.view.screen.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.DARK_EDGE_RADIO_URL
import dev.goodwy.rphone.R
import dev.goodwy.rphone.dotDictateUrl
import dev.goodwy.rphone.controller.util.openLink
import dev.goodwy.rphone.view.components.RillExpressiveCard

@Composable
internal fun AboutOtherApps(language: String) {
    Column(Modifier.fillMaxWidth()) {
        SettingsSectionLabel(stringResource(R.string.other_apps))
        RillExpressiveCard {
            OtherAppRow(R.string.dark_edge_radio, R.string.dark_edge_radio_description,
                R.drawable.ic_dark_edge_radio, DARK_EDGE_RADIO_URL, R.string.open_in_google_play)
            OtherAppRow(R.string.dot_dictate, R.string.dot_dictate_description,
                R.drawable.ic_dot_dictate_about, dotDictateUrl(language), R.string.open_app_website)
        }
    }
}

@Composable
private fun OtherAppRow(
    @StringRes name: Int,
    @StringRes description: Int,
    @DrawableRes logo: Int,
    url: String,
    @StringRes linkDescription: Int,
) {
    val context = LocalContext.current
    Surface(onClick = { openLink(context, url) }, Modifier.fillMaxWidth(), color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(logo), contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(15.dp)).testTag("other-app-logo-$logo"))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(name), style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold)
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(linkDescription),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
