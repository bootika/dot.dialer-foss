package dev.goodwy.rphone.controller.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.net.toUri
import java.util.Locale

data class SocialAppInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val iconDrawable: Drawable? = null,
    val assetIcon: String? = null,
    val action: (Context, String) -> Unit
)

object SocialUtils {

    private const val LOG_TAG = "SocialUtils"

    private fun launchIntent(
        context: Context,
        preferredIntent: Intent,
        fallbackIntent: Intent? = null,
        destination: String,
    ) {
        try {
            context.startActivity(preferredIntent)
        } catch (preferredError: Exception) {
            Log.w(LOG_TAG, "Unable to open $destination with the preferred app", preferredError)
            if (fallbackIntent != null) {
                try {
                    context.startActivity(fallbackIntent)
                } catch (fallbackError: Exception) {
                    Log.w(LOG_TAG, "Unable to open the $destination fallback", fallbackError)
                }
            }
        }
    }

    private fun getApplicationInfo(context: Context, packageName: String): ApplicationInfo? =
        try {
            context.packageManager.getApplicationInfo(packageName, 0)
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to load metadata for $packageName", error)
            null
        }

    /**
     * Formats a raw phone number to international E.164 format (+15551234567).
     * If formatting fails, cleans formatting symbols and preserves leading '+'.
     */
    fun formatInternationalNumber(context: Context, rawNumber: String): String {
        val trimmed = rawNumber.trim()
        if (trimmed.isEmpty()) return ""

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val countryIso = tm?.simCountryIso?.uppercase()?.ifBlank { null }
            ?: tm?.networkCountryIso?.uppercase()?.ifBlank { null }
            ?: Locale.getDefault().country.uppercase().ifBlank { "US" }

        val e164 = try {
            PhoneNumberUtils.formatNumberToE164(trimmed, countryIso)
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to format a phone number as E.164", error)
            null
        }

        if (!e164.isNullOrBlank()) {
            return e164
        }

        // If it starts with '+', keep '+' and strip any spaces/dashes/parentheses
        return if (trimmed.startsWith("+")) {
            "+" + trimmed.drop(1).replace(Regex("[^0-9]"), "")
        } else {
            trimmed.replace(Regex("[^0-9]"), "")
        }
    }

    /**
     * Extracts only digits without '+' or punctuation for APIs like WhatsApp / Viber.
     */
    fun cleanDigitsOnly(context: Context, rawNumber: String): String {
        val intl = formatInternationalNumber(context, rawNumber)
        return intl.replace(Regex("[^0-9]"), "")
    }

