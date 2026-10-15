package dev.goodwy.rphone.controller.util

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.text.Html
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.launchCallActivitySafely
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticComponent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticEvent
import io.github.bootika.dotdialer.core.diagnostics.DiagnosticOperation
import io.github.bootika.dotdialer.diagnostics.AppDiagnostics

private const val UTILITIES_TAG = "DialerUtilities"

fun makeCall(
    context: Context,
    number: String,
    accountHandle: PhoneAccountHandle? = null,
    contactId: String? = null,
    launchCallUi: Boolean = true,
) {
    if (!dev.goodwy.rphone.controller.license.TestAccessManager.get(context).allowed) {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        // The legacy local classifier does not require a READ_PHONE_STATE permission grant.
        @Suppress("DEPRECATION")
        val emergency = android.telephony.PhoneNumberUtils.isEmergencyNumber(number)
        if (emergency) {
            val uri = Uri.fromParts("tel", number, null)
            fun openSystemDialer() {
                val systemPackage = telecom.systemDialerPackage
                val intent = if (systemPackage.isNullOrBlank()) Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                    else Intent(Intent.ACTION_DIAL, uri).setPackage(systemPackage)
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                try { telecom.placeCall(uri, Bundle()) }
                catch (error: SecurityException) {
                    android.util.Log.w("CallRouting", "Emergency routing: ${error.javaClass.simpleName}")
                    openSystemDialer()
                }
            } else openSystemDialer()
        } else context.startActivity(Intent(context, dev.goodwy.rphone.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    }
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    val extras = Bundle()

    val prefs = PreferenceManager(context)
    if (contactId != null) {
        prefs.setLastUsedNumber(contactId, number)
    }

    var preferredHandle = accountHandle
    if (preferredHandle == null) {
        val accountInfos = getCallingAccounts(context)
        val accounts = accountInfos.map { it.handle }

        val favSim = contactId?.let { prefs.getFavoriteSim(it) }
            ?: prefs.getFavoriteSimForNumber(number)
        val favNum = contactId?.let { prefs.getFavoriteNumber(it) }

        preferredHandle = if (favSim != null && (favNum == null || areNumbersEqual(number, favNum))) {
            resolveCallingAccount(context, favSim, accountInfos)
        } else null

        if (preferredHandle == null) {
            preferredHandle = resolveCallingAccount(
                context,
                prefs.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null),
                accountInfos,
            )
            // One-time compatibility with builds that persisted SIM 1/SIM 2 as a list index.
            if (preferredHandle == null) {
                val defaultSim = prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, 0)
                if (defaultSim > 0 && accounts.size >= defaultSim) {
                    preferredHandle = accounts[defaultSim - 1]
                    prefs.setString(
                        PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT,
                        accountInfos[defaultSim - 1].key,
                    )
                }
            }
        }
    }

    if (preferredHandle != null) {
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, preferredHandle)
    }

    val outgoingNumber = normalizeOutgoingNumber(context, number, preferredHandle, prefs)
    val uri = if (outgoingNumber.startsWith("voicemail:")) {
        outgoingNumber.toUri()
    } else {
        Uri.fromParts("tel", outgoingNumber, null)
    }

    fun openDotDialpad() {
        context.startActivity(Intent(context, dev.goodwy.rphone.MainActivity::class.java).apply {
            action = Intent.ACTION_DIAL
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
        val renderCallUi = outgoingCallUiAllowed(number, launchCallUi)
        val transfer = if (renderCallUi) dev.goodwy.rphone.controller.CallUiHandoff.controller.beginOutgoing() else null
        try {
            // Telecom owns cellular, IMS/VoWiFi and platform emergency routing.
            telecomManager.placeCall(uri, extras)
            if (renderCallUi) launchOutgoingCallUiFromForeground(context)
        } catch (error: Exception) {
            transfer?.let(dev.goodwy.rphone.controller.CallUiHandoff.controller::cancel)
            android.util.Log.w("CallRouting", "Telecom could not place the call", error)
            openDotDialpad()
        }
    } else {
        openDotDialpad()
    }
}

private fun launchOutgoingCallUiFromForeground(context: Context) {
    val activity = context.findActivity()
    if (activity == null) {
        AppDiagnostics.record(
            DiagnosticEvent.Failure(
                component = DiagnosticComponent.MAIN_ACTIVITY,
                operation = DiagnosticOperation.START_ACTIVITY,
                errorType = "NoActivityContext",
            )
        )
        return
    }
    activity.launchCallActivitySafely(outgoing = true)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Places a call respecting the user's default SIM preference.
 * simPref: 0 = ask, 1 = SIM1 (index 0), 2 = SIM2 (index 1)
 * Returns true if a direct call was placed, false if sim picker should be shown.
 */
fun placeCallWithSimPreference(
    context: Context,
    number: String,
    simPref: Int,
    onShowSimPicker: () -> Unit
): Boolean {
    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    val hasPhoneState = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    return if (hasPhoneState) {
        val accounts = telecomManager.callCapablePhoneAccounts
        if (accounts.size > 1) {
            when (simPref) {
                1 -> {
                    makeCall(context, number, accounts[0])
                    true
                }
                2 -> {
                    makeCall(context, number, accounts[1])
                    true
                }
                else -> {
                    onShowSimPicker()
                    false
                }
            }
        } else {
            makeCall(context, number)
            true
        }
    } else {
        makeCall(context, number)
        true
    }
}

fun openInContacts(context: Context, contactId: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId)
    }
    context.startActivity(intent)
}

