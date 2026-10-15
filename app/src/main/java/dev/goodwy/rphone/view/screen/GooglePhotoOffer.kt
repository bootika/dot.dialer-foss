package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import android.content.Context
import androidx.core.content.edit
import android.provider.ContactsContract
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramcosta.composedestinations.generated.destinations.ContactPhotosScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.controller.util.GoogleContactPhotoPreferences
import dev.goodwy.rphone.controller.util.GoogleContactsAuthorization
import dev.goodwy.rphone.controller.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

internal object PhotoOfferPolicy {
    const val WAIT_MILLIS = 48 * 60 * 60 * 1000L
    const val SESSION_GAP_MILLIS = 30 * 60 * 1000L
    fun eligible(now: Long, firstUse: Long, sessions: Int, seen: Boolean): Boolean =
        !seen && firstUse > 0 && now - firstUse >= WAIT_MILLIS && sessions >= 3
}

/** Local eligibility only. No Google authorization or network request is made here. */
@Composable
internal fun GooglePhotoOffer(navigator: DestinationsNavigator, blocked: Boolean) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val google = koinInject<GoogleContactsAuthorization>()
    val calls: CallViewModel = koinActivityViewModel()
    val allCalls by calls.allCalls.collectAsStateWithLifecycle()
    val settingsVersion by prefs.settingsChanged.collectAsStateWithLifecycle()
    val usage = remember(context) { context.getSharedPreferences("photo_offer_usage", Context.MODE_PRIVATE) }
    var resumed by remember { mutableStateOf(false) }
    var entry by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    LifecycleResumeEffect(context) {
        resumed = true
        val now = System.currentTimeMillis()
        val last = usage.getLong("last_session", 0)
        if (last == 0L || now - last >= PhotoOfferPolicy.SESSION_GAP_MILLIS) {
            usage.edit {
                if (usage.getLong("first_use", 0) == 0L) putLong("first_use", now)
                putLong("last_session", now)
                putInt("sessions", usage.getInt("sessions", 0) + 1)
            }
        }
        entry++
        onPauseOrDispose { resumed = false; visible = false }
    }
    val allowed = resumed && !blocked && allCalls.isEmpty() && google.isAvailable &&
        !prefs.getBoolean(GoogleContactPhotoPreferences.ENABLED, false)
    LaunchedEffect(entry, allowed, settingsVersion) {
        if (!allowed) { visible = false; return@LaunchedEffect }
        val eligible = PhotoOfferPolicy.eligible(System.currentTimeMillis(),
            usage.getLong("first_use", 0), usage.getInt("sessions", 0), usage.getBoolean("seen", false))
        if (!eligible) return@LaunchedEffect
        val hasPhotos = withContext(Dispatchers.IO) { hasLocalGooglePhoto(context) }
        if (hasPhotos && calls.allCalls.value.isEmpty()) {
            // An offer is shown once, including when it is dismissed or interrupted by a call.
            val saved = withContext(Dispatchers.IO) { markPhotoOfferSeen(usage) }
            visible = saved
        }
    }
    if (visible && allowed) {
        AlertDialog(
            onDismissRequest = { visible = false },
            title = { Text(stringResource(R.string.photo_offer_title)) },
            text = { Text(stringResource(R.string.photo_offer_body)) },
            confirmButton = { TextButton(onClick = {
                visible = false
                navigator.navigate(ContactPhotosScreenDestination())
            }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.photo_offer_options)) } },
            dismissButton = { TextButton(onClick = { visible = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

internal fun hasLocalGooglePhoto(context: Context): Boolean = try {
    context.contentResolver.query(ContactsContract.Data.CONTENT_URI,
        arrayOf(ContactsContract.Data._ID),
        "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND " +
            "(${ContactsContract.CommonDataKinds.Photo.PHOTO} IS NOT NULL OR ${ContactsContract.CommonDataKinds.Photo.PHOTO_FILE_ID} IS NOT NULL)",
        arrayOf("com.google", ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE), null,
    )?.use { it.moveToFirst() } ?: false
} catch (error: SecurityException) {
    android.util.Log.w("PhotoOffer", "Contacts permission is unavailable", error)
    false
} catch (error: RuntimeException) {
    android.util.Log.w("PhotoOffer", "Local photo eligibility is unavailable", error)
    false
}

// Framework commit result is needed: a failed write must not display an unremembered offer.
@android.annotation.SuppressLint("UseKtx")
private fun markPhotoOfferSeen(usage: android.content.SharedPreferences): Boolean =
    usage.edit().putBoolean("seen", true).commit()