    fun openWhatsApp(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) return
        val url = "https://api.whatsapp.com/send?phone=$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            when {
                context.isPackageInstalled("com.whatsapp") -> setPackage("com.whatsapp")
                context.isPackageInstalled("com.whatsapp.w4b") -> setPackage("com.whatsapp.w4b")
            }
        }
        launchIntent(
            context = context,
            preferredIntent = intent,
            fallbackIntent = Intent(intent).setPackage(null),
            destination = "WhatsApp",
        )
    }

    fun openTelegram(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) return
        val url = "https://t.me/+$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            when {
                context.isPackageInstalled("org.telegram.messenger") -> setPackage("org.telegram.messenger")
                context.isPackageInstalled("org.thunderdog.challegram") -> setPackage("org.thunderdog.challegram")
                context.isPackageInstalled("org.telegram.plus") -> setPackage("org.telegram.plus")
            }
        }
        launchIntent(
            context = context,
            preferredIntent = intent,
            fallbackIntent = Intent(intent).setPackage(null),
            destination = "Telegram",
        )
    }

    fun openSignal(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) return
        val url = "sgnl://signal.me/#p/+$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.isPackageInstalled("org.thoughtcrime.securesms")) {
                setPackage("org.thoughtcrime.securesms")
            }
        }
        val fallbackIntent = Intent(
            Intent.ACTION_VIEW,
            "https://signal.me/#p/+$digits".toUri(),
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        launchIntent(context, intent, fallbackIntent, "Signal")
    }

    fun openViber(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) return
        val url = "viber://chat?number=%2B$digits"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.isPackageInstalled("com.viber.voip")) {
                setPackage("com.viber.voip")
            }
        }
        launchIntent(
            context = context,
            preferredIntent = intent,
            fallbackIntent = Intent(intent).setPackage(null),
            destination = "Viber",
        )
    }

    fun openSkype(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        if (digits.isBlank()) return
        val url = "skype:+$digits?chat"
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.isPackageInstalled("com.skype.raider")) {
                setPackage("com.skype.raider")
            }
        }
        launchIntent(
            context = context,
            preferredIntent = intent,
            fallbackIntent = Intent(intent).setPackage(null),
            destination = "Skype",
        )
    }

    fun openWeChat(context: Context, number: String) {
        val appIntent = context.packageManager.getLaunchIntentForPackage("com.tencent.mm")
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            launchIntent(context, appIntent, destination = "WeChat")
        }
    }

    fun openLine(context: Context, number: String) {
        val appIntent = context.packageManager.getLaunchIntentForPackage("jp.naver.line.android")
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            launchIntent(context, appIntent, destination = "LINE")
        }
    }

    fun openMeet(context: Context, number: String) {
        val appIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.tachyon")
        if (appIntent != null) {
            appIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            launchIntent(context, appIntent, destination = "Google Meet")
        }
    }

    fun openTruecaller(context: Context, number: String) {
        val digits = cleanDigitsOnly(context, number)
        val intent = Intent(Intent.ACTION_VIEW, "truecaller://search?q=$digits".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            if (context.isPackageInstalled("com.truecaller")) {
                setPackage("com.truecaller")
            }
        }
        val fallbackIntent = context.packageManager
            .getLaunchIntentForPackage("com.truecaller")
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        launchIntent(context, intent, fallbackIntent, "Truecaller")
    }

    fun openSms(context: Context, number: String) {
        val intl = formatInternationalNumber(context, number)
        val intent = Intent(Intent.ACTION_SENDTO, "smsto:$intl".toUri()).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        launchIntent(context, intent, destination = "SMS")
    }

    /**
     * Queries and returns ONLY the supported social/messaging apps that are
     * actually installed on the user's device.
     */
    fun getInstalledSocialApps(context: Context): List<SocialAppInfo> {
        val pm = context.packageManager
        val list = mutableListOf<SocialAppInfo>()

        // 1. WhatsApp or WhatsApp Business
        val waPkg = when {
            context.isPackageInstalled("com.whatsapp") -> "com.whatsapp"
            context.isPackageInstalled("com.whatsapp.w4b") -> "com.whatsapp.w4b"
            else -> null
        }
        if (waPkg != null) {
            val appInfo = getApplicationInfo(context, waPkg)
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "WhatsApp"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "whatsapp",
                    name = label,
                    packageName = waPkg,
                    iconDrawable = icon,
                    assetIcon = "file:///android_asset/icons/whatsapp.png",
                    action = { ctx, num -> openWhatsApp(ctx, num) }
                )
            )
        }

        // 2. Telegram or Telegram X or Plus Messenger
        val tgPkg = when {
            context.isPackageInstalled("org.telegram.messenger") -> "org.telegram.messenger"
            context.isPackageInstalled("org.thunderdog.challegram") -> "org.thunderdog.challegram"
            context.isPackageInstalled("org.telegram.plus") -> "org.telegram.plus"
            else -> null
        }
        if (tgPkg != null) {
            val appInfo = getApplicationInfo(context, tgPkg)
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Telegram"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "telegram",
                    name = label,
                    packageName = tgPkg,
                    iconDrawable = icon,
                    assetIcon = "file:///android_asset/icons/telegram.png",
                    action = { ctx, num -> openTelegram(ctx, num) }
                )
            )
        }

        // 3. Signal
        if (context.isPackageInstalled("org.thoughtcrime.securesms")) {
            val appInfo = getApplicationInfo(context, "org.thoughtcrime.securesms")
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Signal"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "signal",
                    name = label,
                    packageName = "org.thoughtcrime.securesms",
                    iconDrawable = icon,
                    assetIcon = "file:///android_asset/icons/signal.png",
                    action = { ctx, num -> openSignal(ctx, num) }
                )
            )
        }

        // 4. Viber
        if (context.isPackageInstalled("com.viber.voip")) {
            val appInfo = getApplicationInfo(context, "com.viber.voip")
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Viber"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "viber",
                    name = label,
                    packageName = "com.viber.voip",
                    iconDrawable = icon,
                    action = { ctx, num -> openViber(ctx, num) }
                )
            )
        }

        // 5. Skype
        if (context.isPackageInstalled("com.skype.raider")) {
            val appInfo = getApplicationInfo(context, "com.skype.raider")
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Skype"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "skype",
                    name = label,
                    packageName = "com.skype.raider",
                    iconDrawable = icon,
                    action = { ctx, num -> openSkype(ctx, num) }
                )
            )
        }

        // 6. WeChat
        if (context.isPackageInstalled("com.tencent.mm")) {
            val appInfo = getApplicationInfo(context, "com.tencent.mm")
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "WeChat"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "wechat",
                    name = label,
                    packageName = "com.tencent.mm",
                    iconDrawable = icon,
                    action = { ctx, num -> openWeChat(ctx, num) }
                )
            )
        }

        // 7. LINE
        if (context.isPackageInstalled("jp.naver.line.android")) {
            val appInfo = getApplicationInfo(context, "jp.naver.line.android")
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "LINE"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "line",
                    name = label,
                    packageName = "jp.naver.line.android",
                    iconDrawable = icon,
                    action = { ctx, num -> openLine(ctx, num) }
                )
            )
        }

        // 8. Meet
        if (context.isPackageInstalled("com.google.android.apps.tachyon")) {
            val appInfo = getApplicationInfo(context, "com.google.android.apps.tachyon")
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Meet"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "meet",
                    name = label,
                    packageName = "com.google.android.apps.tachyon",
                    iconDrawable = icon,
                    action = { ctx, num -> openMeet(ctx, num) }
                )
            )
        }

        // 9. Truecaller
        if (context.isPackageInstalled("com.truecaller")) {
            val appInfo = getApplicationInfo(context, "com.truecaller")
            val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: "Truecaller"
            val icon = appInfo?.let { pm.getApplicationIcon(it) }
            list.add(
                SocialAppInfo(
                    id = "truecaller",
                    name = label,
                    packageName = "com.truecaller",
                    iconDrawable = icon,
                    action = { ctx, num -> openTruecaller(ctx, num) }
                )
            )
        }

        return list
    }

    fun Context.launchSendWhatsAppIntent(phoneNumber: String) {
        val digits = phoneNumber.filter { it.isDigit() }
        if (digits.isEmpty()) {
            toast("No valid app found")
            return
        }
        val pkg = messengerPackages["WhatsApp"]?.let { getInstalledMessenger(it) }
        val intent = Intent(Intent.ACTION_VIEW, "https://wa.me/$digits".toUri())
        if (pkg != null) intent.setPackage(pkg)
        try {
            startActivity(intent)
        } catch (error: android.content.ActivityNotFoundException) {
            Log.w(LOG_TAG, "Unable to open WhatsApp", error)
            toast("No valid app found")
        }
    }

    fun Context.getInstalledMessenger(packageNames: List<String>): String? {
        return packageNames.firstOrNull { isPackageInstalled(it) }
    }

    val messengerPackages = mapOf(
        "WhatsApp" to listOf("com.whatsapp", "com.whatsapp.w4b"),
        "Telegram" to listOf(
            "org.telegram.messenger",
            "org.telegram.plus",
            "org.thunderdog.challegram",
            "com.ghost.telegram",
            "nekox.messenger",
            "it.feed.android"
        ),
        "Signal" to listOf("org.thoughtcrime.securesms"),
        "Viber" to listOf("com.viber.voip"),
        "WeChat" to listOf("com.tencent.mm"),
        "Line" to listOf("jp.naver.line.android"),
        "Teams" to listOf("com.microsoft.teams"),
        "Discord" to listOf("com.discord"),
        "Slack" to listOf("com.slack"),
        "Messenger" to listOf("com.facebook.orca"),
        "Snapchat" to listOf("com.snapchat.android"),
        "KakaoTalk" to listOf("com.kakao.talk"),
        "Threema" to listOf("ch.threema.app"),
        "Wire" to listOf("com.wire"),
        "Tox" to listOf("im.tox.tox"),
        "Element" to listOf("im.vector.app")
    )
}
