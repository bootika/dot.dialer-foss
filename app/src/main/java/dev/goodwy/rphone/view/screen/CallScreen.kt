package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.components.CallControlIcons

import dev.goodwy.rphone.view.components.DotDialerHangupIcon
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.goodwy.rphone.view.components.callPressFeedback
import dev.goodwy.rphone.view.components.CallTapIconButton
import androidx.compose.ui.platform.testTag
import dev.goodwy.rphone.view.theme.CallActionColors
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.R
import dev.goodwy.rphone.controller.CallViewModel
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.lock.AppLockManager
import dev.goodwy.rphone.controller.sensor.PocketModeManager
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.controller.util.AndroidHaptics
import dev.goodwy.rphone.controller.util.CallBackReminderScheduler
import dev.goodwy.rphone.controller.util.currentTelecomState
import dev.goodwy.rphone.controller.util.loadPhoneAccount
import dev.goodwy.rphone.controller.util.isTelecomConference
import dev.goodwy.rphone.controller.util.supportedCallAudioRoutes
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.LocalRillAvatarAppearance

import dev.goodwy.rphone.view.components.rillAvatarAppearance
import dev.goodwy.rphone.view.components.rillAvatarColors
import dev.goodwy.rphone.view.components.RillSelectionDialog
import dev.goodwy.rphone.view.theme.MyColors.bottomBarColor
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.color_call_end
import dev.goodwy.rphone.controller.util.formatDuration
import dev.goodwy.rphone.controller.util.AmbientDropletPreference
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.liquidglass.LocalLiquidGlassBackdrop
import dev.goodwy.rphone.liquidglass.backdrops.LayerBackdrop
import dev.goodwy.rphone.liquidglass.drawBackdrop
import dev.goodwy.rphone.liquidglass.drawPlainBackdrop
import dev.goodwy.rphone.liquidglass.effects.blur
import dev.goodwy.rphone.liquidglass.effects.colorControls
import dev.goodwy.rphone.liquidglass.effects.lens
import dev.goodwy.rphone.liquidglass.highlight.Highlight
import dev.goodwy.rphone.liquidglass.shadow.Shadow
import dev.goodwy.rphone.view.screen.settings.PasswordSetupDialog
import dev.goodwy.rphone.view.screen.settings.PinSetupDialog
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import java.util.Date
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import io.github.bootika.dotdialer.core.haptics.HapticIntent

private const val CALL_SCREEN_LOG_TAG = "CallScreen"

