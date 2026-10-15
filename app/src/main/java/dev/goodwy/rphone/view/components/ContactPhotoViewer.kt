package dev.goodwy.rphone.view.components

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.util.contactPhotoMime
import dev.goodwy.rphone.controller.util.contactPhotoShareIntent
import dev.goodwy.rphone.controller.util.copyContactPhoto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ContactPhotoViewer(photoUri: String, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uri = remember(photoUri) { photoUri.toUri() }
    val mime = remember(uri) { contactPhotoMime(context, uri) }
    val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
    var loaded by remember(photoUri) { mutableStateOf(false) }
    var failed by remember(photoUri) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorText = stringResource(R.string.contact_photo_error)
    val savedText = stringResource(R.string.contact_photo_saved)
    val shareText = stringResource(R.string.share)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mime)) { target ->
        if (target != null) {
            busy = true
            scope.launch {
                var feedback = savedText
                try {
                    copyContactPhoto(context, uri, target)
                } catch (error: CancellationException) { throw error
                } catch (error: Exception) { feedback = errorText
                } finally { busy = false }
                snackbar.showSnackbar(feedback)
            }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ContactPhotoViewerContent(title, loaded && !busy, busy, { if (!busy) onDismiss() },
            onShare = {
                busy = true
                scope.launch {
                    var failed = false
                    try {
                        context.startActivity(Intent.createChooser(contactPhotoShareIntent(context, uri), shareText))
                    } catch (error: CancellationException) { throw error
                    } catch (error: Exception) { failed = true
                    } finally { busy = false }
                    if (failed) snackbar.showSnackbar(errorText)
                }
            },
            onSave = { save.launch("Contact photo.$extension") },
            snackbar = { SnackbarHost(snackbar) },
            photo = {
                AsyncImage(model = rememberWhiteContactPhotoRequest(uri), contentDescription = title,
                    contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().testTag("contactPhotoImage"),
                    onSuccess = { loaded = true; failed = false }, onError = { failed = true; loaded = false })
                if (failed) Text(errorText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (!loaded) CircularProgressIndicator()
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContactPhotoViewerContent(
    title: String, enabled: Boolean, busy: Boolean, onClose: () -> Unit,
    onShare: () -> Unit, onSave: () -> Unit,
    snackbar: @Composable () -> Unit = {},
    photo: @Composable BoxScope.() -> Unit,
) {
    Surface(Modifier.fillMaxSize().testTag("contactPhotoViewer"), color = MaterialTheme.colorScheme.surface) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClose, enabled = !busy) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.close))
                } })
        }, snackbarHost = snackbar) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding().padding(16.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(MaterialTheme.shapes.extraLarge),
                    contentAlignment = Alignment.Center, content = photo)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onShare, enabled = enabled, colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f).testTag("contactPhotoShare")) {
                        Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.share), maxLines = 1)
                    }
                    FilledTonalButton(onSave, enabled = enabled, colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.weight(1f).testTag("contactPhotoSave")) {
                        Icon(Icons.Rounded.SaveAlt, null); Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.save), maxLines = 1)
                    }
                }
            }
        }
    }
}
