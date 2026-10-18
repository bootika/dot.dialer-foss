package dev.goodwy.rphone.controller.util

import android.content.Context
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.goodwy.rphone.R
import io.github.bootika.dotdialer.core.preferences.DotDialerPreferenceDefaults
import io.github.bootika.dotdialer.core.preferences.LegacyPreferenceDefaults
import dev.goodwy.rphone.controller.lock.AuthenticationSecrets
import io.github.bootika.dotdialer.core.preferences.PreferenceBackupPolicy

class PreferenceManager(context: Context, private val authenticationSecrets: AuthenticationSecrets = AuthenticationSecrets(context),
    private val featureAllowed: (io.github.bootika.dotdialer.core.license.PlanFeature) -> Boolean = { true },
    private val onPlanRequired: (io.github.bootika.dotdialer.core.license.PlanFeature) -> Unit = {},
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dataStore = getSharedDataStore(appContext)

    private val _prefsCache = MutableStateFlow<Preferences>(
        // We perform a blocking read when creating an object,
        // so that the cache isn't empty during a cold start in onCreate()
        runBlocking(Dispatchers.IO) { dev.goodwy.rphone.controller.lock.loadAuthenticationPreferences(dataStore, authenticationSecrets) }
    )

    private val _settingsChanged = MutableStateFlow(0)
    val settingsChanged: StateFlow<Int> = _settingsChanged.asStateFlow()
    private val authentication = dev.goodwy.rphone.controller.lock.AuthenticationPreferences(
        dataStore, authenticationSecrets, { _prefsCache.value }, { _prefsCache.value = it })
    init {
        scope.launch {
            dataStore.data.collect { newPrefs ->
                _prefsCache.value = newPrefs
                _settingsChanged.value += 1
            }
        }
    }

    /** Number of currently active SIM subscriptions (0, 1, 2+). Never throws. */
    fun getActiveSimCount(): Int {
        return try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    appContext, android.Manifest.permission.READ_PHONE_STATE
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return 1
            val sm = appContext.getSystemService(android.telephony.SubscriptionManager::class.java)
            val list =
                sm?.activeSubscriptionInfoList
            list?.size ?: 1
        } catch (error: Exception) {
            android.util.Log.w("PreferenceManager", "Unable to determine the active SIM count", error)
            1
        }
    }

    /** "Show SIM badges in call logs" should only default to on for dual-SIM (or more)
     *  devices — on a single-SIM device the badge is pure clutter since every entry
     *  would show the same "SIM 1" chip. */
    fun getShowSimsInCallLogsDefault(): Boolean = getActiveSimCount() >= 2

    /** Default-SIM-for-calling behavior should only default to "always ask" on
     *  dual-SIM (or more) devices. On a single-SIM device there is nothing to ask —
     *  default straight to that one SIM so every call just goes out immediately. */
    fun getDefaultSimAskEveryTimeDefault(): Boolean = getActiveSimCount() >= 2

    /** Default value for the "default_sim" pref (0 = ask every time, 1 = SIM 1, 2 = SIM 2).
     *  On a single-SIM device there's nothing to ask about, so default straight to that
     *  one SIM instead of nagging with an "Ask every time" dialog on every call. */
    fun getDefaultSimIndexDefault(): Int = if (getActiveSimCount() == 1) 1 else 0

    // Synchronous-style API for backward compatibility
    internal fun notifyPlanChanged() { _settingsChanged.value += 1 }
    private val planAccess=PlanPreferenceAccess(featureAllowed,onPlanRequired,::notifyPlanChanged)
    internal fun allowsFeature(feature: io.github.bootika.dotdialer.core.license.PlanFeature) = planAccess.allows(feature)
    internal fun requireFeature(feature: io.github.bootika.dotdialer.core.license.PlanFeature) = planAccess.require(feature)
    private fun permitted(key: String) = planAccess.permitted(key)
    private fun allowWrite(key: String) = planAccess.write(key)
    private val usesOwnerDefaults: Boolean get() =
        (_prefsCache.value.asMap()[intPreferencesKey(DotDialerPreferenceDefaults.INSTALLATION_PROFILE_KEY)] as? Int) ==
            DotDialerPreferenceDefaults.OWNER_PROFILE_REVISION
    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        if (!permitted(key)) false else _prefsCache.value[booleanPreferencesKey(key)]
            ?: if (usesOwnerDefaults) DotDialerPreferenceDefaults.booleanFor(key) ?: defaultValue
                else LegacyPreferenceDefaults.booleanFor(key) ?: defaultValue

    fun setBoolean(key: String, value: Boolean) {
        if (value && !allowWrite(key)) return
        PreferenceWriteBarrier.enqueue { dataStore.edit { if(!value || permitted(key)) it[booleanPreferencesKey(key)] = value } }
    }

    fun getString(key: String, defaultValue: String?): String? =
        if (!permitted(key)) (if (key == KEY_ACCENT_COLOR) "jade" else null) else _prefsCache.value[stringPreferencesKey(key)]
            ?: if (usesOwnerDefaults) DotDialerPreferenceDefaults.stringFor(key) ?: defaultValue
                else LegacyPreferenceDefaults.stringFor(key) ?: defaultValue

    fun setString(key: String, value: String?) {
        if (!value.isNullOrEmpty() && !allowWrite(key)) return
        require(key !in setOf(KEY_BIOMETRICS_PIN, KEY_BIOMETRICS_PASSWORD, KEY_APP_LOCK_PIN) || value.isNullOrEmpty()) {
            "Use secure authentication configuration"
        }
        PreferenceWriteBarrier.enqueue {
            dataStore.edit {
                if(!value.isNullOrEmpty() && !permitted(key)) return@edit
                if (value == null) it.remove(stringPreferencesKey(key))
                else it[stringPreferencesKey(key)] = value
            }
        }
    }

    fun getInt(key: String, defaultValue: Int): Int =
        if (!permitted(key)) when(key) { KEY_SWIPE_RIGHT_ACTION -> DEFAULT_SWIPE_RIGHT_ACTION; KEY_SWIPE_LEFT_ACTION -> DEFAULT_SWIPE_LEFT_ACTION; KEY_CALL_MESSAGE_CHANNEL -> 0; else -> defaultValue } else _prefsCache.value[intPreferencesKey(key)]
            ?: if (usesOwnerDefaults) DotDialerPreferenceDefaults.intFor(key) ?: defaultValue
                else LegacyPreferenceDefaults.intFor(key) ?: defaultValue

    fun setInt(key: String, value: Int) {
        if (!allowWrite(key)) return
        PreferenceWriteBarrier.enqueue { dataStore.edit { if(permitted(key)) it[intPreferencesKey(key)] = value } }
    }

    fun getFloat(key: String, defaultValue: Float): Float =
        _prefsCache.value[floatPreferencesKey(key)]
            ?: if (usesOwnerDefaults) DotDialerPreferenceDefaults.floatFor(key) ?: defaultValue
                else LegacyPreferenceDefaults.floatFor(key) ?: defaultValue

    fun setFloat(key: String, value: Float) {
        PreferenceWriteBarrier.enqueue { dataStore.edit { it[floatPreferencesKey(key)] = value } }
    }

    /** Returns a map of all preferences in the DataStore for backup purposes. */
    fun getAllPreferences(): Map<String, Any> {
        return _prefsCache.value.asMap().mapKeys { it.key.name }.mapValues { it.value }
            .filterKeys { it != DotDialerPreferenceDefaults.INSTALLATION_PROFILE_KEY }
    }

    suspend fun getPersistedPreferences(): Map<String, Any> {
        PreferenceWriteBarrier.awaitPending()
        val persisted = dataStore.data.first()
        _prefsCache.value = persisted
        return persisted.asMap().mapKeys { it.key.name }.mapValues { it.value }
            .filterKeys { it != DotDialerPreferenceDefaults.INSTALLATION_PROFILE_KEY }
    }

    /** Restores a preference snapshot and persists it before reporting success to the caller. */
    suspend fun restoreAllPreferences(preferences: Map<String, Any>, replacePortable: Boolean = false) {
        PreferenceWriteBarrier.awaitPending()
        _prefsCache.value = dataStore.edit { it.applyRestoredPreferences(preferences, replacePortable) }
        _settingsChanged.value += 1
    }

    /** Returns true if an incoming call from [phoneNumber] should be gated behind biometric. */
    fun shouldGateCallWithBiometric(phoneNumber: String?) = authentication.shouldGateCall(phoneNumber) { resolveMergeRegion(appContext) }

    fun setLastUsedNumber(contactId: String, number: String) {
        setString("last_used_number_$contactId", number)
    }

    fun getLastUsedNumber(contactId: String): String? {
        return getString("last_used_number_$contactId", null)
    }

    fun setFavoriteNumber(contactId: String, number: String?) {
        setString("favorite_number_$contactId", number)
    }

    fun getFavoriteNumber(contactId: String): String? {
        return getString("favorite_number_$contactId", null)
    }

    fun setFavoriteSim(contactId: String, simHandle: String?) {
        setString("favorite_sim_$contactId", simHandle)
    }

    fun getFavoriteSim(contactId: String): String? {
        return getString("favorite_sim_$contactId", null)
    }

    fun setFavoriteSimForNumber(number: String, simHandle: String?) {
        val key = number.filter(Char::isDigit).takeLast(12)
        if (key.isNotBlank()) setString("favorite_sim_number_$key", simHandle)
    }

    fun getFavoriteSimForNumber(number: String): String? {
        val key = number.filter(Char::isDigit).takeLast(12)
        return if (key.isBlank()) null else getString("favorite_sim_number_$key", null)
    }

    fun setFavoriteEmail(contactId: String, email: String?) {
        setString("favorite_email_$contactId", email)
    }

    fun getFavoriteEmail(contactId: String): String? {
        return getString("favorite_email_$contactId", null)
    }

    fun getFavoritesOrder(): List<String> {
        val orderStr = getString(KEY_FAVORITES_ORDER, null) ?: return emptyList()
        return orderStr.split(",").filter { it.isNotEmpty() }
    }

    fun setFavoritesOrder(order: List<String>) {
        setString(KEY_FAVORITES_ORDER, order.joinToString(","))
    }

    fun getQuickResponses(): List<String> {
        val stored = getString(KEY_QUICK_RESPONSES, null)
        if (stored.isNullOrBlank()) return defaultQuickResponses()
        val list = stored.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
        return list.ifEmpty { defaultQuickResponses() }
    }

    fun setQuickResponses(responses: List<String>) {
        setString(KEY_QUICK_RESPONSES, responses.filter { it.isNotBlank() }.joinToString("|||"))
    }

    fun defaultQuickResponses(): List<String> {
        (if (usesOwnerDefaults) DotDialerPreferenceDefaults.stringFor(KEY_QUICK_RESPONSES) else null)?.let { preset ->
            return preset.split("|||").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return listOf(
            appContext.getString(R.string.default_quick_responses_1),
            appContext.getString(R.string.default_quick_responses_2),
            appContext.getString(R.string.default_quick_responses_3),
            appContext.getString(R.string.default_quick_responses_4)
        )
    }

    fun contactBackgroundIdKey(contactId: String): String {
        return CONTACT_BACKGROUND_PREFIX + contactId
    }

    fun contactBackgroundNumberKey(numberKey: String): String {
        return CONTACT_BACKGROUND_NUMBER_PREFIX + numberKey
    }

    fun setContactBackground(contactId: String, uri: String?) {
        setString(contactBackgroundIdKey(contactId), uri)
    }

    fun getContactBackground(contactId: String): String? {
        return _prefsCache.value[stringPreferencesKey(contactBackgroundIdKey(contactId))]
    }

    fun setContactBackgroundForNumber(numberKey: String, value: String?) {
        setString(contactBackgroundNumberKey(numberKey), value)
    }

    fun getContactBackgroundForNumber(numberKey: String): String? {
        return try {
            _prefsCache.value[stringPreferencesKey(contactBackgroundNumberKey(numberKey))]
        } catch (e: Exception) {
            null
        }
    }

    fun getContactBackgroundEntries(): Map<String, String> {
        return try {
            _prefsCache.value.asMap()
                .filterKeys { it.name.startsWith(CONTACT_BACKGROUND_PREFIX) }
                .mapNotNull { (key, value) ->
                    val stringValue = value as? String
                    if (stringValue.isNullOrBlank()) null else key.name to stringValue
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun getContactBackgroundNumberEntries(): Map<String, String> {
        return try {
            _prefsCache.value.asMap()
                .filterKeys { it.name.startsWith(CONTACT_BACKGROUND_NUMBER_PREFIX) }
                .mapNotNull { (key, value) ->
                    val stringValue = value as? String
                    if (stringValue.isNullOrBlank()) null else {
                        val numberKey = key.name.removePrefix(CONTACT_BACKGROUND_NUMBER_PREFIX)
                        numberKey to stringValue
                    }
                }
                .toMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun updateContactBackgroundEntries(updates: Map<String, String?>) {
        if (updates.isEmpty()) return
        scope.launch {
            dataStore.edit { prefs ->
                updates.forEach { (key, value) ->
                    if (key.startsWith(CONTACT_BACKGROUND_PREFIX)) {
                        val dataStoreKey = stringPreferencesKey(key)
                        if (value == null) prefs.remove(dataStoreKey)
                        else prefs[dataStoreKey] = value
                    }
                }
            }
        }
    }

    suspend fun updateContactBackgroundEntriesSuspend(updates: Map<String, String?>, requireAppearance: Boolean=false): Boolean {
        if (updates.isEmpty()) return true
        var applied=false
        dataStore.edit { prefs ->
            if(requireAppearance && !allowsFeature(io.github.bootika.dotdialer.core.license.PlanFeature.PREMIUM_APPEARANCE)) return@edit
            updates.forEach { (key, value) ->
                if (key.startsWith(CONTACT_BACKGROUND_PREFIX)) {
                    val dataStoreKey = stringPreferencesKey(key)
                    if (value == null) prefs.remove(dataStoreKey)
                    else prefs[dataStoreKey] = value
                }
            }
            applied=true
        }
        return applied
    }

    fun getVisibleAccounts(): Set<String>? {
        val str = getString(KEY_VISIBLE_ACCOUNTS, null) ?: return null
        return str.split(",").filter { it.isNotEmpty() }.toSet()
    }

    fun setVisibleAccounts(accounts: Set<String>) {
        setString(KEY_VISIBLE_ACCOUNTS, accounts.joinToString(","))
    }

    companion object {
        const val CONTACT_BACKGROUND_PREFIX = "contact_background_"
        const val CONTACT_BACKGROUND_NUMBER_PREFIX = "contact_background_num_"

        const val KEY_DEFAULT_SIM           = "default_sim"
        const val KEY_DEFAULT_CALLING_ACCOUNT = "default_calling_account"
        const val KEY_OUTGOING_NUMBER_NORMALIZATION = "outgoing_number_normalization"
        const val KEY_OUTGOING_NUMBER_REGION_ISO = "outgoing_number_region_iso"
        const val KEY_DYNAMIC_COLORS        = "dynamic_colors"
        const val KEY_AMOLED_MODE           = "amoled_mode"
        const val KEY_SHOW_FIRST_LETTER     = "show_first_letter"
        const val KEY_COLORFUL_AVATARS      = "colorful_avatars"
        const val KEY_GRADIENT_AVATARS      = "gradient_avatars"
        const val KEY_PRIMARY_COLOR_AVATARS = "primary_color"
        const val KEY_SECONDARY_COLOR_AVATARS = "secondary_color"
        const val KEY_GOOGLE_CONTACTS_AVATARS      = "google_contacts_color"
        const val KEY_MODERN_PASTEL_AVATARS        = "modern_pastel_avatars"
        const val KEY_SHOW_PICTURE          = "show_picture"
        const val KEY_ICON_ONLY_NAV         = "icon_only_nav"
        const val KEY_FLIP_BOTTOM_NAV = "flip_bottom_nav"
        const val KEY_DEFAULT_BOTTOM_NAV = "default_bottom_nav"
        const val KEY_DTMF_TONE             = "dtmf_tone"
        const val KEY_DIALPAD_VIBRATION     = "dialpad_vibration"
        const val KEY_SPEED_DIAL            = "speed_dial"
        const val KEY_T9_DIALING            = "t9_dialing"
        const val KEY_PROXIMITY_SENSOR = "proximity_sensor"
        const val KEY_INCOMING_CALL_POPUP = "incoming_call_popup"
        const val KEY_ALWAYS_FULL_SCREEN_CALLS = "always_full_screen_calls"
        const val KEY_ANDROID_AUTO_ENABLED = "android_auto_enabled"
        const val KEY_AUTO_REDIAL_BUSY = "auto_redial_busy"
        const val KEY_REDIAL_ATTEMPTS = "redial_attempts"
        const val KEY_REDIAL_DELAY = "redial_delay"
        const val KEY_BLOCK_METHOD = "block_method"
        const val KEY_BLOCK_LOG_VISIBILITY = "block_log_visibility"
        const val KEY_BLOCK_NOTIFICATION = "block_notification"
        const val KEY_VIBRATE_ON_ANSWER = "vibrate_on_answer"
        const val KEY_VIBRATE_ON_HANGUP = "vibrate_on_hangup"
        const val KEY_ROUND_AVATARS = "round_avatars"
        const val KEY_SHOW_DIVIDERS = "show_dividers"
        const val KEY_TRANSITION_STYLE = "transition_animation_style"
        const val KEY_DIALPAD_STYLE = "dialpad_style"
        const val KEY_VOICEMAIL_NUMBER = "voicemail_number"
        const val KEY_VOICEMAIL_VIBRATION = "voicemail_vibration"
        const val KEY_VOICEMAIL_RINGTONE = "voicemail_ringtone"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_AUTO_ANSWER_DELAY = "auto_answer_delay"
        const val KEY_SHOW_CALL_SUMMARY_TOAST = "show_call_summary_toast"
        const val KEY_VIBRATE_OUTGOING_RINGING = "vibrate_outgoing_ringing"
        const val KEY_FLIP_TO_SILENCE = "flip_to_silence"
        const val KEY_SILENCE_UNKNOWN = "silence_unknown_calls"
        const val KEY_DISPLAY_CARRIER_INFO = "display_carrier_info"
        const val KEY_CALL_DURATION_DISPLAY = "call_duration_display_mode"
        const val KEY_CALL_LOG_GROUPING = "call_log_grouping"
        const val KEY_PERMISSION_SETUP_REVIEWED = "permission_setup_reviewed"
        const val KEY_SHOW_ALL_RECENT_CALLS = "show_all_recent_calls"
        const val KEY_DIALPAD_LAYOUT = "dialpad_layout_style"
        const val KEY_AVATAR_SHAPE = "avatar_shape"
        const val KEY_SWIPE_TO_CALL = "swipe_to_call"
        const val KEY_DIALPAD_VIBRATION_STRENGTH = "dialpad_vibration_strength"
        const val KEY_DTMF_TONE_VOLUME = "dtmf_tone_volume"
        const val KEY_HAPTIC_LIST_SCROLL = "haptic_list_scroll"
        const val KEY_MISSED_CALL_NOTIFICATIONS = "missed_call_notifications"
        const val KEY_SHOW_SIM_ICON_HISTORY = "show_sim_icon_history"
        const val KEY_SEARCH_MATCH_MODE = "search_match_mode"
        const val KEY_SEARCH_DIACRITIC_NORMALIZATION = "search_diacritic_normalization"
        const val KEY_REPEAT_CONTACTS_IN_SEARCH_RESULTS = "repeat_contacts_in_search_results"
        const val KEY_RECENT_HISTORY_PERIOD_DAYS = "recent_history_period_days"
        const val KEY_RECENT_HISTORY_MAX_ENTRIES = "recent_history_max_entries"
        const val KEY_QUICK_RESPONSE_ENABLED = "quick_response_enabled"
        const val KEY_INCOMING_CALL_UI_MODE = "incoming_call_ui_mode"
        const val INCOMING_CALL_UI_HORIZONTAL_SWIPE = 0
        const val INCOMING_CALL_UI_TAP_BUTTONS = 1
        const val INCOMING_CALL_UI_HORIZONTAL_SLIDER = 2
        const val INCOMING_CALL_UI_VERTICAL_SWIPE = 3
        const val INCOMING_CALL_UI_SAMSUNG_RADIAL = 4
        const val INCOMING_CALL_UI_DEFAULT_SWIPE = 10
        const val KEY_INCOMING_ANSWER_PLACEMENT = "incoming_answer_placement"
        const val INCOMING_ANSWER_PLACEMENT_LEFT_TOP = "left_top"
        const val INCOMING_ANSWER_PLACEMENT_RIGHT_BOTTOM = "right_bottom"
        const val DEFAULT_INCOMING_ANSWER_PLACEMENT = INCOMING_ANSWER_PLACEMENT_LEFT_TOP
        const val KEY_SHOW_CARDS = "show_cards"
        const val KEY_SHOW_CALL_SCREEN_AVATAR = "show_call_screen_avatar"
        const val KEY_HIDE_AVATAR_WITH_BACKGROUND = "hide_avatar_with_background"
        const val KEY_CARD_ROUNDNESS = "card_roundness"
        const val KEY_ONBOARDING_SHOWN = "onboarding_shown"
        const val KEY_HOME_UI_TOUR_SHOWN = "home_ui_tour_shown"
        const val KEY_PERMISSION_POPUP_SHOWN = "permission_popup_shown"
        const val KEY_LAST_USED_ACCOUNT_NAME = "last_used_account_name"
        const val KEY_LAST_USED_ACCOUNT_TYPE = "last_used_account_type"
        const val KEY_FAVORITES_ORDER = "favorites_order"
        const val KEY_VISIBLE_ACCOUNTS = "visible_accounts"
        const val KEY_CONTACT_SORT_ORDER = "contact_sort_order"
        const val KEY_CONTACT_DISPLAY_ORDER = "contact_display_order"
        const val KEY_PATREON_PROMPT_SHOWN = "patreon_prompt_shown"
        const val KEY_CALL_RECORDING = "call_recording"
        const val KEY_CALL_RECORDING_AUTO = "call_recording_auto"
        const val KEY_CALL_RECORDING_SHIZUKU = "call_recording_shizuku"
        const val KEY_CALL_RECORDING_FILTER = "call_recording_filter"
        const val RECORD_FILTER_ALL = 0
        const val RECORD_FILTER_INCOMING_ONLY = 1
        const val RECORD_FILTER_OUTGOING_ONLY = 2
        const val RECORD_FILTER_UNKNOWN_ONLY = 3
        const val RECORD_FILTER_CONTACTS_ONLY = 4

        const val KEY_POCKET_MODE = "pocket_mode"
        const val KEY_VOLUME_SQUEEZE_DND = "volume_squeeze_dnd"
        const val KEY_CALL_MESSAGE_CHANNEL = "call_message_channel"
        const val KEY_QUICK_RESPONSES = "custom_quick_responses"
        val DEFAULT_QUICK_RESPONSES = listOf(
            "Can't talk now. What's up?",
            "I'll call you right back.",
            "I'll call you later.",
            "Can't talk now. Call me later?",
            "I'm in a meeting. Will message you soon."
        )
        const val KEY_BOTTOM_NAV_ORDER = "bottom_nav_order"
        const val KEY_BOTTOM_NAV_HIDDEN = "bottom_nav_hidden"
        const val KEY_MERGE_FAVORITES_RECENTS = "merge_favorites_recents"
        const val KEY_RECENTS_FAVORITES_COLLAPSED = "recents_favorites_collapsed"
        const val KEY_SHOW_RECENTS_FILTER_CHIPS = "show_recents_filter_chips"
        const val KEY_SHOW_RECENTS_FAVORITES = "show_recents_favorites"
        const val KEY_IS_SUPPORTER = "is_supporter"
        const val KEY_POST_CALL_SCREEN = "post_call_screen"
        const val KEY_NAV_BAR_STYLE = "nav_bar_style"
        const val NAV_BAR_STYLE_STANDARD = 0
        const val NAV_BAR_STYLE_TOOLBAR = 1

        const val KEY_START_LOCATION = "start_location"
        const val START_LOCATION_NORMAL = 0
        const val START_LOCATION_DIALPAD_RECENTS = 1
        const val START_LOCATION_DIALPAD_CONTACTS = 2

        const val KEY_SECRET_DIALPAD_CODE = "secret_dialpad_code"
        const val DEFAULT_SECRET_DIALPAD_CODE = "*#0000#"
        const val KEY_HIDE_PRIVATE_SETTINGS_ENTRY = "hide_private_settings_entry"
        const val KEY_HIDDEN_CONTACTS_VISIBLE = "hidden_contacts_visible"

        const val KEY_LOG_FAKE_CALLS = "log_fake_calls"
        const val KEY_SHOW_RECENTS_STATS = "show_recents_stats"

        const val KEY_APP_USAGE_SECONDS = "app_usage_seconds"
        const val KEY_RATE_APP_SHOWN = "rate_app_shown"
        const val KEY_RATE_APP_SNOOZED_TIME = "rate_app_snoozed_time"

        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_APP_LOCK_BIOMETRIC = "app_lock_biometric"
        const val KEY_APP_LOCK_PIN = "app_lock_pin"
        const val KEY_APP_LOCK_TIMEOUT = "app_lock_timeout"
        const val APP_LOCK_TIMEOUT_IMMEDIATELY = 0
        const val APP_LOCK_TIMEOUT_1_MIN = 1
        const val APP_LOCK_TIMEOUT_5_MIN = 5
        const val APP_LOCK_TIMEOUT_15_MIN = 15
        const val APP_LOCK_TIMEOUT_30_MIN = 30

        const val KEY_FLOATING_CALL_BUBBLE = "floating_call_bubble"

        const val KEY_FLOATING_BAR_ROUNDNESS = "floating_bar_roundness"
        const val DEFAULT_FLOATING_BAR_ROUNDNESS = 32
        const val KEY_FLOATING_BAR_BLUR = "floating_bar_blur"
        const val KEY_UI_BLUR = "ui_blur"

        const val KEY_SWIPE_ACTIONS_ENABLED = "swipe_actions_enabled"
        const val KEY_SWIPE_RIGHT_ACTION = "swipe_right_action"
        const val KEY_SWIPE_LEFT_ACTION = "swipe_left_action"

        const val SWIPE_ACTION_NONE = 0
        const val SWIPE_ACTION_CALL = 1
        const val SWIPE_ACTION_MESSAGE = 2
        const val SWIPE_ACTION_VIDEO_CALL = 3
        const val SWIPE_ACTION_WHATSAPP = 4
        const val SWIPE_ACTION_COPY_NUMBER = 5
        const val SWIPE_ACTION_DELETE = 6
        const val SWIPE_ACTION_CONTACT = 7
        const val SWIPE_ACTION_DELETE_ENTRY = 8
        const val KEY_RECENT_TAP_ACTION = "recent_tap_action"

        const val DEFAULT_DIRECT_CALL_ON_TAP = true
        const val DEFAULT_OUTGOING_NUMBER_NORMALIZATION = true
        const val DEFAULT_CLEAR_SEARCH_AFTER_CALL = true
        const val DEFAULT_SWIPE_ENABLED = true
        const val DEFAULT_SWIPE_RIGHT_ACTION = SWIPE_ACTION_MESSAGE
        const val DEFAULT_SWIPE_LEFT_ACTION = SWIPE_ACTION_WHATSAPP

        const val TAB_RECENTS = 0
        const val TAB_FAVORITES = 1
        const val TAB_CONTACTS = 2
        const val TAB_RECORDINGS = 3

        val DEFAULT_BOTTOM_NAV_ORDER = listOf(TAB_RECENTS, TAB_CONTACTS, TAB_FAVORITES, TAB_RECORDINGS)

        const val KEY_MISSED_CALL_CARD = "missed_call_card"
        const val KEY_AUTO_DECLINE_UNKNOWN = "auto_decline_unknown"
        const val KEY_AUTO_DECLINE_NON_CONTACTS = "auto_decline_non_contacts"
        const val KEY_DUAL_SIM_DIALPAD_BUTTONS = "dual_sim_dialpad_buttons"

        const val KEY_BLOCK_UNKNOWN         = "block_unknown_callers"
        const val KEY_BLOCK_HIDDEN          = "block_hidden_callers"
        const val KEY_OPEN_DIALPAD_DEFAULT  = "open_dialpad_default"
        const val KEY_APP_HAPTICS              = "app_haptics_enabled"
        const val KEY_APP_HAPTICS_STRENGTH     = "app_haptics_strength"
        const val KEY_HAPTICS_CUSTOM_INTENSITY = "haptics_custom_intensity"
        const val KEY_NOTES_ENABLED         = "notes_enabled"
        const val KEY_CUSTOM_FONT_PATH      = "custom_font_path"
        const val KEY_CUSTOM_FONT_SIZE      = "custom_font_size"
        const val KEY_IGNORE_SYSTEM_FONT_SCALE = "ignore_system_font_scale"
        const val DEFAULT_IGNORE_SYSTEM_FONT_SCALE = DotDialerPreferenceDefaults.DEFAULT_IGNORE_SYSTEM_FONT_SCALE
        const val KEY_THEME_MODE            = "theme_mode"
        const val KEY_ACCENT_COLOR          = "accent_color"
        const val KEY_CONTACT_NAME_SIZE_SP  = "contact_name_size_sp"
        const val KEY_EXPANDED_LIST_DETAILS = "expanded_list_details"
        const val DEFAULT_EXPANDED_LIST_DETAILS = false
        const val KEY_CONTACT_AVATAR_SIZE   = "contact_avatar_size"
        const val CONTACT_AVATAR_SIZE_SMALL = "small"
        const val CONTACT_AVATAR_SIZE_MEDIUM = "medium"
        const val CONTACT_AVATAR_SIZE_LARGE = "large"
        const val DEFAULT_CONTACT_AVATAR_SIZE = CONTACT_AVATAR_SIZE_MEDIUM
        const val KEY_CALL_SCREEN_APPEARANCE = "call_screen_appearance"
        const val CALL_SCREEN_APPEARANCE_CIRCULAR_AVATAR = "circular_avatar"
        const val CALL_SCREEN_APPEARANCE_FULL_PHOTO = "full_photo"
        const val DEFAULT_CALL_SCREEN_APPEARANCE = DotDialerPreferenceDefaults.DEFAULT_CALL_SCREEN_APPEARANCE
        const val KEY_FROSTED_CALL_BACKGROUND = "frosted_call_background"
        const val DEFAULT_FROSTED_CALL_BACKGROUND = DotDialerPreferenceDefaults.DEFAULT_FROSTED_CALL_BACKGROUND
        const val KEY_CALL_AVATAR_SIZE = "call_avatar_size"
        const val CALL_AVATAR_SIZE_SMALL = "small"
        const val CALL_AVATAR_SIZE_MEDIUM = "medium"
        const val CALL_AVATAR_SIZE_LARGE = "large"
        const val DEFAULT_CALL_AVATAR_SIZE = CALL_AVATAR_SIZE_SMALL
        const val DEFAULT_INCOMING_CALL_UI_MODE = INCOMING_CALL_UI_DEFAULT_SWIPE
        const val KEY_INTERFACE_SCALE_PERCENT = "interface_scale_percent"
        const val KEY_HIGH_CONTRAST_TEXT    = "high_contrast_text"
        const val KEY_BLOCKED_CONTACTS      = "blocked_contacts"
        const val KEY_SHOW_INCOMING_CALL_UI = "show_incoming_call_ui"
        const val KEY_SHOW_CALLER_UI        = "show_caller_ui"
        const val KEY_PROXIMITY_BG          = "proximity_sensor_bg"
        const val KEY_SCROLL_HAPTICS        = "scroll_haptics_enabled"
        const val KEY_SCROLL_CM_PER_HAPTIC  = "scroll_cm_per_haptic"   // cm scrolled before each haptic tick
        const val KEY_SCROLL_HAPTICS_PER_CM = "scroll_haptics_per_cm"  // haptic ticks per cm
        const val KEY_SCROLL_HAPTIC_STRENGTH = "scroll_haptic_strength" // vibration amplitude 1–255
        const val KEY_HAPTICS_STRENGTH      = "app_haptics_strength"
        const val KEY_CALL_UI_SHOW_TODAY    = "call_ui_show_today"
        const val KEY_CALL_UI_SHOW_MISSED   = "call_ui_show_missed"
        const val KEY_CALL_UI_SHOW_OUTGOING = "call_ui_show_outgoing"
        const val KEY_CALL_UI_SHOW_CALL_TIME = "call_ui_show_call_time"
        const val KEY_AUTO_UPDATE_CHECK     = "auto_update_check"
        const val KEY_PILL_NAV              = "pill_style_nav"
        const val KEY_FIRST_LAUNCH_DONE     = "first_launch_done"
        // Hangup button size control (0.1f .. 1.0f)
        const val KEY_HANGUP_WIDTH          = "hangup_button_width"
        const val DEFAULT_HANGUP_WIDTH      = DotDialerPreferenceDefaults.DEFAULT_HANGUP_WIDTH
        // Dialer role popup shown after welcome
        const val KEY_DIALER_POPUP_SHOWN    = "dialer_popup_shown"
        const val KEY_TELEGRAM_SHOWN        = "telegram_shown"
        const val KEY_SCROLL_ANIMATION      = "scroll_animation_enabled"
        const val KEY_POCKET_MODE_PREVENTION = "pocket_mode_prevention"
        const val KEY_DIRECT_CALL_ON_TAP     = "direct_call_on_tap"
        const val KEY_CLEAR_SEARCH_AFTER_CALL = "clear_search_after_call"
        const val KEY_CONTACTS_DISPLAY_ACCOUNTS = "contacts_display_accounts"
        const val KEY_LIQUID_GLASS              = "liquid_glass_ui"
        const val KEY_LG_BOTTOM_NAV            = "lg_bottom_nav"
        const val KEY_LG_DROPDOWN_MENU         = "lg_dropdown_menu"
        const val KEY_LG_DIALPAD_CALL_BUTTON   = "lg_dialpad_call_button"
        const val KEY_LG_CONTACTS_FAB          = "lg_contacts_fab"
        const val KEY_LG_RECENTS_FAB           = "lg_recents_fab"
        const val KEY_LG_CALL_SCREEN           = "lg_call_screen"
        const val KEY_LG_DIALPAD               = "lg_dialpad"
        const val KEY_BLUR_EFFECTS             = "blur_effects_ui"
        const val KEY_BLUR_INTENSITY             = "blur_intensity"
        const val KEY_DIALPAD_GLASS_STRENGTH = "dialpad_glass_strength_percent"
        // Material Blur effect elements
        const val KEY_BLUR_BOTTOM_NAV          = "blur_bottom_nav"
        const val KEY_BLUR_DROPDOWN_MENU       = "blur_dropdown_menu"
        const val KEY_BLUR_DIALPAD_CALL_BUTTON = "blur_dialpad_call_button"
        const val KEY_BLUR_CONTACTS_FAB        = "blur_contacts_fab"
        const val KEY_BLUR_RECENTS_FAB         = "blur_recents_fab"
        const val KEY_BLUR_CALL_SCREEN         = "blur_call_screen"
        const val KEY_BLUR_DIALPAD             = "BLUR_dialpad"
        const val KEY_AUTO_SPEAKER             = "auto_speaker"
        const val KEY_FLOATING_CALL            = "floating_ongoing_call"
        const val KEY_FLOATING_BUBBLE_X         = "floating_bubble_x"
        const val KEY_FLOATING_BUBBLE_Y         = "floating_bubble_y"
        // Tab Sections visibility
        const val KEY_TAB_SHOW_FAVORITES       = "tab_show_favorites"
        const val KEY_TAB_SHOW_CALLS           = "tab_show_calls"
        const val KEY_TAB_SHOW_CONTACTS        = "tab_show_contacts"
        const val KEY_TAB_SHOW_DIALPAD         = "tab_show_dialpad"
        const val KEY_TAB_SHOW_NOTES           = "tab_show_notes"
        const val KEY_TAB_SHOW_SETTINGS        = "tab_show_settings"
        const val KEY_TAB_SHOW_SEARCH          = "tab_show_search"
        // Comma-separated list of tab keys (favorites, calls, contacts, notes)
        // describing the order tabs appear in the bottom navigation bar.
        const val KEY_TAB_ORDER                = "tab_order"
        const val DEFAULT_TAB_ORDER            = "favorites,contacts,calls,dialpad,notes,search,settings"
        // Biometrics
        const val KEY_BIOMETRICS_TYPE          = "biometrics_type"         // "system" | "pin" | "password" | ""
        const val KEY_AUTHENTICATION_ENABLED = "authentication_enabled"
        const val KEY_BIOMETRICS_PIN           = "biometrics_pin"
        const val KEY_BIOMETRICS_PASSWORD      = "biometrics_password"
        const val KEY_BIOMETRICS_APP_LOCK      = "biometrics_app_lock"
        const val KEY_BIOMETRICS_APP_LOCK_ON_MINIMIZE      = "biometrics_app_lock_on_minimize"
        const val KEY_BIOMETRICS_CALL_LOCK     = "biometrics_call_lock"
        const val KEY_BIOMETRICS_CALL_LOCK_MODE    = "biometrics_call_lock_mode"    // "all" | "specified" | "skip_specified"
        const val KEY_BIOMETRICS_CALL_LOCK_NUMBERS = "biometrics_call_lock_numbers" // comma-separated phone numbers
        // Search filter (Dialpad / Calls / Contacts / Favourites search bars) — the "Filter"
        // button beside the search bar. All four default to true (checked) so search behaves
        // as broadly as possible until the user deliberately narrows it down. Persisted here
        // (rather than in-memory) so the chosen filter survives the app being closed and
        // reopened.
        const val KEY_SEARCH_FILTER_CONTACTS        = "search_filter_contacts"
        const val KEY_SEARCH_FILTER_NON_CONTACTS    = "search_filter_non_contacts"
        const val KEY_SEARCH_FILTER_CONTACT_NOTES   = "search_filter_contact_notes"

        const val KEY_DEFAULT_TAB              = "default_tab"
        const val KEY_CONTACTS_DEFAULT_ACCOUNT = "contacts_default_accounts"
        const val KEY_LAST_OPENED_TAB          = "last_opened_tab"
        const val KEY_DIALPAD_TEMP_NUMBER      = "dialpad_temp_number"
        const val KEY_IS_PRO_IAP               = "is_pro_iap"
        const val KEY_IS_PRO_SUB               = "is_pro_sub"
        const val KEY_IS_PRO_FOSS              = "is_pro_foss"
        const val KEY_AVATAR_FRAME             = "avatar_frame"
        const val KEY_HIGH_SCORE               = "horse_game_high_score"
        const val KEY_ALWAYS_FULLSCREEN_CALLS  = "always_fullscreen_calls"
        const val KEY_DIALPAD_ANIMATION        = "dialpad_animation_enabled"
        const val KEY_HIDE_VOICE_SEARCH        = "hide_voice_search"
        const val KEY_VOICE_SEARCH_ENABLED     = "voice_search_enabled"
    }

    fun isAuthenticationEnabled() = authentication.isEnabled()
    fun isAppLockEnabled() = authentication.isEnabled() && getBoolean(KEY_BIOMETRICS_APP_LOCK, false)
    suspend fun configureAuthentication(method: String, secret: String = "") = authentication.configure(method, secret)
    suspend fun disableAuthentication() = authentication.disable()
    suspend fun verifyAuthentication(method: String, secret: String): Boolean = authenticationSecrets.verify(method, secret)
    fun setAppLockEnabled(enabled: Boolean) = setBoolean(KEY_APP_LOCK_ENABLED, enabled)
    fun isBiometricLockEnabled(): Boolean = getBoolean(KEY_APP_LOCK_BIOMETRIC, true)
    fun setBiometricLockEnabled(enabled: Boolean) = setBoolean(KEY_APP_LOCK_BIOMETRIC, enabled)
    fun getAppLockPin(): String = getString(KEY_APP_LOCK_PIN, "") ?: ""
    fun setAppLockPin(pin: String) = setString(KEY_APP_LOCK_PIN, pin)
    fun getAppLockTimeout(): Int = getInt(KEY_APP_LOCK_TIMEOUT, APP_LOCK_TIMEOUT_IMMEDIATELY)
        .takeIf { it in setOf(0, 1, 5, 15, 30) } ?: APP_LOCK_TIMEOUT_IMMEDIATELY
    fun setAppLockTimeout(timeout: Int) = setInt(KEY_APP_LOCK_TIMEOUT, timeout)
    fun isFloatingCallBubbleEnabled(): Boolean = getBoolean(KEY_FLOATING_CALL_BUBBLE, true)
    fun setFloatingCallBubbleEnabled(enabled: Boolean) = setBoolean(KEY_FLOATING_CALL_BUBBLE, enabled)
    fun isHiddenContactsVisible(): Boolean = getBoolean(KEY_HIDDEN_CONTACTS_VISIBLE, false)
    fun setHiddenContactsVisible(visible: Boolean) = setBoolean(KEY_HIDDEN_CONTACTS_VISIBLE, visible)
    fun getFloatingBarRoundness(): Int = getInt(KEY_FLOATING_BAR_ROUNDNESS, DEFAULT_FLOATING_BAR_ROUNDNESS)
    fun setFloatingBarRoundness(roundness: Int) = setInt(KEY_FLOATING_BAR_ROUNDNESS, roundness)

    fun isFloatingBarBlurEnabled(): Boolean = isUiBlurEnabled()
    fun setFloatingBarBlurEnabled(enabled: Boolean) = setUiBlurEnabled(enabled)
    fun isUiBlurEnabled(): Boolean = getBoolean(KEY_UI_BLUR, getBoolean(KEY_FLOATING_BAR_BLUR, false))
    fun setUiBlurEnabled(enabled: Boolean) {
        setBoolean(KEY_UI_BLUR, enabled)
        setBoolean(KEY_FLOATING_BAR_BLUR, enabled)
    }

    fun isSwipeActionsEnabled(): Boolean = getBoolean(KEY_SWIPE_ACTIONS_ENABLED, DEFAULT_SWIPE_ENABLED)
    fun setSwipeActionsEnabled(enabled: Boolean) = setBoolean(KEY_SWIPE_ACTIONS_ENABLED, enabled)

    fun isSupporter(): Boolean = getBoolean(KEY_IS_SUPPORTER, false)
    fun setSupporter(isSupporter: Boolean) = setBoolean(KEY_IS_SUPPORTER, isSupporter)

    fun isPostCallScreenEnabled(): Boolean = getBoolean(KEY_POST_CALL_SCREEN, true)
    fun setPostCallScreenEnabled(enabled: Boolean) = setBoolean(KEY_POST_CALL_SCREEN, enabled)

    fun isMissedCallCardEnabled(): Boolean = getBoolean(KEY_MISSED_CALL_CARD, true)
    fun setMissedCallCardEnabled(enabled: Boolean) = setBoolean(KEY_MISSED_CALL_CARD, enabled)

    fun isAutoDeclineUnknownEnabled(): Boolean = getBoolean(KEY_AUTO_DECLINE_UNKNOWN, false)
    fun setAutoDeclineUnknownEnabled(enabled: Boolean) = setBoolean(KEY_AUTO_DECLINE_UNKNOWN, enabled)

    fun isAutoDeclineNonContactsEnabled(): Boolean = getBoolean(KEY_AUTO_DECLINE_NON_CONTACTS, false)
    fun setAutoDeclineNonContactsEnabled(enabled: Boolean) = setBoolean(KEY_AUTO_DECLINE_NON_CONTACTS, enabled)

    fun isDualSimDialpadButtonsEnabled(): Boolean = getBoolean(KEY_DUAL_SIM_DIALPAD_BUTTONS, false)
    fun setDualSimDialpadButtonsEnabled(enabled: Boolean) = setBoolean(KEY_DUAL_SIM_DIALPAD_BUTTONS, enabled)

    fun resetSwipeActions() {
        setBoolean(KEY_SWIPE_ACTIONS_ENABLED, DEFAULT_SWIPE_ENABLED)
        setInt(KEY_SWIPE_RIGHT_ACTION, DEFAULT_SWIPE_RIGHT_ACTION)
        setInt(KEY_SWIPE_LEFT_ACTION, DEFAULT_SWIPE_LEFT_ACTION)
    }
}
