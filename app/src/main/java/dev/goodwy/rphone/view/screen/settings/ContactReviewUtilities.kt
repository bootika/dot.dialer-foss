package dev.goodwy.rphone.view.screen.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.R
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun ContactReviewSourceChoice(source: String?,sources: List<String>,enabled: Boolean,choose: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val density=LocalDensity.current
    if(source!=null) Box(Modifier.fillMaxWidth()) {
        if(sources.size<=1) Text(stringResource(R.string.review_source_title,reviewSourceLabel(source)),
            modifier=Modifier.testTag("contactReviewSource"),style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        else OutlinedButton(enabled=enabled,onClick={open=true},modifier=Modifier.fillMaxWidth().testTag("contactReviewSource")) {
            Text(stringResource(R.string.review_source_title,reviewSourceLabel(source)),Modifier.weight(1f))
            Icon(Icons.Rounded.ArrowDropDown,null,Modifier.size(20.dp))
        }
        DropdownMenu(expanded=open,onDismissRequest={open=false}) { CompositionLocalProvider(LocalDensity provides density) {
            for(value in sources) DropdownMenuItem(text={Text(reviewSourceLabel(value), fontWeight=FontWeight.Medium)},onClick={choose(value);open=false},
                modifier=Modifier.testTag("contactReviewSource-$value"))
        } }
    }
}
@Composable
private fun reviewSourceLabel(source: String): String = stringResource(when(source) {
    "numbers" -> R.string.contact_photos_find_numbers
    "whatsapp" -> R.string.whatsapp_avatar_sync_title
    "gravatar" -> R.string.contact_photos_gravatar
    "business" -> R.string.contact_photos_business
    else -> R.string.contact_photos_google_find_title
})