fun openLink(context: Context, link: String) {
    try {
        val uri = link.toUri()
        val intent = if (uri.scheme == "tel") {
            Intent(Intent.ACTION_DIAL, uri)
        } else {
            Intent(Intent.ACTION_VIEW, uri)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(UTILITIES_TAG, "Unable to open external link", e)
    }
}

fun getAppVersion(context: Context): Pair<String, Long> {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }

        val versionName = packageInfo.versionName ?: "Unknown"
        // PackageInfoCompat handles retrieving long version codes safely across old/new API levels
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

        Pair(versionName, versionCode)
    } catch (e: PackageManager.NameNotFoundException) {
        Log.e(UTILITIES_TAG, "Unable to read the application package", e)
        Pair("Unknown", -1L)
    }
}

fun isCustomPermissionDevice(): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()
    return manufacturer.contains("xiaomi") ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("vivo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("huawei") ||
            manufacturer.contains("meizu") ||
            manufacturer.contains("oneplus")
}


// Goodwy
fun String.isLetter(): Boolean {
    return this.length == 1 && this[0].isLetter()
}

fun String.isEmoji(): Boolean {
    return matches(
        ("(?:[\uD83C\uDF00-\uD83D\uDDFF]|[\uD83E\uDD00-\uD83E\uDDFF]|" +
                "[\uD83D\uDE00-\uD83D\uDE4F]|[\uD83D\uDE80-\uD83D\uDEFF]|" +
                "[\u2600-\u26FF]\uFE0F?|[\u2700-\u27BF]\uFE0F?|\u24C2\uFE0F?|" +
                "[\uD83C\uDDE6-\uD83C\uDDFF]{1,2}|" +
                "[\uD83C\uDD70\uD83C\uDD71\uD83C\uDD7E\uD83C\uDD7F\uD83C\uDD8E\uD83C\uDD91-\uD83C\uDD9A]\uFE0F?|" +
                "[\u0023\u002A\u0030-\u0039]\uFE0F?\u20E3|[\u2194-\u2199\u21A9-\u21AA]\uFE0F?|[\u2B05-\u2B07\u2B1B\u2B1C\u2B50\u2B55]\uFE0F?|" +
                "[\u2934\u2935]\uFE0F?|[\u3030\u303D]\uFE0F?|[\u3297\u3299]\uFE0F?|" +
                "[\uD83C\uDE01\uD83C\uDE02\uD83C\uDE1A\uD83C\uDE2F\uD83C\uDE32-\uD83C\uDE3A\uD83C\uDE50\uD83C\uDE51]\uFE0F?|" +
                "[\u203C\u2049]\uFE0F?|[\u25AA\u25AB\u25B6\u25C0\u25FB-\u25FE]\uFE0F?|" +
                "[\u00A9\u00AE]\uFE0F?|[\u2122\u2139]\uFE0F?|\uD83C\uDC04\uFE0F?|\uD83C\uDCCF\uFE0F?|" +
                "[\u231A\u231B\u2328\u23CF\u23E9-\u23F3\u23F8-\u23FA]\uFE0F?)+").toRegex()
    )
}