@Composable
fun ExpressiveCallScreen(
    call: Call,
    callState: Int,
    contactName: String,
    companyName: String?,
    phoneNumber: String,
    photoUri: String?,
    audioState: CallAudioState?,
    initialConnectTime: Long = 0L,
    backgroundUri: String? = null,
    skipIncomingScreen: Boolean = false,
    liquidGlassBackdrop: LayerBackdrop,
    frostedWindow: Boolean = false,
    photoLookupComplete: Boolean = true,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val conferenceLabel = stringResource(R.string.conference_call)
    val displayContactName = contactName.ifBlank {
        phoneNumber.ifBlank { resources.getString(R.string.label_unknown) }
    }
    val preferenceManager = koinInject<PreferenceManager>()
    val contactsRepo = koinInject<IContactsRepository>()
    val callViewModel = koinInject<CallViewModel>()
    val primarySnapshot by rememberTelecomCallSnapshot(call)
    val phoneAccount = remember(call, call.details?.accountHandle) {
        val handle = call.details?.accountHandle
        if (handle == null) null else loadPhoneAccount(context, handle)
    }

    val allCalls by callViewModel.allCalls.collectAsStateWithLifecycle()
    val callerPhotos by callViewModel.callerMetadataMap.collectAsStateWithLifecycle()
    val photoRevision = dev.goodwy.rphone.view.components.LocalCallPhotoRevision.current
    val otherCall by rememberOtherLogicalTelecomCall(allCalls, call)
    val otherCallNumber = android.net.Uri.decode(otherCall?.details?.handle?.schemeSpecificPart.orEmpty())
        .takeIf { otherCall?.details?.handlePresentation == TelecomManager.PRESENTATION_ALLOWED }.orEmpty()
    val otherCallProvidedName = otherCall?.let { secondaryCall ->
        if (secondaryCall.details?.callerDisplayNamePresentation == TelecomManager.PRESENTATION_ALLOWED) {
            secondaryCall.details?.callerDisplayName?.takeIf { it.isNotBlank() }
        } else null
    }
    val otherCallIsConference = otherCall?.isTelecomConference() == true
    var otherCallName by remember(otherCall, otherCallIsConference) {
        mutableStateOf(
            if (otherCallIsConference) conferenceLabel else otherCallProvidedName ?: otherCallNumber,
        )
    }
    var otherCallColorSeed by remember(otherCall) { mutableStateOf(otherCallName) }
    var otherCallPhotoUri by remember(otherCall) { mutableStateOf<String?>(null) }
    LaunchedEffect(otherCall, otherCallNumber, otherCallProvidedName, otherCallIsConference, conferenceLabel, callerPhotos, photoRevision) {
        val secondaryCall = otherCall
        if (secondaryCall == null) {
            otherCallName = ""
            otherCallPhotoUri = null
            return@LaunchedEffect
        }

        if (otherCallIsConference) {
            otherCallName = conferenceLabel
            otherCallPhotoUri = null
            return@LaunchedEffect
        }

        val displayOrder = preferenceManager.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0)
        val contact = if (otherCallNumber.isBlank()) null else try {
            contactsRepo.getContactByNumberInRegion(otherCallNumber, dev.goodwy.rphone.controller.util.resolveContactLookupRegion(context, secondaryCall.details?.accountHandle))
        } catch (error: Exception) {
            Log.w(CALL_SCREEN_LOG_TAG, "Unable to resolve the secondary call contact", error)
            null
        }
        otherCallName = when {
            contact != null -> getDisplayName(contact, displayOrder)
            !otherCallProvidedName.isNullOrBlank() -> otherCallProvidedName
            otherCallNumber.isNotBlank() -> otherCallNumber
            else -> resources.getString(R.string.label_unknown_number)
        }
        otherCallColorSeed = contact?.displayName ?: otherCallName
        otherCallPhotoUri = if (callerPhotos[otherCallNumber]?.forLookupRegion(dev.goodwy.rphone.controller.util.resolveContactLookupRegion(context, secondaryCall.details?.accountHandle)) != null) callerPhotos[otherCallNumber]?.photoUri
            else contact?.photoUri ?: dev.goodwy.rphone.controller.telecomCallerPhotoUri(secondaryCall)
    }
    val otherCallSnapshot = otherCall?.let { rememberTelecomCallSnapshot(it).value }
    val canMergeCalls = otherCall != null &&
        (otherCall in primarySnapshot.conferenceableCalls ||
            call in otherCallSnapshot?.conferenceableCalls.orEmpty())

    val screenAudio = callScreenAudioSnapshot(audioState)
    val isMuted = screenAudio.muted
    val supportedAudioRoutes = remember(screenAudio.supportedRouteMask) {
        supportedCallAudioRoutes(screenAudio.supportedRouteMask)
    }
    val callCapabilities = call.details?.callCapabilities ?: 0
    val controls = callControlAvailability(
        callState = callState,
        canHold = callCapabilities and Call.Details.CAPABILITY_HOLD != 0,
        canMute = callCapabilities and Call.Details.CAPABILITY_MUTE != 0,
        isConference = call.isTelecomConference(),
        supportedAudioRouteCount = supportedAudioRoutes.size,
        hasOtherCall = otherCall != null,
    )

    val currentSession by callViewModel.currentCallSession.collectAsStateWithLifecycle()
    val primaryConnectTime = primarySnapshot.details?.connectTimeMillis?.takeIf { it > 0L }
        ?: currentSession?.takeIf { it.call === call }?.connectTimeMillis ?: 0L
    val primaryElapsed = rememberCallElapsedSeconds(callState, primaryConnectTime)
    var showKeypad by remember { mutableStateOf(false) }
    var showCallConnectionDetails by remember { mutableStateOf(false) }
    var showCallAppearanceMenu by remember { mutableStateOf(false) }
    var callAppearanceAnchor by remember { mutableStateOf(Offset.Zero) }
    var avatarPositionInRoot by remember { mutableStateOf(Offset.Zero) }
    var avatarCenterInRoot by remember { mutableStateOf(Offset.Zero) }
    var typedDigits by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    var isEnding by remember { mutableStateOf(false) }
    var contactId by remember(phoneNumber) { mutableStateOf<String?>(null) }
    var contactLookupComplete by remember(phoneNumber) { mutableStateOf(false) }

    LaunchedEffect(phoneNumber) {
        contactLookupComplete = false
        contactId = if (phoneNumber.isBlank()) null else try {
            contactsRepo.getContactByNumber(phoneNumber)?.id
        } catch (error: Exception) {
            Log.w(CALL_SCREEN_LOG_TAG, "Unable to resolve the active caller as a contact", error)
            null
        }
        contactLookupComplete = true
    }

    fun callDisconnect(isIncoming: Boolean = false) {
        if (isIncoming) isEnding = true
        try {
            call.disconnect()
        } catch (error: Exception) {
            Log.w(CALL_SCREEN_LOG_TAG, "Unable to disconnect Telecom call", error)
        }
    }

    fun rejectIncomingCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                call.reject(Call.REJECT_REASON_DECLINED)
            } else {
                call.disconnect()
            }
        } catch (error: Exception) {
            Log.w(CALL_SCREEN_LOG_TAG, "Unable to reject incoming Telecom call", error)
        }
    }

    fun answerAudioCall() {
        try {
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
        } catch (error: Exception) {
            Log.w(CALL_SCREEN_LOG_TAG, "Unable to answer Telecom call", error)
        }
    }

    fun holdExistingAndAnswer() {
        otherCall?.let { existingCall ->
            if (existingCall.currentTelecomState() == Call.STATE_ACTIVE) {
                runCatching { existingCall.hold() }
                    .onFailure { Log.w(CALL_SCREEN_LOG_TAG, "Unable to hold the current call", it) }
            }
        }
        callViewModel.setPreferredCall(call)
        answerAudioCall()
    }

    fun declineIncomingAndResumeExisting() {
        isEnding = true
        rejectIncomingCall()
        otherCall?.let { existingCall ->
            callViewModel.setPreferredCall(existingCall)
            if (existingCall.currentTelecomState() == Call.STATE_HOLDING) {
                runCatching { existingCall.unhold() }
                    .onFailure { Log.w(CALL_SCREEN_LOG_TAG, "Unable to resume call after decline", it) }
            }
        }
    }

    fun swapWithOtherCall() {
        val secondaryCall = otherCall ?: return
        callViewModel.setPreferredCall(secondaryCall)
        swapTelecomCalls(call, secondaryCall)
    }

    fun mergeWithOtherCall() {
        val secondaryCall = otherCall ?: return
        if (mergeTelecomCalls(call, secondaryCall)) {
            callViewModel.setPreferredCall(call)
        }
    }

    val settingsState by preferenceManager.settingsChanged.collectAsStateWithLifecycle()
    val showCallScreenAvatar = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_SHOW_CALL_SCREEN_AVATAR, true)
    }
    val hideAvatarWithBg = remember(settingsState) {
        false // Retired D106; stored legacy value is preserved.
    }
    var callScreenAppearance by remember {
        mutableStateOf(
            preferenceManager.getString(
                PreferenceManager.KEY_CALL_SCREEN_APPEARANCE,
                PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE,
            ) ?: PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE,
        )
    }
    LaunchedEffect(settingsState) {
        callScreenAppearance = preferenceManager.getString(
            PreferenceManager.KEY_CALL_SCREEN_APPEARANCE,
            PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE,
        ) ?: PreferenceManager.DEFAULT_CALL_SCREEN_APPEARANCE
    }
    fun openCallAppearanceMenu(position: Offset) {
        if (photoUri.isNullOrEmpty()) return
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        callAppearanceAnchor = position
        showCallAppearanceMenu = true
    }
    fun selectCallScreenAppearance(appearance: String) {
        callScreenAppearance = appearance
        preferenceManager.setString(PreferenceManager.KEY_CALL_SCREEN_APPEARANCE, appearance)
        showCallAppearanceMenu = false
    }
    val useFullPhotoAppearance = callScreenAppearance == PreferenceManager.CALL_SCREEN_APPEARANCE_FULL_PHOTO
    val hasBackground = !backgroundUri.isNullOrEmpty()
    val needsIdentityFallback = photoUri.isNullOrEmpty()
    val shouldShowAvatar = showCallIdentityAvatar(
        fullPhoto = useFullPhotoAppearance,
        hasPhoto = !needsIdentityFallback,
        showAvatar = showCallScreenAvatar,
        hideWithBackground = hideAvatarWithBg,
        hasBackground = hasBackground,
        photoLookupComplete = photoLookupComplete,
    )
    val globalBackdrop = LocalLiquidGlassBackdrop.current
    val lgCallScreen = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_LG_CALL_SCREEN, true) }
    val blurCallScreen = remember(settingsState) { preferenceManager.getBoolean(PreferenceManager.KEY_BLUR_CALL_SCREEN, true) }
    val blurIntensity = remember(settingsState) { preferenceManager.getInt(PreferenceManager.KEY_BLUR_INTENSITY, 20).toFloat() }
    val ambientDroplets = remember(settingsState) { preferenceManager.ambientDropletRenderConfig() }
    val useLgCallScreen = lgCallScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && globalBackdrop != null && view.isHardwareAccelerated
    val useBlurCallScreen = blurCallScreen && !useLgCallScreen && Build.VERSION.SDK_INT >= 31 && globalBackdrop != null && view.isHardwareAccelerated
    val callConnectionInfo = remember(call, call.details) {
        call.details?.let(::resolveCallConnectionInfo)
    }

    val localDensity = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val windowHeightDp = with(localDensity) { windowSize.height.toDp().value }
    val systemBarPadding = WindowInsets.systemBars.asPaddingValues()
    val usableWindowHeightDp = (
        windowHeightDp -
            systemBarPadding.calculateTopPadding().value -
            systemBarPadding.calculateBottomPadding().value
        ).coerceAtLeast(1f)
    var fullPhotoZoneTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var middleVisualZoneTopPx by remember { mutableFloatStateOf(Float.NaN) }
    var actionControlsTopPx by remember { mutableFloatStateOf(Float.NaN) }
    val photoClearZoneCenterDp = callMiddleZoneCenter(
        start = fullPhotoZoneTopPx,
        end = actionControlsTopPx,
    )?.let { centerPx ->
        with(localDensity) { centerPx.toDp().value }
    }
    val fullPhotoOffsetDp = with(localDensity) {
        callFullPhotoVerticalOffset(fullPhotoZoneTopPx, actionControlsTopPx).toDp().value
    }

    BackHandler(showKeypad) {
        showKeypad = false
    }

    var showQuickResponsesSheet by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var showUnknownCallerActions by remember { mutableStateOf(false) }
    var showBlockAndReportConfirmation by remember { mutableStateOf(false) }
    val isUnknownCaller = contactLookupComplete &&
        phoneNumber.isNotBlank() &&
        contactId.isNullOrBlank()

    val pocketModeEnabled = remember(settingsState) {
        preferenceManager.getBoolean(PreferenceManager.KEY_POCKET_MODE, false)
    }
    var isPocketModeCovered by remember { mutableStateOf(false) }
    var pocketModeDismissedManually by remember { mutableStateOf(false) }

    DisposableEffect(callState, pocketModeEnabled) {
        if (pocketModeEnabled && callState == Call.STATE_RINGING) {
            val manager = PocketModeManager(context)
            manager.startListening { isNear ->
                isPocketModeCovered = isNear
            }
            onDispose {
                manager.stopListening()
            }
        } else {
            isPocketModeCovered = false
            onDispose { }
        }
    }

    val onSendQuickResponse: (String) -> Unit = { message ->
        val canRespondViaText =
            (call.details?.callCapabilities ?: 0) and Call.Details.CAPABILITY_RESPOND_VIA_TEXT != 0
        if (canRespondViaText) {
            try {
                @Suppress("DEPRECATION")
                call.reject(true, message)
            } catch (error: Exception) {
                Log.w(CALL_SCREEN_LOG_TAG, "Telecom rejected the SMS decline request", error)
                rejectIncomingCall()
                openQuickResponseComposer(context, phoneNumber, message)
            }
        } else {
            rejectIncomingCall()
            openQuickResponseComposer(context, phoneNumber, message)
        }
        showQuickResponsesSheet = false
    }

    fun scheduleCallBackReminder(triggerAtMillis: Long) {
        if (scheduleCallBackReminderAndNotify(context, phoneNumber, contactName, triggerAtMillis)) {
            rejectIncomingCall()
            showReminderSheet = false
        }
    }

    var showNoteWindow by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    val callLockEnabled = remember(call, phoneNumber, settingsState) {
        preferenceManager.shouldGateCallWithBiometric(phoneNumber)
    }
    val originallySkippedCall = remember { if (skipIncomingScreen) call else null }
    val authenticationMethod = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "")
    var callBiometricUnlocked by remember(call, phoneNumber, callLockEnabled, authenticationMethod) {
        mutableStateOf(!callLockEnabled || (skipIncomingScreen && call === originallySkippedCall))
    }

    var showCallBiometricUnlock by remember(call, phoneNumber, callLockEnabled, authenticationMethod) { mutableStateOf(false) }
    var biometricGatesScreen by remember(call, phoneNumber, callLockEnabled, authenticationMethod) { mutableStateOf(false) }
    var pendingAction by remember(call, phoneNumber, callLockEnabled, authenticationMethod) { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(call, callState, callLockEnabled, authenticationMethod) {
        if (callLockEnabled && !callBiometricUnlocked && !showCallBiometricUnlock) {
            if (callState == Call.STATE_RINGING) {
                biometricGatesScreen = true
                showCallBiometricUnlock = true
            }
        }
    }

    LaunchedEffect(phoneNumber) {
        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
            val existing = NoteManager.readNoteByPhone(context, phoneNumber)
            if (existing.isNotBlank()) noteText = existing
        }
    }

    LaunchedEffect(contactName) {
        if (phoneNumber.isNotEmpty() && noteText.isBlank()) {
            val existing = NoteManager.readNote(context, contactName, phoneNumber)
            if (existing.isNotBlank()) noteText = existing
        }
    }

    LaunchedEffect(noteText) {
        if (phoneNumber.isNotEmpty() && noteText.isNotBlank()) {
            delay(1000.milliseconds)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
            }
        }
    }

    LaunchedEffect(callState) {
        if ((callState == Call.STATE_DISCONNECTED || callState == Call.STATE_DISCONNECTING) && noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
            NoteManager.writeNote(context, contactName, phoneNumber, noteText)
        }
    }
    val hasExpandedCallPanel = showKeypad || showNoteWindow || showMore
    val callAppearanceLabel = stringResource(R.string.call_appearance)
    val avatarAppearance = LocalRillAvatarAppearance.current ?: remember(settingsState) { preferenceManager.rillAvatarAppearance() }
    val avatarFallbackColor = rillAvatarColors(
        dev.goodwy.rphone.view.components.avatarIdentityColorSeed(displayContactName, phoneNumber),
        avatarAppearance,
        MaterialTheme.colorScheme,
    ).first
    val conferenceIdentities = rememberConferenceParticipantIdentities(if (primarySnapshot.isConference) primarySnapshot.children else emptyList())
    val conferenceAvatarSources = if (primarySnapshot.isConference) conferenceIdentities.map { identity ->
        CallMistSource(identity.call, identity.photoUri.takeIf { avatarAppearance.showPicture }, rillAvatarColors(
            dev.goodwy.rphone.view.components.avatarIdentityColorSeed(identity.colorSeed, identity.number), avatarAppearance, MaterialTheme.colorScheme,
        ).first)
    } else null

    Box(modifier = Modifier.fillMaxSize()) {
        ExpressiveBackground(
            photoUri = photoUri,
            avatarFallbackColor = avatarFallbackColor,
            conferenceAvatarSources = conferenceAvatarSources,
            useAvatarPhoto = avatarAppearance.showPicture,
            backgroundUri = backgroundUri,
            liquidGlassBackdrop = liquidGlassBackdrop,
            hideGradient = false,
            fullPhoto = useFullPhotoAppearance,
            circularAvatarVisible = shouldShowAvatar && !primarySnapshot.isConference && !hasExpandedCallPanel,
            customGlassState = DialpadEffectState(
                lgCallScreen,
                CUSTOM_CALL_BACKGROUND_GLASS_STRENGTH,
                blurIntensity.coerceIn(0f, 100f),
            ),
            clearZoneCenterDp = photoClearZoneCenterDp,
            fullPhotoOffsetDp = fullPhotoOffsetDp,
            frostedWindow = frostedWindow,
            ambientDroplets = ambientDroplets,
            ambientMiddleZone = AmbientDropletMiddleZone(middleVisualZoneTopPx, actionControlsTopPx),
            onPhotoLongClick = if (
                useFullPhotoAppearance &&
                !photoUri.isNullOrEmpty() &&
                !hasExpandedCallPanel
            ) {
                ::openCallAppearanceMenu
            } else {
                null
            },
            photoLongClickLabel = callAppearanceLabel,
        )

        CallAppearanceMenu(
            expanded = showCallAppearanceMenu,
            appearance = callScreenAppearance,
            dropletsEnabled = ambientDroplets.enabled,
            onDropletsChanged = { enabled ->
                preferenceManager.setBoolean(AmbientDropletPreference.ENABLED_KEY, enabled)
            },
            anchorPosition = callAppearanceAnchor,
            onDismiss = { showCallAppearanceMenu = false },
            onSelect = ::selectCallScreenAppearance,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SecondaryCallCard(
                otherCall = otherCall,
                otherCallName = otherCallName,
                otherCallColorSeed = otherCallColorSeed,
                otherCallPhotoUri = otherCallPhotoUri,
                otherCallNumber = otherCallNumber,
                currentCallState = callState,
                        onSwap = ::swapWithOtherCall,
                        canMerge = canMergeCalls,
                        onMerge = ::mergeWithOtherCall,
            )

            val displayedCompanyName = displayedCallCompanyName(companyName, displayContactName)
            val screenSpacing = remember(
                usableWindowHeightDp,
                otherCall,
            ) {
                callScreenSpacing(
                    availableHeightDp = usableWindowHeightDp.toInt(),
                    hasOtherCall = otherCall != null,
                )
            }
            val identityVerticalCompensationDp =
                callIdentityVerticalCompensationDp(displayedCompanyName != null)
            val showsCircularAvatar = shouldShowAvatar && !primarySnapshot.isConference && !hasExpandedCallPanel

            // --- APPROVED CALL HERO ---
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .weight(if (hasExpandedCallPanel) 0.42f else 1f)
            ) {
                Spacer(modifier = Modifier.height(screenSpacing.topSpacingDp.dp))
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(500))
                ) {
                    CallIdentityHeader(
                        contactName = displayContactName,
                        companyName = displayedCompanyName,
                        callState = callState,
                        duration = primaryElapsed?.let(::formatDuration) ?: "—",
                        phoneAccount = phoneAccount,
                        connectionInfo = callConnectionInfo,
                        spacing = screenSpacing,
                        identityVerticalCompensationDp = identityVerticalCompensationDp,
                        onConnectionDetailsClick = { showCallConnectionDetails = true },
                    )
                }

                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (showsCircularAvatar) 0.dp else screenSpacing.avatarTopGapDp.dp)
                        .testTag("callPhotoZoneStart")
                        .onGloballyPositioned { coordinates ->
                            // Full photo starts at the header edge; ambient keeps its padded zone.
                            val measuredTop = coordinates.positionInRoot().y
                            if (!fullPhotoZoneTopPx.isFinite() ||
                                abs(fullPhotoZoneTopPx - measuredTop) > 0.5f
                            ) {
                                fullPhotoZoneTopPx = measuredTop
                            }
                            val measuredBottom = measuredTop + coordinates.size.height
                            if (!middleVisualZoneTopPx.isFinite() ||
                                abs(middleVisualZoneTopPx - measuredBottom) > 0.5f
                            ) {
                                middleVisualZoneTopPx = measuredBottom
                            }
                        },
                )

                AnimatedVisibility(
                    visible = (shouldShowAvatar || primarySnapshot.isConference) && !hasExpandedCallPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    enter = if (useFullPhotoAppearance && !primarySnapshot.isConference) EnterTransition.None else fadeIn(tween(320)),
                    exit = if (useFullPhotoAppearance && !primarySnapshot.isConference) ExitTransition.None else fadeOut(tween(180)),
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize().testTag("callAvatarMiddleZone"),
                        contentAlignment = Alignment.Center,
                    ) {
                        val boundedAvatarSize = callCircularAvatarSizeDp(
                            availableWidthDp = maxWidth.value.toInt(),
                            availableHeightDp = maxHeight.value.toInt(),
                        ).dp
                        if (primarySnapshot.isConference) {
                            ConferenceParticipants(parent = call, identities = conferenceIdentities, parentState = primarySnapshot.state)
                        } else if (photoUri.isNullOrEmpty()) {
                            CalmCallAvatar(
                                photoUri = photoUri,
                                displayName = displayContactName,
                                phoneNumber = phoneNumber,
                                avatarSize = boundedAvatarSize,
                                modifier = Modifier.testTag("callPrimaryAvatar"),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .sizeIn(maxWidth = boundedAvatarSize, maxHeight = boundedAvatarSize)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .onGloballyPositioned { coordinates ->
                                        avatarPositionInRoot = coordinates.positionInRoot()
                                        avatarCenterInRoot = avatarPositionInRoot + Offset(
                                            coordinates.size.width / 2f,
                                            coordinates.size.height / 2f,
                                        )
                                    }
                                    .pointerInput(photoUri) {
                                        detectTapGestures(
                                            onLongPress = { localPosition ->
                                                openCallAppearanceMenu(avatarPositionInRoot + localPosition)
                                            },
                                        )
                                    }
                                    .semantics {
                                        onLongClick(label = callAppearanceLabel) {
                                            openCallAppearanceMenu(avatarCenterInRoot)
                                            true
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                CalmCallAvatar(
                                    photoUri = photoUri,
                                    displayName = displayContactName,
                                    phoneNumber = phoneNumber,
                                    avatarSize = boundedAvatarSize,
                                    modifier = Modifier.testTag("callPrimaryAvatar"),
                                )
                            }
                        }
                    }
                }
            }

            // --- UI CONTROLS ---
            if (callState != Call.STATE_RINGING && !isEnding) {
                val controlBtnFg = MaterialTheme.colorScheme.onSurface

                val controlContent: @Composable () -> Unit = {
                    Row(
                        modifier = Modifier
                            .padding(start = 9.dp, end = 9.dp, top = if (showKeypad || showsCircularAvatar) 0.dp else 8.dp, bottom = if (showKeypad) 0.dp else 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .then(if (showKeypad) Modifier.testTag("inCallKeypadContent") else Modifier)
                                .padding(
                                    start = if (showKeypad) 8.dp else if (hasExpandedCallPanel) 20.dp else (CALL_ACTION_CONTROLS_INSET_DP - 9).dp,
                                    end = if (showKeypad) 8.dp else if (hasExpandedCallPanel) 20.dp else (CALL_ACTION_CONTROLS_INSET_DP - 9).dp,
                                    top = if (hasExpandedCallPanel) {
                                        if (showKeypad) 8.dp else 20.dp
                                    } else {
                                        0.dp
                                    },
                                    bottom = if (hasExpandedCallPanel) {
                                        if (showKeypad) 8.dp else 20.dp
                                    } else {
                                        screenSpacing.controlsBottomPaddingDp.dp
                                    }
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AnimatedContent(
                                targetState = showKeypad || showNoteWindow || showMore,
                                transitionSpec = {
                                    (fadeIn() + expandVertically(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    )) togetherWith (fadeOut() + shrinkVertically(
                                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                                    ))
                                },
                                label = "moreContent"
                            ) { visible ->
                                if (visible) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                if (showKeypad) stringResource(R.string.keypad)
                                                else if (showNoteWindow) stringResource(R.string.add_note)
                                                else stringResource(R.string.more),
                                                style = MaterialTheme.typography.titleMedium,
                                                color = controlBtnFg
                                            )
                                            CallTapIconButton(onClick = {
                                                showKeypad = false
                                                showNoteWindow = false
                                                showMore = false
                                            }) { Icon(Icons.Rounded.Cancel, stringResource(R.string.cancel), tint = controlBtnFg) }
                                        }
                                        Spacer(modifier = Modifier.height(if (showKeypad) 4.dp else 12.dp))

                                        if (showMore) {
                                            RillExpressiveCard {
                                                MoreItem(
                                                    headline = stringResource(R.string.view_contact),
                                                    leadingIcon = Icons.Rounded.PersonOutline,
                                                    onClick = {
                                                        openInAppContactDetails(context, contactId, phoneNumber)
                                                    }
                                                )
                                                MoreItem(
                                                    headline = stringResource(R.string.message),
                                                    leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_message_outline),
                                                    onClick = {
                                                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                                                            data = "smsto:$phoneNumber".toUri()
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                                )
                                                if (otherCall == null) {
                                                    MoreItem(
                                                        headline = if (callState == Call.STATE_HOLDING) {
                                                            stringResource(R.string.resume)
                                                        } else {
                                                            stringResource(R.string.hold)
                                                        },
                                                        leadingIcon = if (callState == Call.STATE_HOLDING) {
                                                            CallControlIcons.Resume
                                                        } else {
                                                            Icons.Default.Pause
                                                        },
                                                        enabled = callState != Call.STATE_DIALING,
                                                        onClick = {
                                                            if (callState == Call.STATE_HOLDING) call.unhold() else call.hold()
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        if (showNoteWindow) {
                                            RillExpressiveCard {
                                                MoreItem(
                                                    headline = contactName,
                                                    leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                    trailingIcon = Icons.Default.Check,
                                                    enabled = callState != Call.STATE_DIALING,
                                                    onClick = {
                                                        if (phoneNumber.isNotEmpty()) NoteManager.writeNote(context, contactName, phoneNumber, noteText)
                                                        showNoteWindow = false
                                                        showKeypad = false
                                                        showMore = true
                                                    }
                                                )
                                                Surface(
                                                    color = cardColor,
                                                    shape = RoundedCornerShape(cardCornerExtraSmall),
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shadowElevation = 0.dp
                                                ) {
                                                    OutlinedTextField(
                                                        value = noteText,
                                                        onValueChange = { noteText = it },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(min = 184.dp, max = 184.dp),
                                                        placeholder = { Text(stringResource(R.string.type_your_note)) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        minLines = 3,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            focusedBorderColor = Color.Transparent,
                                                            unfocusedBorderColor = Color.Transparent)
                                                    )
                                                }
                                            }
                                        }

                                        if (showKeypad) {
                                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                InCallKeypad(
                                                    call = call,
                                                    typedDigits = typedDigits,
                                                    onDigitClick = { digit -> typedDigits += digit }
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(if (showKeypad) 8.dp else 24.dp))
                                    }
                                }
                            }

                            if (!hasExpandedCallPanel) {
                                val audioRoute = screenAudio.route
                                val bluetoothRouteLabel = screenAudio.bluetoothDeviceName
                                val audioRouteLabel = when (audioRoute) {
                                    CallAudioState.ROUTE_SPEAKER -> stringResource(R.string.audio_route_speaker)
                                    CallAudioState.ROUTE_BLUETOOTH -> bluetoothRouteLabel.ifBlank {
                                        stringResource(R.string.audio_route_bluetooth)
                                    }
                                    CallAudioState.ROUTE_WIRED_HEADSET -> stringResource(R.string.audio_route_headset)
                                    else -> stringResource(R.string.audio_route_handset)
                                }
                                CallActionGrid(
                                    rowCounts = listOf(3, 3),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("callActionControls")
                                        .onGloballyPositioned { coordinates ->
                                            val measuredTop = coordinates.positionInRoot().y
                                            if (!actionControlsTopPx.isFinite() ||
                                                abs(actionControlsTopPx - measuredTop) > 0.5f
                                            ) {
                                                actionControlsTopPx = measuredTop
                                            }
                                        },
                                ) {
                                        CallScreenActionButton(
                                            icon = when {
                                                callState == Call.STATE_HOLDING && otherCall == null ->
                                                    CallControlIcons.Resume
                                                else -> CallControlIcons.Add
                                            },
                                            label = stringResource(
                                                when {
                                                    callState == Call.STATE_HOLDING && otherCall == null ->
                                                        R.string.resume
                                                    else -> R.string.add_call
                                                },
                                            ),
                                            enabled = controls.primaryAction,
                                            isResumeAction = callState == Call.STATE_HOLDING && otherCall == null,
                                            useLiquidGlass = useLgCallScreen,
                                            useBlur = useBlurCallScreen,
                                            blurIntensity = blurIntensity,
                                        ) {
                                            performAddCallOrResume(
                                                    context = context,
                                                    call = call,
                                                    callState = callState,
                                                    hasOtherCall = otherCall != null,
                                            )
                                        }
                                        CallScreenActionButton(
                                            icon = callMuteActionIcon(isMuted),
                                            label = stringResource(if (isMuted) R.string.unmute else R.string.mute),
                                            enabled = controls.mute,
                                            isActive = isMuted,
                                            useLiquidGlass = useLgCallScreen,
                                            useBlur = useBlurCallScreen,
                                            blurIntensity = blurIntensity,
                                        ) {
                                            callViewModel.toggleMute()
                                        }
                                        CallScreenActionButton(
                                            icon = callAudioRouteIcon(audioRoute),
                                            label = audioRouteLabel,
                                            enabled = controls.audioRoute,
                                            isActive = audioRoute != CallAudioState.ROUTE_EARPIECE,
                                            useLiquidGlass = useLgCallScreen,
                                            useBlur = useBlurCallScreen,
                                            blurIntensity = blurIntensity,
                                        ) {
                                            callViewModel.cycleAudioRoute()
                                        }
                                        CallScreenActionButton(
                                            icon = CallControlIcons.Note,
                                            label = stringResource(R.string.add_note),
                                            enabled = controls.more,
                                            useLiquidGlass = useLgCallScreen,
                                            useBlur = useBlurCallScreen,
                                            blurIntensity = blurIntensity,
                                        ) {
                                            showNoteWindow = true
                                            showKeypad = false
                                            showMore = false
                                        }
                                        CallScreenActionButton(
                                            icon = CallControlIcons.Keypad,
                                            label = stringResource(R.string.keypad),
                                            enabled = controls.keypad,
                                            useLiquidGlass = useLgCallScreen,
                                            useBlur = useBlurCallScreen,
                                            blurIntensity = blurIntensity,
                                        ) {
                                            showKeypad = true
                                            showNoteWindow = false
                                            showMore = false
                                        }
                                        CallScreenActionButton(
                                            icon = CallControlIcons.More,
                                            label = stringResource(R.string.more),
                                            enabled = controls.more,
                                            useLiquidGlass = useLgCallScreen,
                                            useBlur = useBlurCallScreen,
                                            blurIntensity = blurIntensity,
                                        ) {
                                            showMore = true
                                            showNoteWindow = false
                                            showKeypad = false
                                        }
                                }
                            }

                            val endInteraction = remember { MutableInteractionSource() }
                            val endPressed by endInteraction.collectIsPressedAsState()
                            val hangupWidthFraction = remember(settingsState) {
                                preferenceManager.getFloat(
                                    PreferenceManager.KEY_HANGUP_WIDTH,
                                    PreferenceManager.DEFAULT_HANGUP_WIDTH,
                                ).coerceIn(0.1f, 1f)
                            }

                            Spacer(
                                modifier = Modifier.height(
                                    if (showKeypad) 8.dp else if (hasExpandedCallPanel) 18.dp
                                    else screenSpacing.hangupGapDp.dp
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BoxWithConstraints(contentAlignment = Alignment.Center) {
                                    val geometry = hangupButtonGeometry(
                                        availableWidthDp = maxWidth.value,
                                        savedSetting = hangupWidthFraction,
                                    )
                                    Surface(
                                        onClick = {
                                            AndroidHaptics.performOnView(view, HapticIntent.REJECT)
                                            if (noteText.isNotBlank() && phoneNumber.isNotEmpty()) {
                                                NoteManager.writeNote(context, contactName, phoneNumber, noteText)
                                            }
                                            callDisconnect()
                                        },
                                        modifier = Modifier
                                            .width(geometry.widthDp.dp)
                                            .height(geometry.heightDp.dp)
                                            .testTag("callHangupSurface")
                                            .callPressFeedback(endPressed),
                                        shape = if (geometry.isCircle) {
                                            CircleShape
                                        } else {
                                            RoundedCornerShape((HANGUP_BUTTON_HEIGHT_DP / 2f).dp)
                                        },
                                        color = if (endPressed) CallActionColors.rejectCommitted else CallActionColors.rejectIdle,
                                        interactionSource = endInteraction
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                DotDialerHangupIcon,
                                                stringResource(R.string.end_call),
                                                tint = Color.White,
                                                modifier = Modifier.size(callActionIconSize(DotDialerHangupIcon))
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val controlShape = MaterialTheme.shapes.extraExtraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))

                if (!hasExpandedCallPanel) {
                    Box(modifier = Modifier.fillMaxWidth()) { controlContent() }
                } else if (showKeypad && useLgCallScreen) {
                    DialpadGlassSurface(
                        state = DialpadEffectState(glass = true, strength = 100, blurRadius = blurIntensity),
                        modifier = Modifier.fillMaxWidth().testTag("inCallKeypadPanel"),
                        shape = controlShape,
                        backdrop = globalBackdrop,
                    ) { controlContent() }
                } else if (useLgCallScreen) {
                    Surface(
                        shape           = controlShape,
                        color           = bottomBarColor.copy(alpha = 0.35f),
                        shadowElevation = 0.dp,
                        tonalElevation  = 0.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBackdrop(
                                backdrop = globalBackdrop,
                                shape = { controlShape },
                                effects = {
                                    val d = density
                                    colorControls(saturation = 1.4f)
                                    blur(blurIntensity * d)
                                    lens(
                                        refractionHeight = 23f * d,
                                        refractionAmount = 64f * d
                                    )
                                },
                                highlight = { Highlight.Default }
                            )
                    ) { controlContent() }
                } else if (useBlurCallScreen && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Surface(
                        shape           = controlShape,
                        color           = bottomBarColor.copy(alpha = 0.72f),
                        shadowElevation = 0.dp,
                        tonalElevation  = 0.dp,
                        modifier        = Modifier
                            .fillMaxWidth()
                            .drawPlainBackdrop(
                                backdrop = globalBackdrop,
                                shape = { controlShape },
                                effects = { blur(blurIntensity * density) }
                            )
                    ) { controlContent() }
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(controlShape),
                        color = bottomBarColor
                    ) {
                        controlContent()
                    }
                }
            } else {
                val useCustomUI = preferenceManager.getInt(
                    PreferenceManager.KEY_INCOMING_CALL_UI_MODE,
                    PreferenceManager.DEFAULT_INCOMING_CALL_UI_MODE,
                )
                val answerOnStart = preferenceManager.getString(
                    PreferenceManager.KEY_INCOMING_ANSWER_PLACEMENT,
                    PreferenceManager.DEFAULT_INCOMING_ANSWER_PLACEMENT,
                ) != PreferenceManager.INCOMING_ANSWER_PLACEMENT_RIGHT_BOTTOM
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("callIncomingControls")
                        .onGloballyPositioned { coordinates ->
                            val measuredTop = coordinates.positionInRoot().y
                            if (!actionControlsTopPx.isFinite() ||
                                abs(actionControlsTopPx - measuredTop) > 0.5f
                            ) {
                                actionControlsTopPx = measuredTop
                            }
                        }
                        .navigationBarsPadding()
                        .padding(
                            start = CALL_ACTION_CONTROLS_INSET_DP.dp,
                            end = CALL_ACTION_CONTROLS_INSET_DP.dp,
                            bottom = screenSpacing.controlsBottomPaddingDp.dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(
                        screenSpacing.actionRowsGapDp.dp,
                    ),
                ) {
                    val secondaryCount = if (isUnknownCaller) 3 else 2
                    val secondaryActions: @Composable () -> Unit = {
                        IncomingCallSecondaryActionCells(
                        useLg = useLgCallScreen,
                        useBlur = useBlurCallScreen,
                        blurIntensity = blurIntensity,
                        showMore = isUnknownCaller,
                        onMessage = {
                            showQuickResponsesSheet = true
                        },
                        onRemind = {
                            showReminderSheet = true
                        },
                        onMore = {
                            showUnknownCallerActions = true
                        },
                    )
                    }
                    IncomingCallResponseControls(
                        incomingMode = useCustomUI,
                        answerOnStart = answerOnStart,
                        useLg = useLgCallScreen,
                        useBlur = useBlurCallScreen,
                        blurIntensity = blurIntensity,
                        secondaryCount = secondaryCount,
                        secondaryActions = secondaryActions,
                        rowGap = screenSpacing.actionRowsGapDp.dp,
                        actionGap = screenSpacing.hangupGapDp.dp,
                        onAnswer = {
                            val action = if (otherCall != null) ::holdExistingAndAnswer else ::answerAudioCall
                            if (callBiometricUnlocked) action() else {
                                pendingAction = action
                                showCallBiometricUnlock = true
                            }
                        },
                        onDecline = {
                            val action = if (otherCall != null) {
                                ::declineIncomingAndResumeExisting
                            } else {
                                { callDisconnect(true) }
                            }
                            if (callBiometricUnlocked) action() else {
                                pendingAction = action
                                showCallBiometricUnlock = true
                            }
                        },
                    )
                }
            }
        }

        if (isPocketModeCovered && !pocketModeDismissedManually && callState == Call.STATE_RINGING) {
            PocketModeOverlay(
                onDismiss = { pocketModeDismissedManually = true }
            )
        }

        if (showQuickResponsesSheet) {
            QuickResponsesBottomSheet(
                phoneNumber = phoneNumber,
                contactName = contactName,
                onDismiss = { showQuickResponsesSheet = false },
                onSend = onSendQuickResponse,
                onOpenSmsApp = {
                    try {
                        if (call.currentTelecomState() == Call.STATE_RINGING) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                call.reject(Call.REJECT_REASON_DECLINED)
                            } else {
                                call.disconnect()
                            }
                        } else {
                            call.disconnect()
                        }
                    } catch (error: Exception) {
                        Log.w(CALL_SCREEN_LOG_TAG, "Unable to end call before opening SMS", error)
                    }
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = "smsto:$phoneNumber".toUri()
                    }
                    context.startActivity(intent)
                    showQuickResponsesSheet = false
                },
            )
        }

        IncomingCallTransientDialogs(
            showReminderSheet = showReminderSheet,
            showUnknownCallerActions = showUnknownCallerActions,
            showBlockAndReportConfirmation = showBlockAndReportConfirmation,
            phoneNumber = phoneNumber,
            call = call,
            context = context,
            onDismissReminder = { showReminderSheet = false },
            onDismissUnknownActions = { showUnknownCallerActions = false },
            onShowBlockConfirmation = {
                showUnknownCallerActions = false
                showBlockAndReportConfirmation = true
            },
            onDismissBlockConfirmation = { showBlockAndReportConfirmation = false },
            onScheduleReminder = ::scheduleCallBackReminder,
            onReject = ::rejectIncomingCall,
            onDisconnect = { callDisconnect(true) },
        )
    }

    if (showCallConnectionDetails && callConnectionInfo != null) {
        CallConnectionDetailsSheet(
            info = callConnectionInfo,
            onDismiss = { showCallConnectionDetails = false },
        )
    }

    if (showCallBiometricUnlock) {
        val biometricType = preferenceManager.getString(PreferenceManager.KEY_BIOMETRICS_TYPE, "") ?: ""
        val callActivity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
        fun onBiometricFail() {
            showCallBiometricUnlock = false
            pendingAction = null
        }
        when (biometricType) {
            "system" -> {
                val activity = callActivity ?: run { onBiometricFail(); return }
                dev.goodwy.rphone.view.components.AuthenticationSystemPrompt(
                    activity = activity,
                    title = stringResource(R.string.verify_your_identity_to_access_call),
                    onSuccess = {
                        callBiometricUnlocked = true
                        biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onError = { _, _ -> onBiometricFail()}
                )
            }
            "pin" -> {
                PinSetupDialog(
                    title = stringResource(R.string.enter_pin), isVerify = true,
                    verifySecret = { preferenceManager.verifyAuthentication("pin", it) },
                    showCloseButton = !biometricGatesScreen,
                    onConfirm = {
                        callBiometricUnlocked = true; biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onDismiss = { onBiometricFail() }
                )
            }
            "password" -> {
                PasswordSetupDialog(
                    title = stringResource(R.string.enter_password), isVerify = true,
                    verifySecret = { preferenceManager.verifyAuthentication("password", it) },
                    showCloseButton = !biometricGatesScreen,
                    onConfirm = {
                        callBiometricUnlocked = true; biometricGatesScreen = false
                        showCallBiometricUnlock = false
                        pendingAction?.invoke(); pendingAction = null
                    },
                    onDismiss = { onBiometricFail() }
                )
            }
        }
    }
}

