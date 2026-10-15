package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.ContactPhotoReadiness

@Composable
internal fun ContactPhotoReadinessRow(state: ContactPhotoReadiness, onSetup: () -> Unit) {
    val message = when (state) {
        ContactPhotoReadiness.Unavailable -> R.string.contact_photos_whatsapp_unavailable
        ContactPhotoReadiness.Off -> R.string.contact_photos_whatsapp_off
        ContactPhotoReadiness.ConsentRequired -> R.string.contact_photos_whatsapp_consent_required
        ContactPhotoReadiness.NotificationAccessRequired -> R.string.contact_photos_whatsapp_access_required
        ContactPhotoReadiness.ContactsAccessRequired -> R.string.contact_photos_whatsapp_contacts_required
        ContactPhotoReadiness.Ready -> R.string.contact_photos_whatsapp_ready
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("contactPhotoReadiness")) {
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
        if (state == ContactPhotoReadiness.ConsentRequired || state == ContactPhotoReadiness.NotificationAccessRequired) {
            TextButton(onClick = onSetup, modifier = Modifier.testTag("contactPhotoSetup")) {
                Text(stringResource(R.string.contact_photos_whatsapp_setup))
            }
        }
    }
}