fun getPhoneTypeText(context: Context, type: Int?, label: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> context.resources.getString(R.string.home)
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> context.resources.getString(R.string.mobile)
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> context.resources.getString(R.string.main_number)
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> context.resources.getString(R.string.work)
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> context.resources.getString(R.string.work_fax)
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> context.resources.getString(R.string.home_fax)
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> context.resources.getString(R.string.pager)
        else -> context.resources.getString(R.string.other)
    }
}

fun getEventTypeText(context: Context, type: Int, label: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Event.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> context.resources.getString(R.string.anniversary)
        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> context.resources.getString(R.string.birthday)
        else -> context.resources.getString(R.string.other)
    }
}

fun getAddressTypeText(context: Context, type: Int, label: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> context.resources.getString(R.string.home)
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> context.resources.getString(R.string.work)
        else -> context.resources.getString(R.string.other)
    }
}

fun getEmailTypeText(context: Context, type: Int, label: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> label ?: context.resources.getString(R.string.no_label)
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> context.resources.getString(R.string.home)
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> context.resources.getString(R.string.work)
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> context.resources.getString(R.string.mobile)
        else -> context.resources.getString(R.string.other)
    }
}

fun Context.toast(msg: String, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            doToast(this, msg, length)
        } else {
            Handler(Looper.getMainLooper()).post {
                doToast(this, msg, length)
            }
        }
    } catch (error: Exception) {
        Log.w(UTILITIES_TAG, "Unable to show toast", error)
    }
}

fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()

private fun doToast(context: Context, message: String, length: Int) {
    if (context is Activity) {
        if (!context.isFinishing && !context.isDestroyed) {
            Toast.makeText(context, message, length).show()
        }
    } else {
        Toast.makeText(context, message, length).show()
    }
}

fun Context.copyToClipboard(text: String) {
    val clip = ClipData.newPlainText("Phone", text)
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}

fun Context.getTextFromClipboard(): CharSequence? {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = clipboard.primaryClip
    return if (clip != null && clip.itemCount > 0) {
        clip.getItemAt(0).coerceToText(this)
    } else null
}

fun Color.darken(amount: Float = 0.2f): Color {
    return Color(
        red = this.red * (1 - amount),
        green = this.green * (1 - amount),
        blue = this.blue * (1 - amount),
        alpha = this.alpha
    )
}

fun Context.getBlockedNumbers(): ArrayList<String> {
    val blockedNumbers = ArrayList<String>()
    if (!isAlreadyDefaultDialer(this)) {
        return blockedNumbers
    }

    val uri = BlockedNumbers.CONTENT_URI
    val projection = arrayOf(
        BlockedNumbers.COLUMN_ID,
        BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
        BlockedNumbers.COLUMN_E164_NUMBER
    )

    queryCursor(uri, projection) { cursor ->
//        val id = cursor.getLongValue(BlockedNumbers.COLUMN_ID)
        val number = cursor.getStringValue(BlockedNumbers.COLUMN_ORIGINAL_NUMBER) ?: ""
        val normalizedNumber = cursor.getStringValue(BlockedNumbers.COLUMN_E164_NUMBER) ?: number
        val comparableNumber = normalizedNumber.trimToComparableNumber()
        blockedNumbers.add(comparableNumber)
    }

    return blockedNumbers
}

fun Context.queryCursor(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    showErrors: Boolean = false,
    callback: (cursor: Cursor) -> Unit
) {
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    callback(cursor)
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        if (showErrors) {
            toast(e.toString())
        }
    }
}

fun Cursor.getLongValue(key: String) = getLong(getColumnIndexOrThrow(key))

fun Cursor.getStringValue(key: String) = getString(getColumnIndexOrThrow(key))

fun Context.launchInternetSearch(query: String) {
    Intent(Intent.ACTION_WEB_SEARCH).apply {
        putExtra(SearchManager.QUERY, query)
        try {
            startActivity(this)
        } catch (_: ActivityNotFoundException) {
            toast("Unable to open search")
        } catch (e: Exception) {
            toast(e.toString())
        }
    }
}

@Composable
fun HtmlTextView(
    html: String,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                @Suppress("DEPRECATION")
                text = Html.fromHtml(html)
                gravity = Gravity.CENTER
                textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                setTextColor(textColor)
            }
        },
        modifier = modifier,
        update = { view ->
            @Suppress("DEPRECATION")
            view.text = Html.fromHtml(html)
            view.gravity = Gravity.CENTER
            view.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
            view.setTextColor(textColor)
        }
    )
}

fun String.forceLtr(): String = "\u200E$this"
