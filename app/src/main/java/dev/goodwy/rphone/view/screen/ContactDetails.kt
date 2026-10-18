package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.modal.data.ContactSource
import dev.goodwy.rphone.view.theme.accentText

import dev.goodwy.rphone.view.components.DotDialerHandsetIcon
import dev.goodwy.rphone.controller.util.currentDisplayRotation
import dev.goodwy.rphone.controller.util.parcelableExtra
import android.accounts.Account
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract
import android.view.Surface
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.goodwy.rphone.controller.CallLogViewModel
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.NoteManager
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.view.components.*
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.NavController
import dev.goodwy.rphone.R
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinActivityViewModel
import org.koin.compose.koinInject
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.controller.util.copyToClipboard
import dev.goodwy.rphone.controller.util.getAddressTypeText
import dev.goodwy.rphone.controller.util.getEmailTypeText
import dev.goodwy.rphone.controller.util.getEventTypeText
import dev.goodwy.rphone.controller.util.getCallingAccounts
import dev.goodwy.rphone.controller.util.shareContactsAsVCard
import dev.goodwy.rphone.controller.util.isPackageInstalled
import dev.goodwy.rphone.controller.util.normalizePhoneNumber
import dev.goodwy.rphone.controller.util.toast
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.ContactDetailsTransitionStyle
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.generated.destinations.CallLogFullScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import dev.goodwy.rphone.controller.util.BlockedNumbersManager
import dev.goodwy.rphone.controller.util.CallBackgroundStore
import dev.goodwy.rphone.controller.util.ContactShortcutUtils
import dev.goodwy.rphone.controller.util.retainDocumentReadAccess
import dev.goodwy.rphone.controller.util.ringtoneTitleOrFallback
import dev.goodwy.rphone.controller.util.SocialUtils
import dev.goodwy.rphone.controller.util.SocialUtils.launchSendWhatsAppIntent
import dev.goodwy.rphone.controller.util.areNumbersEqual
import dev.goodwy.rphone.controller.util.forceLtr
import dev.goodwy.rphone.controller.util.hasDualSim
import dev.goodwy.rphone.device_only
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.private_only
import java.util.Calendar

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>(style = ContactDetailsTransitionStyle::class)
@Composable
fun ContactDetailsScreen(
    contactId: String? = null,
    phoneNumber: String? = null,
    navController: NavController,
    navigator: DestinationsNavigator
) {
    val context = LocalContext.current
    val contactsViewModel: ContactsViewModel = koinActivityViewModel()
    val contactRevision by contactsViewModel.contactRevision.collectAsStateWithLifecycle()
    var resumeRevision by remember { mutableIntStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        resumeRevision++
    }
    val callLogViewModel: CallLogViewModel = koinActivityViewModel()

    val allLogs by callLogViewModel.allCallLogs.collectAsStateWithLifecycle()
    val availableAccounts = contactsViewModel.availableAccounts.collectAsStateWithLifecycle().value
    val availableAccountsForMoving = contactsViewModel.availableAccountsForMoving.collectAsStateWithLifecycle().value

    var contact by remember(contactId, phoneNumber) { mutableStateOf<Contact?>(null) }
    var contactAccount by remember(contactId, phoneNumber) { mutableStateOf<Account?>(null) }
    var isFullLoading by remember(contactId, phoneNumber) { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    fun navigateBack() {
        navigator.navigateUp()
    }

    val noContactsFound = stringResource(R.string.no_contacts_found)
    LaunchedEffect(contactId, phoneNumber, contactRevision, resumeRevision) {
        // Initial loading belongs to this route; refresh keeps the existing body mounted.
        contact = if (contactId != null && contactId != "null") {
            contactsViewModel.getFullContactById(contactsViewModel.editedContactId(contactId))
        } else if (phoneNumber != null) {
            contactsViewModel.getFullContactByNumber(phoneNumber)
        } else null
        val loadedContact = contact
        contactAccount = loadedContact?.let { currentContact ->
            availableAccounts.find {
                it.name == currentContact.accountName && it.type == currentContact.accountType
            }
        }

        if (contact == null && contactId != null && contactId != "null") {
            if (phoneNumber != null) {
                isFullLoading = false
                return@LaunchedEffect
            } else {
                context.toast(noContactsFound)
                isFullLoading = false
                navigateBack()
                return@LaunchedEffect
            }
        }
        isFullLoading = false
    }

    val unknownLabel = stringResource(R.string.label_unknown)
    val displayPhone = phoneNumber
        ?: contact?.phoneDetails?.firstOrNull { it.isPrimary }?.number
        ?: contact?.phoneNumbers?.firstOrNull()
        ?: unknownLabel

    val companyAndJob = contact.companyAndJobLabel()

    var contactSources by remember { mutableStateOf<List<ContactSource>>(emptyList()) }
    var phoneSourceData by remember { mutableStateOf<List<ContactPhoneSourceData>>(emptyList()) }
    LaunchedEffect(contact) {
        val currentContact = contact
        if (currentContact != null && !currentContact.isPrivate) {
            val rawContacts = contactsViewModel.getContactSources(currentContact.id)
            contactSources = rawContacts
            phoneSourceData = loadPhoneSourceData(contactsViewModel, rawContacts)
        } else {
            contactSources = emptyList()
            phoneSourceData = emptyList()
        }
    }

    val phoneRows = remember(contact?.phoneDetails, phoneSourceData) {
        buildContactPhoneRows(contact?.phoneDetails.orEmpty(), phoneSourceData)
    }

    val defaultPhone = remember(contact) { derivedStateOf { contact?.phoneDetails?.firstOrNull { it.isPrimary } } }.value

    fun updateDefaultPhone(phoneNumber: String, isPrimary: Boolean) {
        val currentContact = contact ?: return
        contactsViewModel.setDefaultPhoneNumber(
            contactId = currentContact.id,
            phoneNumber = phoneNumber,
            isPrimary = isPrimary
        )
        contact = currentContact.withPrimaryPhone(phoneNumber, isPrimary)
    }

    fun editPhoneContact(currentContact: Contact) {
        scope.launch {
            val rawContactId = contactsViewModel.getPreferredEditableRawContactId(currentContact)
            navController.navigate(
                ContactEditScreenDestination(
                    contactId = currentContact.id,
                    rawContactId = rawContactId,
                    isEditingSource = rawContactId != null,
                ).route
            ) {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val prefs = koinInject<PreferenceManager>()
    val expandedListDetails = rememberExpandedListDetails(prefs)
    val simPref = remember { prefs.getInt(PreferenceManager.KEY_DEFAULT_SIM, prefs.getDefaultSimIndexDefault()) }
    val displayOrder by remember {
        mutableIntStateOf(prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0))
    }
    val displayName = contact?.let { getDisplayName(it, displayOrder) }
        ?: phoneNumber?.forceLtr()
        ?: unknownLabel

    var showSimPicker by remember { mutableStateOf(false) }
    var showNumberPicker by remember { mutableStateOf(false) }
    var showMessagePicker by remember { mutableStateOf(false) }
    var showEmailPicker by remember { mutableStateOf(false) }
    var showAddCallNotePicker by remember { mutableStateOf(false) }
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showQrDialogPicker by remember { mutableStateOf(false) }
    var pendingQrNumber by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showShortcutNumberPicker by remember { mutableStateOf(false) }
    var showShortcutActionPicker by remember { mutableStateOf(false) }
    var pendingShortcutNumber by remember { mutableStateOf<String?>(null) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showSharePicker by remember { mutableStateOf(false) }
    var showSourcesDialog by remember { mutableStateOf(false) }
    var showCallingAccountDialog by remember { mutableStateOf(false) }

    var callBackground by remember { mutableStateOf<String?>(null) }
    var backgroundSaving by remember { mutableStateOf(false) }
    var showBackgroundDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    var blockedVersion by remember { mutableIntStateOf(0) }
    val knownNumbers = remember(contact, phoneNumber) {
        ((contact?.phoneNumbers ?: emptyList()) + listOfNotNull(phoneNumber)).distinct()
    }
    val blockedNumbers = remember(knownNumbers, blockedVersion) {
        knownNumbers.filter { BlockedNumbersManager.isBlocked(context, it) }.toSet()
    }
    val isNumberBlocked: (String) -> Boolean = { number ->
        blockedNumbers.any { areNumbersEqual(it, number) }
    }

    val backgroundContactId = contact?.id?.takeIf { it.isNotBlank() }
    val backgroundNumbers = knownNumbers
    val backgroundKeys = remember(backgroundNumbers) {
        CallBackgroundStore.numberKeys(backgroundNumbers)
    }
    val backgroundAvailable = backgroundContactId != null || backgroundKeys.isNotEmpty()
    val backgroundErrorMessage = stringResource(R.string.contact_call_background_error)
    val backgroundNoTargetMessage = stringResource(R.string.contact_call_background_no_target)

    LaunchedEffect(backgroundContactId, backgroundNumbers) {
        callBackground = CallBackgroundStore.peek(context, backgroundContactId, backgroundNumbers)
    }

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            retainDocumentReadAccess(context, uri)
            scope.launch {
                backgroundSaving = true
                val saved = CallBackgroundStore.save(
                    context,
                    backgroundContactId,
                    backgroundNumbers,
                    uri
                )
                backgroundSaving = false
                if (saved) {
                    callBackground = CallBackgroundStore.peek(context, backgroundContactId, backgroundNumbers)
                } else {
                    snackbarHostState.showSnackbar(backgroundErrorMessage)
                }
            }
        }
    }

    val onBackgroundClick: () -> Unit = {
        when {
            !backgroundAvailable -> {
                scope.launch { snackbarHostState.showSnackbar(backgroundNoTargetMessage) }
            }
            callBackground != null -> showBackgroundDialog = true
            else -> backgroundPickerLauncher.launch(arrayOf("image/*"))
        }
    }

    val contactLogs = remember(contact, phoneNumber, allLogs) {
        allLogs.matchingContactDetailsLogs(contact, phoneNumber)
    }

    val isFavorite = contact?.isFavorite ?: false
    val listState = rememberLazyListState()

    var showPhoto by remember { mutableStateOf(false) }
    if (showPhoto && !contact?.photoUri.isNullOrBlank()) {
        dev.goodwy.rphone.view.components.ContactPhotoViewer(
            photoUri = requireNotNull(contact?.photoUri), title = displayName, onDismiss = { showPhoto = false })
    }
    BackHandler(enabled = !showPhoto) { navigateBack() }

    val initiateCall = { number: String ->
        val contactKey = contact?.id
        val contactAccount = contactKey?.let(prefs::getFavoriteSim)
            ?: prefs.getFavoriteSimForNumber(number)
        val appAccount = prefs.getString(PreferenceManager.KEY_DEFAULT_CALLING_ACCOUNT, null)
        val accounts = getCallingAccounts(context)
        if (accounts.size > 1 && contactAccount == null && appAccount == null && simPref == 0) {
            pendingNumber = number
            showSimPicker = true
        } else {
            makeCall(context, number, contactId = contactKey)
        }
    }

    val initiateMessage = { number: String ->
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                "sms:$number".toUri()
            )
        )
    }

    val initiateEmail = { email: String ->
        context.startActivity(Intent(Intent.ACTION_SENDTO,
            "mailto:$email".toUri()))
    }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = result.data?.parcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            contact?.let { currentContact ->
                contactsViewModel.setCustomRingtone(currentContact.id, uri?.toString())
                contact = currentContact.copy(customRingtone = uri?.toString())
            }
        }
    }

    val showSimLabel = hasDualSim(context)

    val openWhatsApp = { num: String -> SocialUtils.openWhatsApp(context, num) }
    val openTelegram = { num: String -> SocialUtils.openTelegram(context, num) }
    val openSignal = { num: String -> SocialUtils.openSignal(context, num) }

    val shareText = stringResource(R.string.share)
    val nameText = stringResource(R.string.name)
    val phoneNumberText = stringResource(R.string.phone_number)
    val shareContact = { number: String ->
        val currentContact = contact
        if (currentContact != null) {
            shareContactsAsVCard(context, listOf(currentContact), shareText)
        } else {
            val name = if (displayName != number) "$nameText: $displayName\n" else ""
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "$name$phoneNumberText: $number")
            }
            context.startActivity(Intent.createChooser(intent, shareText))
        }
    }

    val currentContact = contact
    val contactNotes = currentContact?.notes
    if (showSharePicker && currentContact != null) {
        NumberPickerDialog(numbers = currentContact.phoneNumbers, onDismissRequest = { showSharePicker = false }, onNumberSelected = { showSharePicker = false; shareContact(it) })
    }
    if (showNumberPicker && currentContact != null) {
        NumberPickerDialog(numbers = currentContact.phoneNumbers, onDismissRequest = { showNumberPicker = false }, onNumberSelected = { showNumberPicker = false; initiateCall(it) })
    }
    if (showMessagePicker && currentContact != null) {
        NumberPickerDialog(numbers = currentContact.phoneNumbers, onDismissRequest = { showMessagePicker = false }, onNumberSelected = { showMessagePicker = false; initiateMessage(it) })
    }
    if (showEmailPicker && currentContact != null) {
        NumberPickerDialog(numbers = currentContact.emails.map { it.value }, onDismissRequest = { showEmailPicker = false }, onNumberSelected = { showEmailPicker = false; initiateEmail(it) }, icon = Icons.Rounded.Email)
    }
    val currentPendingNumber = pendingNumber
    if (showSimPicker && currentPendingNumber != null) {
        SimPickerDialog(onDismissRequest = { showSimPicker = false }, onSimSelected = { handle -> makeCall(context, currentPendingNumber, handle, contact?.id); showSimPicker = false })
    }
    if (showCallingAccountDialog && currentContact != null) {
        CallingAccountPreferenceDialog(
            selectedKey = prefs.getFavoriteSim(currentContact.id),
            onSelected = { key ->
                prefs.setFavoriteSim(currentContact.id, key)
                currentContact.phoneNumbers.forEach { prefs.setFavoriteSimForNumber(it, key) }
                showCallingAccountDialog = false
            },
            onDismissRequest = { showCallingAccountDialog = false },
        )
    }
    if (showQrDialog) {
        QrCodeDialog(name = displayName, phone = pendingQrNumber ?: displayPhone, email = contact?.emails?.firstOrNull()?.value, onDismiss = { showQrDialog = false })
    }
    if (showQrDialogPicker && currentContact != null) {
        NumberPickerDialog(
            numbers = currentContact.phoneNumbers,
            onDismissRequest = { showQrDialogPicker = false },
            onNumberSelected = { pendingQrNumber = it; showQrDialogPicker = false; showQrDialog = true }
        )
    }

    val shortcutNumbers = remember(contact, phoneNumber) {
        contact?.phoneNumbers?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(phoneNumber?.takeIf { it.isNotBlank() && it != "Unknown" })
    }
    if (showShortcutNumberPicker) {
        NumberPickerDialog(
            numbers = shortcutNumbers,
            onDismissRequest = { showShortcutNumberPicker = false },
            onNumberSelected = { number ->
                showShortcutNumberPicker = false
                pendingShortcutNumber = number
                showShortcutActionPicker = true
            }
        )
    }
    val currentShortcutNumber = pendingShortcutNumber
    if (showShortcutActionPicker && currentShortcutNumber != null) {
        val shortcutKeyId = currentContact?.id ?: currentShortcutNumber
        val toast = stringResource(R.string.shortcut_save_number)
        ShortcutActionDialog(
            onOpenContactInfo = {
                showShortcutActionPicker = false
                if (currentContact != null) {
                    ContactShortcutUtils.pinOpenContactShortcut(
                        context, currentContact.id, displayName, currentContact.photoUri
                    )
                } else {
                    context.toast(toast)
                }
            },
            onCallDirectly = {
                showShortcutActionPicker = false
                ContactShortcutUtils.pinCallShortcut(
                    context, shortcutKeyId, displayName, currentShortcutNumber, currentContact?.photoUri
                )
            },
            onDismiss = { showShortcutActionPicker = false }
        )
    }

    if (showDeleteDialog) {
        RillDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.delete_contact),
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    val targetId = contact?.id?.takeIf { it.isNotBlank() } ?: contactId
                    if (targetId != null) {
                        contactsViewModel.deleteContact(targetId)
                        navigator.navigateUp()
                    }
                }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.cancel))
                }
            }
        ) {
            Text(
                stringResource(R.string.delete_contact_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showBackgroundDialog) {
        CallBackgroundBottomSheet(
            callBackground = callBackground,
            tempCallBackground = null,
            tempBackgroundDeleted = false,
            onDismiss = { showBackgroundDialog = false },
            onChange = {
                backgroundPickerLauncher.launch(arrayOf("image/*"))
            },
            onRemove = {
                scope.launch {
                    CallBackgroundStore.clear(
                        context,
                        backgroundContactId,
                        backgroundNumbers
                    )
                    callBackground = null
                    showBackgroundDialog = false
                }
            },
            onReset = {
                // No need to refresh, as changes are saved immediately
                showBackgroundDialog = false
            }
        )
    }

    var editingNoteNumber by remember { mutableStateOf<String?>(null) }
    val currentEditingNoteNumber = editingNoteNumber
    if (showNoteEditor && currentEditingNoteNumber != null) {
        NoteEditorDialog(
            contactName = displayName,
            phoneNumber = currentEditingNoteNumber,
            onDismiss = {
                showNoteEditor = false
                editingNoteNumber = null
            }
        )
    }

    val initiateAddCallNote = { number: String ->
        if (number != unknownLabel) {
            editingNoteNumber = number
            showNoteEditor = true
        }
    }

    if (showAddCallNotePicker && currentContact != null) {
        NumberPickerDialog(numbers = currentContact.phoneNumbers, onDismissRequest = { showAddCallNotePicker = false }, onNumberSelected = { showAddCallNotePicker = false; initiateAddCallNote(it) })
    }

    if (showSourcesDialog && currentContact != null) {
        SourcesDialog(
            contact = currentContact,
            sources = contactSources,
            navigator = navigator,
            navigateBack = { navigateBack() },
            onDismiss = { showSourcesDialog = false }
        )
    }

    // We track when the title should be displayed
    val showTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex >= 1
        }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "titleAlpha"
    )

    val rotation = context.currentDisplayRotation()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = titleAlpha }
                    ) {
                        Title(displayName, false)
                    }
                },
                navigationIcon = { NavigationIcon(onClick = { navigateBack() }) },
                actions = {
                    if (currentContact != null) {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val cornerRadius by animateDpAsState(
                            if (isPressed) 8.dp else 40.dp,
                            spring(stiffness = Spring.StiffnessMediumLow),
                            label = "ButtonShapeAnimation"
                        )
                        IconButton(
                            onClick = {
                                contact?.let { thisContact ->
                                    val newFavorite = !thisContact.isFavorite
                                    contact = thisContact.copy(isFavorite = newFavorite)
                                    contactsViewModel.toggleFavorite(thisContact)
                                }
                            },
                            interactionSource = interactionSource,
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(cornerRadius),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                containerColor = Color.Transparent,
                            )
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Star else Icons.Default.StarOutline,
                                stringResource(if (isFavorite) R.string.remove_from_favorites else R.string.add_to_favorites))
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        IconButton(onClick = { editPhoneContact(currentContact) }) {
                            Icon(Icons.Outlined.Edit, stringResource(R.string.edit))
                        }
                    } else if (phoneNumber != null && phoneNumber != unknownLabel) {
                        IconButton(onClick = {
                            navigator.navigate(ContactEditScreenDestination(initialPhone = phoneNumber))
                        }) { Icon(Icons.Rounded.PersonAdd, stringResource(R.string.add_contact)) }
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(
                top = innerPadding.calculateTopPadding(),
                start = 0.dp,
                end = 0.dp,
                bottom = 0.dp
            )
            .fillMaxSize()
        ) {
            if (isFullLoading) {
                RillLoadingIndicatorView()
            } else {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (isRotation90) Modifier.navigationBarsPadding()
                                    else Modifier
                                )
                                .padding(bottom = 24.dp)
                                .fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            RillAvatar(
                                name = contact?.displayName ?: displayName,
                                colorSeed = contact?.displayName ?: displayName,
                                photoUri = contact?.photoUri,
                                modifier = Modifier.size(180.dp).clickable(enabled = !contact?.photoUri.isNullOrBlank()) { showPhoto = true },
                                shape = CircleShape
                            )
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1.7f),
                            contentPadding = PaddingValues(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                    if (contact?.nickname != "") {
                                        contact?.nickname?.let {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.bodyMedium,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                    if (companyAndJob != "") {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        ContactCompanyColleagues(contact?.company, contact?.id, companyAndJob) {
                                            navigator.navigate(com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination(contactId = it.id))
                                        }
                                    }
                                }
                            }

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    RillExpressiveButton(
                                        modifier = Modifier.weight(1f),
                                        icon = DotDialerHandsetIcon,
                                        label = stringResource(R.string.call),
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        enabled = currentContact?.phoneNumbers?.isNotEmpty() == true || displayPhone != unknownLabel,
                                        onClick = {
                                            if (defaultPhone != null) initiateCall(
                                                defaultPhone.number
                                            )
                                            else if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showNumberPicker =
                                                true
                                            else if (displayPhone != unknownLabel) initiateCall(displayPhone)
                                        })
                                    val messageImageVector: ImageVector =
                                        ImageVector.vectorResource(id = R.drawable.ic_message_filled)
                                    RillExpressiveButton(
                                        modifier = Modifier.weight(1f),
                                        icon = messageImageVector,
                                        label = stringResource(R.string.message),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        enabled = currentContact?.phoneNumbers?.isNotEmpty() == true || displayPhone != unknownLabel,
                                        onClick = {
                                            if (defaultPhone != null) initiateMessage(
                                                defaultPhone.number
                                            )
                                            else if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showMessagePicker =
                                                true
                                            else if (displayPhone != unknownLabel) initiateMessage(displayPhone)
                                        })
                                    RillExpressiveButton(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Rounded.Email,
                                        label = stringResource(R.string.email),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        enabled = currentContact?.emails?.isNotEmpty() == true,
                                        onClick = {
                                            if ((currentContact?.emails?.size ?: 0) > 1) showEmailPicker =
                                                true
                                            else currentContact?.emails?.firstOrNull()?.let { email ->
                                                initiateEmail(email.value)
                                            }
                                        })
                                }
                            }

                            item {
                                ContactDetailsCategoryCard(title = stringResource(R.string.contact_info)) {
                                    val configuration = LocalConfiguration.current
                                    val screenWidth = configuration.screenWidthDp.dp
                                    val middleOfScreen = screenWidth / 3
                                    val offsetMenu = DpOffset(middleOfScreen, (-24).dp)
                                    if (currentContact != null) {
                                        val messageImageVector: ImageVector =
                                            ImageVector.vectorResource(id = R.drawable.ic_message_outline)
                                        val numberSize = phoneRows.size
                                        phoneRows.forEach { phoneRow ->
                                            val phoneDetail = phoneRow.detail
                                            val isDefault = defaultPhone != null && areNumbersEqual(phoneDetail.number, defaultPhone.number)
                                            val isRecent = normalizePhoneNumber(phoneDetail.number) == phoneNumber && numberSize > 1
                                            val interactionSource = remember { MutableInteractionSource() }
                                            Box {
                                                var showOverflowMenu by remember { mutableStateOf(false) }
                                                RillListItem(
                                                    headline = phoneDetail.number.forceLtr(),
                                                    expandText = expandedListDetails,
                                                    supportingContent = {
                                                        ContactPhoneMetadata(
                                                            context = context,
                                                            phoneRow = phoneRow,
                                                            isDefault = isDefault,
                                                            isRecent = isRecent,
                                                        )
                                                    },
                                                    leadingIcon = if (isDefault) ImageVector.vectorResource(id = R.drawable.ic_phone_star) else DotDialerHandsetIcon,
                                                    iconContainerColor =
                                                        if (isDefault) MaterialTheme.colorScheme.primary else null,
                                                    trailingIcon = messageImageVector,
                                                    modifierTrailingIcon = Modifier
                                                        .padding(end = 8.dp)
                                                        .size(24.dp)
                                                        .combinedClickable(
                                                            interactionSource = interactionSource,
                                                            indication = ripple(
                                                                bounded = false,
                                                                radius = 24.dp
                                                            ),
                                                            onClick = { initiateMessage(phoneDetail.number) }),
                                                    onClick = { initiateCall(phoneDetail.number) },
                                                    onLongClick = { showOverflowMenu = true }
                                                )
                                                // Dropdown menu
                                                DropdownMenu(
                                                    shape = MaterialTheme.shapes.large,
                                                    expanded = showOverflowMenu,
                                                    onDismissRequest = { showOverflowMenu = false },
                                                    offset = offsetMenu,
                                                ) {
                                                    DropdownMenuItem(
                                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                                        text = {
                                                            Text(
                                                                phoneDetail.number.forceLtr(),
                                                                style = MaterialTheme.typography.bodyLarge,
                                                                color = MaterialTheme.colorScheme.onSurface,
                                                            )
                                                        },
                                                        onClick = { },
                                                        enabled = false
                                                    )
                                                    val numberBlocked = isNumberBlocked(phoneDetail.number)
                                                    DropdownMenuItem(
                                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                                        text = { Text(if (numberBlocked) stringResource(R.string.action_unblock_number) else stringResource(R.string.action_block_number)) },
                                                        onClick = {
                                                            showOverflowMenu = false
                                                            if (numberBlocked) {
                                                                BlockedNumbersManager.unblock(context, phoneDetail.number)
                                                            } else {
                                                                BlockedNumbersManager.block(context, phoneDetail.number)
                                                            }
                                                            blockedVersion++
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                                        text = { Text(stringResource(R.string.add_note)) },
                                                        onClick = {
                                                            showOverflowMenu = false
                                                            editingNoteNumber = phoneDetail.number
                                                            showNoteEditor = true
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        contentPadding = PaddingValues(horizontal = 24.dp),
                                                        text = { Text(stringResource(R.string.copy_action)) },
                                                        onClick = {
                                                            showOverflowMenu = false
                                                            context.copyToClipboard(phoneDetail.number)
                                                        }
                                                    )
                                                    if (numberSize > 1) {
                                                        if (phoneDetail.number == defaultPhone?.number) {
                                                            val textToast = stringResource(R.string.default_phone_number_cleared)
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                                text = { Text(stringResource(R.string.clear_default)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    updateDefaultPhone(phoneDetail.number, false)
                                                                    context.toast(textToast)
                                                                }
                                                            )
                                                        } else {
                                                            val message = stringResource(
                                                                R.string.default_phone_set,
                                                                phoneDetail.number.forceLtr()
                                                            )
                                                            DropdownMenuItem(
                                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                                text = { Text(stringResource(R.string.set_as_default)) },
                                                                onClick = {
                                                                    showOverflowMenu = false
                                                                    updateDefaultPhone(phoneDetail.number, true)
                                                                    context.toast(message)
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        currentContact.emails.forEachIndexed { index, email ->
                                            val label = getEmailTypeText(context, email.type, email.label)
                                            RillListItem(
                                                headline = email.value,
                                                supporting = label,
                                                leadingIcon = Icons.Rounded.Email,
                                                onClick = { initiateEmail(email.value) },
                                                onLongClick = { context.copyToClipboard(email.value) }
                                            )
                                        }

                                        if (currentContact.phoneDetails.isEmpty() && currentContact.emails.isEmpty()) {
                                            RillListItem(
                                                headline = stringResource(R.string.add_phone),
                                                leadingIcon = Icons.Rounded.Add,
                                                onClick = { editPhoneContact(currentContact) }
                                            )
                                        }
                                    } else if (phoneNumber != null && phoneNumber != unknownLabel) {
                                        Box {
                                            var showOverflowMenu by remember { mutableStateOf(false) }
                                            RillListItem(
                                                headline = phoneNumber.forceLtr(),
                                                leadingIcon = DotDialerHandsetIcon,
                                                onClick = { initiateCall(phoneNumber) },
                                                onLongClick = { showOverflowMenu = true }
                                            )
                                            // Dropdown menu
                                            DropdownMenu(
                                                shape = MaterialTheme.shapes.large,
                                                expanded = showOverflowMenu,
                                                onDismissRequest = { showOverflowMenu = false },
                                                offset = offsetMenu,
                                            ) {
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    text = {
                                                        Text(
                                                            phoneNumber.forceLtr(),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    },
                                                    onClick = { },
                                                    enabled = false
                                                )
                                                val numberBlocked = isNumberBlocked(phoneNumber)
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    text = { Text(if (numberBlocked) stringResource(R.string.action_unblock_number) else stringResource(R.string.action_block_number)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        if (numberBlocked) {
                                                            BlockedNumbersManager.unblock(context, phoneNumber)
                                                        } else {
                                                            BlockedNumbersManager.block(context, phoneNumber)
                                                        }
                                                        blockedVersion++
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    text = { Text(stringResource(R.string.copy_action)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        context.copyToClipboard(phoneNumber)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Call Notes section (between Contact Info and Recent Activity)
                            item {
                                var refreshTrigger by remember { mutableIntStateOf(0) }

                                // Get a list of all the contact's phone numbers
                                val phoneNumbersList = remember(contact) {
                                    contact?.phoneDetails?.map { it.number }
                                        ?: if (phoneNumber != null && phoneNumber != unknownLabel) {
                                            listOf(phoneNumber)
                                        } else {
                                            emptyList()
                                        }
                                }

                                // Get all the notes for all issues
                                val allNotes = remember(contact, refreshTrigger) {
                                    if (phoneNumbersList.isNotEmpty()) {
                                        NoteManager.getAllNotesForNumbers(context, phoneNumbersList)
                                    } else {
                                        emptyMap()
                                    }
                                }
                                // Refresh after closing the editor
                                LaunchedEffect(showNoteEditor) {
                                    if (!showNoteEditor) {
                                        refreshTrigger++
                                    }
                                }

                                ContactNotesCategoryCard {
                                    if (allNotes.isEmpty()) {
                                        // No notes—display the "Add" button
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    color = cardColor,
                                                    shape = MaterialTheme.shapes.extraLarge.copy(
                                                        topStart = CornerSize(cardCornerExtraSmall),
                                                        topEnd = CornerSize(cardCornerExtraSmall)
                                                    )
                                                )
                                                .combinedClickable(
                                                    onClick = {
                                                        if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showAddCallNotePicker =
                                                            true
                                                        else if (displayPhone != unknownLabel) initiateAddCallNote(
                                                            displayPhone
                                                        )
                                                    }
                                                )
                                                .padding(vertical = 16.dp, horizontal = 24.dp)
                                        ) {
                                            Text(
                                                text = "+ " + stringResource(R.string.add_note),
                                                style = MaterialTheme.typography.bodyMedium.copy(
                                                    color = MaterialTheme.colorScheme.accentText
                                                )
                                            )
                                        }
                                    } else {
                                        // Here is a list of all notes
                                        allNotes.forEach { (number, noteContent) ->
                                            RillListItem(
                                                headline = number,
                                                supporting = noteContent,
                                                leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                                onClick = {
                                                    editingNoteNumber = number
                                                    showNoteEditor = true
                                                },
                                                onLongClick = {
                                                    if (noteContent.isNotBlank()) {
                                                        val clipboard =
                                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(
                                                            ClipData.newPlainText("Phone number", noteContent)
                                                        )
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            if (contactNotes?.isNotBlank() == true) {
                                item {
                                    ContactDetailsCategoryCard(title = stringResource(R.string.notes_contact)) {
                                        RillListItem(
                                            supporting = contactNotes,
                                            leadingIcon = Icons.AutoMirrored.Rounded.StickyNote2,
                                            onClick = {},
                                            onLongClick = {
                                                val clipboard =
                                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(
                                                    ClipData.newPlainText("Phone number", contactNotes)
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            if (currentContact != null && (currentContact.events.isNotEmpty() || currentContact.addresses.isNotEmpty())) {
                                item {
                                    ContactDetailsCategoryCard(
                                        title = stringResource(R.string.other_information),
                                    ) {
                                        currentContact.events.forEachIndexed { index, event ->
                                            val isBirthday =
                                                event.type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                                            val label = getEventTypeText(context, event.type, event.label)
                                            RillListItem(
                                                headline = event.date,
                                                supporting = label,
                                                leadingIcon = if (isBirthday) Icons.Outlined.Cake else Icons.Outlined.Event,
                                                onClick = {
                                                    val dateParts = event.date.split("-")
                                                    if (dateParts.size == 3) {
                                                        val month = dateParts[1].toInt()
                                                        val day = dateParts[2].toInt()

                                                        val currentYear =
                                                            Calendar.getInstance().get(Calendar.YEAR)
                                                        val calendar = Calendar.getInstance().apply {
                                                            set(Calendar.YEAR, currentYear)
                                                            set(
                                                                Calendar.MONTH,
                                                                month - 1
                                                            ) // Month in Calendar: 0 = January
                                                            set(Calendar.DAY_OF_MONTH, day)
                                                            set(Calendar.HOUR_OF_DAY, 12)
                                                            set(Calendar.MINUTE, 0)
                                                            set(Calendar.SECOND, 0)
                                                            set(Calendar.MILLISECOND, 0)
                                                        }
                                                        val uri =
                                                            "content://com.android.calendar/time/${calendar.timeInMillis}".toUri()
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                uri
                                                            )
                                                        )
                                                    }
                                                },
                                                onLongClick = { context.copyToClipboard(event.date) }
                                            )
                                        }
                                        currentContact.addresses.forEachIndexed { index, address ->
                                            val interactionSource = remember { MutableInteractionSource() }
                                            val label =
                                                getAddressTypeText(context, address.type, address.label)
                                            RillListItem(
                                                headline = address.formattedAddress,
                                                supporting = label,
                                                leadingIcon = Icons.Rounded.LocationOn,
                                                trailingIcon = Icons.Outlined.Directions,
                                                modifierTrailingIcon = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(24.dp)
                                                    .combinedClickable(
                                                        interactionSource = interactionSource,
                                                        indication = ripple(
                                                            bounded = false,
                                                            radius = 24.dp
                                                        ),
                                                        onClick = {
                                                            context.startActivity(
                                                                Intent(
                                                                    Intent.ACTION_VIEW,
                                                                    "google.navigation:q=$address".toUri()
                                                                )
                                                            )
                                                        }),
                                                onClick = {
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            "geo:0,0?q=$address".toUri()
                                                        )
                                                    )
                                                },
                                                onLongClick = { context.copyToClipboard(address.formattedAddress) }
                                            )
                                        }
                                    }
                                }
                            }

                            if (contactLogs.isNotEmpty()) {
                                val hasMultipleNumbers = contactLogs.distinctBy { it.number }.size > 1 ||
                                    (currentContact?.phoneNumbers?.size ?: 0) > 1
                                item {
                                    ContactDetailsCategoryCard(
                                        title = stringResource(R.string.recent_activity),
                                    ) {
                                        Column(modifier = Modifier.animateContentSize()) {
                                            contactLogs.take(3).forEachIndexed { index, log ->
                                                CallLogTileSimple(log, showNumber = hasMultipleNumbers, showSimLabel = showSimLabel)
                                                if (index < 2 && index < contactLogs.size - 1) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                }
                                            }
                                            if (contactLogs.size > 3) {
                                                val finalContactId = currentContact?.id ?: contactId?.takeUnless { it == "null" }
                                                val finalPhoneNumber = phoneNumber ?: currentContact?.phoneNumbers?.firstOrNull() ?: contactLogs.firstOrNull()?.number
                                                TextButton(
                                                    onClick = {
                                                        navigator.navigate(CallLogFullScreenDestination(
                                                            contactId = finalContactId,
                                                            phoneNumber = finalPhoneNumber,
                                                            numbersList = currentContact?.phoneNumbers?.toTypedArray()
                                                        ))
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .offset(
                                                            x = 0.dp,
                                                            y = (-2).dp
                                                        ), // We'll make up for the indentation
                                                    colors = ButtonDefaults.textButtonColors()
                                                        .copy(containerColor = cardColor),
                                                    shape = MaterialTheme.shapes.extraLarge.copy(
                                                        topStart = CornerSize(cardCornerExtraSmall),
                                                        topEnd = CornerSize(cardCornerExtraSmall)
                                                    ),
                                                ) {
                                                    Text(stringResource(R.string.show_full_history))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Contact Settings
                            item {
                                ContactDetailsCategoryCard(title = stringResource(R.string.contacts_settings)) {
                                    if (currentContact != null) {
                                        val customRingtoneLabel = stringResource(R.string.ringtone_custom)
                                        val currentRingtone = currentContact.customRingtone?.let { uriStr ->
                                            ringtoneTitleOrFallback(context, uriStr, customRingtoneLabel)
                                        } ?: stringResource(R.string.default_number)
                                        val contactRingtone = stringResource(R.string.contact_ringtone)
                                        RillListItem(
                                            headline = contactRingtone,
                                            supporting = currentRingtone,
                                            leadingIcon = Icons.Rounded.MusicNote,
                                            onClick = {
                                                val intent =
                                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                        putExtra(
                                                            RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                            RingtoneManager.TYPE_RINGTONE
                                                        )
                                                        putExtra(
                                                            RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                            contactRingtone
                                                        )
                                                        putExtra(
                                                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                            currentContact.customRingtone?.toUri()
                                                        )
                                                    }
                                                ringtonePickerLauncher.launch(intent)
                                            }
                                        )
                                        val accountInfos = remember { getCallingAccounts(context) }
                                        val preferredAccountKey = prefs.getFavoriteSim(currentContact.id)
                                        val preferredAccountLabel = accountInfos.firstOrNull {
                                            it.key == preferredAccountKey || it.handle.id == preferredAccountKey
                                        }?.label ?: stringResource(R.string.use_app_default)
                                        RillListItem(
                                            headline = stringResource(R.string.preferred_calling_account),
                                            supporting = preferredAccountLabel,
                                            leadingIcon = Icons.Rounded.SimCard,
                                            onClick = { showCallingAccountDialog = true },
                                        )
                                        CallBackgroundRow(
                                            background = callBackground,
                                            saving = backgroundSaving,
                                            onClick = onBackgroundClick
                                        )
                                    }
                                    val interactionSource = remember { MutableInteractionSource() }
                                    RillListItem(
                                        headline = stringResource(R.string.share),
                                        supporting = stringResource(R.string.share_subtitle),
                                        leadingIcon = Icons.Rounded.Share,
                                        trailingIcon = Icons.Rounded.QrCode2,
                                        modifierTrailingIcon = Modifier
                                            .padding(end = 8.dp)
                                            .size(24.dp)
                                            .combinedClickable(
                                                interactionSource = interactionSource,
                                                indication = ripple(
                                                    bounded = false,
                                                    radius = 24.dp
                                                ),
                                                onClick = {
                                                    if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showQrDialogPicker =
                                                        true
                                                    else {
                                                        pendingQrNumber = null; showQrDialog = true
                                                    }
                                                }
                                            ),
                                        onClick = {
                                            shareContact(displayPhone) }
                                    )
                                    if (currentContact != null || shortcutNumbers.isNotEmpty()) {
                                        RillListItem(
                                            headline = stringResource(R.string.add_to_home_screen),
                                            leadingIcon = Icons.Rounded.Widgets,
                                            onClick = {
                                                if (currentContact != null) {
                                                    if (shortcutNumbers.size > 1) {
                                                        showShortcutNumberPicker = true
                                                    } else if (shortcutNumbers.isEmpty()) {
                                                        ContactShortcutUtils.pinOpenContactShortcut(
                                                            context,
                                                            currentContact.id,
                                                            displayName,
                                                            currentContact.photoUri
                                                        )
                                                    } else {
                                                        pendingShortcutNumber = shortcutNumbers.first()
                                                        showShortcutActionPicker = true
                                                    }
                                                } else if (shortcutNumbers.isNotEmpty()) {
                                                    val shortcutNumber = shortcutNumbers.first()
                                                    ContactShortcutUtils.pinCallShortcut(
                                                        context,
                                                        shortcutNumber,
                                                        displayName,
                                                        shortcutNumber,
                                                        null
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    if (currentContact != null && contactSources.size < 2) {
                                        RillListItem(
                                            headline = stringResource(R.string.move_contact),
                                            supporting = stringResource(R.string.move_to_another_account),
                                            leadingIcon = Icons.AutoMirrored.Rounded.DriveFileMove,
                                            onClick = { showMoveDialog = true }
                                        )
                                    }
                                    val contactNumbers = currentContact?.phoneNumbers ?: listOf(displayPhone)
                                    val contactBlocked = contactNumbers.isNotEmpty() && contactNumbers.all { isNumberBlocked(it) }
                                    RillListItem(
                                        headline = if (contactBlocked) stringResource(R.string.contact_unblock) else stringResource(R.string.contact_block),
                                        supporting = if (contactBlocked) stringResource(R.string.contact_unblock_supporting) else stringResource(R.string.contact_block_supporting),
                                        leadingIcon = if (contactBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                        onClick = {
                                            contactNumbers.forEach { number ->
                                                if (contactBlocked) {
                                                    BlockedNumbersManager.unblock(context, number)
                                                } else {
                                                    BlockedNumbersManager.block(context, number)
                                                }
                                            }
                                            blockedVersion++
                                        }
                                    )
                                    if (currentContact != null) {
                                        RillListItem(
                                            headline = stringResource(R.string.delete),
                                            leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_delete),
                                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                            onClick = { showDeleteDialog = true }
                                        )
                                    }

                                    if (showMoveDialog && currentContact != null) {
                                        val textToast = stringResource(R.string.contact_moved_successfully)
                                        MoveSingleContactDialog(
                                            contact = currentContact,
                                            availableAccounts = availableAccountsForMoving,
                                            currentAccountKey = if (currentContact.accountName == null && currentContact.accountType == null && currentContact.isPrivate) private_only
                                                                else if (currentContact.accountName == null && currentContact.accountType == null) device_only
                                                                else "${currentContact.accountName}|${currentContact.accountType}",
                                            contactsViewModel = contactsViewModel,
                                            onDismiss = { showMoveDialog = false },
                                            onSuccess = { selectedAccount, isPrivate, newContactId ->
                                                context.toast(textToast)
                                                // Updating local data
                                                contact = currentContact.copy(
                                                    id = newContactId,
                                                    accountName = selectedAccount?.name,
                                                    accountType = selectedAccount?.type,
                                                    isPrivate = isPrivate
                                                )
                                                contactAccount = selectedAccount
                                            }
                                        )
                                    }
                                }
                            }

                            // Contact Sources
                            if (currentContact != null && contactSources.size > 1) {
                                item {
                                    ContactDetailsCategoryCard(title = stringResource(R.string.contact_sources)) {
                                        contactSources.forEachIndexed { index, source ->
                                            val account = if (!source.accountName.isNullOrEmpty()) {
                                                Account(source.accountName, source.accountType ?: "")
                                            } else {
                                                null // We do not create accounts with blank names
                                            }
                                            SourceItem(
                                                modifier = Modifier.combinedClickable(
                                                    onClick = { showSourcesDialog = true },
                                                ),
                                                leadingIcon = ContactUtils.getAccountIcon(account, currentContact.isPrivate),
                                                headline = ContactUtils.getAccountName(account),
                                                supporting = ContactUtils.getFriendlyAccountName(account),
                                            )
                                        }
                                    }
                                }
                            } else if (currentContact != null) {
                                item {
                                    ContactDetailsCategoryCard(title = stringResource(R.string.contact_sources)) {
                                        val currentAccount = contactAccount
                                        SourceItem(
                                            modifier = Modifier,
                                            leadingIcon = ContactUtils.getAccountIcon(currentAccount, currentContact.isPrivate),
                                            headline = currentAccount?.let { ContactUtils.getAccountName(it) }
                                                ?: ContactUtils.getFriendlyAccountName(null, currentContact.isPrivate),
                                            supporting = currentAccount?.let { ContactUtils.getFriendlyAccountName(it) },
                                        )
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(100.dp)) }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                                )
                        )
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.wrapContentSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RillAvatar(
                                        name = contact?.displayName ?: displayName,
                                colorSeed = contact?.displayName ?: displayName,
                                        photoUri = contact?.photoUri,
                                        modifier = Modifier.size(180.dp).clickable(enabled = !contact?.photoUri.isNullOrBlank()) { showPhoto = true },
                                        shape = CircleShape
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                if (contact?.nickname != "") {
                                    contact?.nickname?.let {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = it,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                                if (companyAndJob != "") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = companyAndJob,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        stickyHeader {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                RillExpressiveButton(
                                    modifier = Modifier.weight(1f),
                                    icon = DotDialerHandsetIcon,
                                    label = stringResource(R.string.call),
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    enabled = currentContact?.phoneNumbers?.isNotEmpty() == true || displayPhone != unknownLabel,
                                    onClick = {
                                        if (defaultPhone != null) initiateCall(
                                            defaultPhone.number
                                        )
                                        else if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showNumberPicker =
                                            true
                                        else if (displayPhone != unknownLabel) initiateCall(displayPhone)
                                    })
                                val messageImageVector: ImageVector =
                                    ImageVector.vectorResource(id = R.drawable.ic_message_filled)
                                RillExpressiveButton(
                                    modifier = Modifier.weight(1f),
                                    icon = messageImageVector,
                                    label = stringResource(R.string.message),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    enabled = currentContact?.phoneNumbers?.isNotEmpty() == true || displayPhone != unknownLabel,
                                    onClick = {
                                        if (defaultPhone != null) initiateMessage(
                                            defaultPhone.number
                                        )
                                        else if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showMessagePicker =
                                            true
                                        else if (displayPhone != unknownLabel) initiateMessage(displayPhone)
                                    })
                                RillExpressiveButton(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.Email,
                                    label = stringResource(R.string.email),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    enabled = currentContact?.emails?.isNotEmpty() == true,
                                    onClick = {
                                        if ((currentContact?.emails?.size ?: 0) > 1) showEmailPicker =
                                            true
                                        else currentContact?.emails?.firstOrNull()?.let { initiateEmail(it.value) }
                                    })
                            }
                        }

                        // Contact Info
                        item {
                            ContactDetailsCategoryCard(
                                title = stringResource(R.string.contact_info),
                            ) {
                                val configuration = LocalConfiguration.current
                                val screenWidth = configuration.screenWidthDp.dp
                                val middleOfScreen = screenWidth / 3
                                val offsetMenu = DpOffset(middleOfScreen, (-24).dp)
                                if (currentContact != null) {
                                    val messageImageVector: ImageVector =
                                        ImageVector.vectorResource(id = R.drawable.ic_message_outline)
                                    val whatsappImageVector: ImageVector =
                                        ImageVector.vectorResource(id = R.drawable.ic_whatsapp)
                                    val numberSize = phoneRows.size
                                    val whatsappInstall =
                                        context.isPackageInstalled("com.whatsapp") || context.isPackageInstalled(
                                            "com.whatsapp.w4b"
                                        )
                                    phoneRows.forEach { phoneRow ->
                                        val phoneDetail = phoneRow.detail
                                        val isDefault = defaultPhone != null && areNumbersEqual(phoneDetail.number, defaultPhone.number)
                                        val isRecent = normalizePhoneNumber(phoneDetail.number) == phoneNumber && numberSize > 1
                                        Box {
                                            var showOverflowMenu by remember {
                                                mutableStateOf(
                                                    false
                                                )
                                            }
                                            RillListItem(
                                                headline = phoneDetail.number.forceLtr(),
                                                expandText = expandedListDetails,
                                                supportingContent = {
                                                    ContactPhoneMetadata(
                                                        context = context,
                                                        phoneRow = phoneRow,
                                                        isDefault = isDefault,
                                                        isRecent = isRecent,
                                                    )
                                                },
                                                leadingIcon = if (isDefault) ImageVector.vectorResource(id = R.drawable.ic_phone_star) else DotDialerHandsetIcon,
                                                iconContainerColor =
                                                    if (isDefault) MaterialTheme.colorScheme.primary else null,
                                                preTrailingIcon = if (whatsappInstall) whatsappImageVector else null,
                                                modifierPreTrailingIcon = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(24.dp)
                                                    .combinedClickable(
                                                        interactionSource = null,
                                                        indication = ripple(
                                                            bounded = false,
                                                            radius = 24.dp
                                                        ),
                                                        onClick = {
                                                            context.launchSendWhatsAppIntent(
                                                                phoneDetail.number
                                                            )
                                                        }),
                                                trailingIcon = messageImageVector,
                                                modifierTrailingIcon = Modifier
                                                    .padding(end = 8.dp)
                                                    .size(24.dp)
                                                    .combinedClickable(
                                                        interactionSource = null,
                                                        indication = ripple(
                                                            bounded = false,
                                                            radius = 24.dp
                                                        ),
                                                        onClick = {
                                                            initiateMessage(
                                                                phoneDetail.number
                                                            )
                                                        }),
                                                onClick = { initiateCall(phoneDetail.number) },
                                                onLongClick = { showOverflowMenu = true }
                                            )
                                            // Dropdown menu
                                            DropdownMenu(
                                                shape = MaterialTheme.shapes.large,
                                                expanded = showOverflowMenu,
                                                onDismissRequest = {
                                                    showOverflowMenu = false
                                                },
                                                offset = offsetMenu,
                                            ) {
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    text = {
                                                        Text(
                                                            phoneDetail.number.forceLtr(),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                        )
                                                    },
                                                    onClick = { },
                                                    enabled = false
                                                )
                                                val numberBlocked = isNumberBlocked(phoneDetail.number)
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    text = { Text(if (numberBlocked) stringResource(R.string.action_unblock_number) else stringResource(R.string.action_block_number)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        if (numberBlocked) {
                                                            BlockedNumbersManager.unblock(context, phoneDetail.number)
                                                        } else {
                                                            BlockedNumbersManager.block(context, phoneDetail.number)
                                                        }
                                                        blockedVersion++
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(
                                                        horizontal = 24.dp
                                                    ),
                                                    text = { Text(stringResource(R.string.add_note)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        editingNoteNumber = phoneDetail.number
                                                        showNoteEditor = true
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    contentPadding = PaddingValues(
                                                        horizontal = 24.dp
                                                    ),
                                                    text = { Text(stringResource(R.string.copy_action)) },
                                                    onClick = {
                                                        showOverflowMenu = false
                                                        context.copyToClipboard(phoneDetail.number)
                                                    }
                                                )
                                                if (numberSize > 1) {
                                                    if (phoneDetail.number == defaultPhone?.number) {
                                                        val textToast = stringResource(R.string.default_phone_number_cleared)
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.clear_default)) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                updateDefaultPhone(
                                                                    phoneDetail.number,
                                                                    false
                                                                )
                                                                context.toast(textToast)
                                                            }
                                                        )
                                                    } else {
                                                        val message = stringResource(
                                                            R.string.default_phone_set,
                                                            phoneDetail.number.forceLtr()
                                                        )
                                                        DropdownMenuItem(
                                                            contentPadding = PaddingValues(
                                                                horizontal = 24.dp
                                                            ),
                                                            text = { Text(stringResource(R.string.set_as_default)) },
                                                            onClick = {
                                                                showOverflowMenu = false
                                                                updateDefaultPhone(
                                                                    phoneDetail.number,
                                                                    true
                                                                )
                                                                context.toast(message)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    currentContact.emails.forEachIndexed { index, email ->
                                        val label =
                                            getEmailTypeText(context, email.type, email.label)
                                        RillListItem(
                                            headline = email.value,
                                            supporting = label,
                                            leadingIcon = Icons.Rounded.Email,
                                            onClick = { initiateEmail(email.value) },
                                            onLongClick = { context.copyToClipboard(email.value) }
                                        )
                                    }

                                    if (currentContact.phoneDetails.isEmpty() && currentContact.emails.isEmpty()) {
                                        RillListItem(
                                            headline = stringResource(R.string.add_phone),
                                            leadingIcon = Icons.Rounded.Add,
                                            onClick = { editPhoneContact(currentContact) }
                                        )
                                    }
                                } else if (phoneNumber != null && phoneNumber != unknownLabel) {
                                    Box {
                                        var showOverflowMenu by remember { mutableStateOf(false) }
                                        RillListItem(
                                            headline = phoneNumber.forceLtr(),
                                            leadingIcon = DotDialerHandsetIcon,
                                            onClick = { initiateCall(phoneNumber) },
                                            onLongClick = { showOverflowMenu = true }
                                        )
                                        // Dropdown menu
                                        DropdownMenu(
                                            shape = MaterialTheme.shapes.large,
                                            expanded = showOverflowMenu,
                                            onDismissRequest = { showOverflowMenu = false },
                                            offset = offsetMenu,
                                        ) {
                                            DropdownMenuItem(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                text = {
                                                    Text(
                                                        phoneNumber.forceLtr(),
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                    )
                                                },
                                                onClick = { },
                                                enabled = false
                                            )
                                            val numberBlocked = isNumberBlocked(phoneNumber)
                                            DropdownMenuItem(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                text = { Text(if (numberBlocked) stringResource(R.string.action_unblock_number) else stringResource(R.string.action_block_number)) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    if (numberBlocked) {
                                                        BlockedNumbersManager.unblock(context, phoneNumber)
                                                    } else {
                                                        BlockedNumbersManager.block(context, phoneNumber)
                                                    }
                                                    blockedVersion++
                                                }
                                            )
                                            DropdownMenuItem(
                                                contentPadding = PaddingValues(horizontal = 24.dp),
                                                text = { Text(stringResource(R.string.copy_action)) },
                                                onClick = {
                                                    showOverflowMenu = false
                                                    context.copyToClipboard(phoneNumber)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Call Notes section (between Contact Info and Recent Activity)
                        item {
                            var refreshTrigger by remember { mutableIntStateOf(0) }

                            // Get a list of all the contact's phone numbers
                            val phoneNumbersList = remember(contact) {
                                contact?.phoneDetails?.map { it.number }
                                    ?: if (phoneNumber != null && phoneNumber != unknownLabel) {
                                        listOf(phoneNumber)
                                    } else {
                                        emptyList()
                                    }
                            }

                            // Get all the notes for all issues
                            val allNotes = remember(contact, refreshTrigger) {
                                if (phoneNumbersList.isNotEmpty()) {
                                    NoteManager.getAllNotesForNumbers(context, phoneNumbersList)
                                } else {
                                    emptyMap()
                                }
                            }
                            // Refresh after closing the editor
                            LaunchedEffect(showNoteEditor) {
                                if (!showNoteEditor) {
                                    refreshTrigger++
                                }
                            }

                            ContactNotesCategoryCard {
                                if (allNotes.isEmpty()) {
                                    // No notes—display the "Add" button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = cardColor,
                                                shape = MaterialTheme.shapes.extraLarge.copy(
                                                    topStart = CornerSize(cardCornerExtraSmall),
                                                    topEnd = CornerSize(cardCornerExtraSmall)
                                                )
                                            )
                                            .combinedClickable(
                                                onClick = {
                                                    if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showAddCallNotePicker =
                                                        true
                                                    else if (displayPhone != unknownLabel) initiateAddCallNote(
                                                        displayPhone
                                                    )
                                                }
                                            )
                                            .padding(vertical = 16.dp, horizontal = 24.dp)
                                    ) {
                                        Text(
                                            text = "+ " + stringResource(R.string.add_note),
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.accentText
                                            )
                                        )
                                    }
                                } else {
                                    // Here is a list of all notes
                                    allNotes.forEach { (number, noteContent) ->
                                        RillListItem(
                                            headline = number,
                                            supporting = noteContent,
                                            leadingIcon = Icons.AutoMirrored.Outlined.StickyNote2,
                                            onClick = {
                                                editingNoteNumber = number
                                                showNoteEditor = true
                                            },
                                            onLongClick = {
                                                if (noteContent.isNotBlank()) {
                                                    val clipboard =
                                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    clipboard.setPrimaryClip(
                                                        ClipData.newPlainText(
                                                            "Phone number",
                                                            noteContent
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (contactNotes?.isNotBlank() == true) {
                            item {
                                ContactDetailsCategoryCard(title = stringResource(R.string.notes_contact)) {
                                    RillListItem(
                                        supporting = contactNotes,
                                        leadingIcon = Icons.AutoMirrored.Rounded.StickyNote2,
                                        onClick = {},
                                        onLongClick = {
                                            val clipboard =
                                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText("Phone number", contactNotes)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        // Events & Addresses
                        if (currentContact != null && (currentContact.events.isNotEmpty() || currentContact.addresses.isNotEmpty())) {
                            item {
                                ContactDetailsCategoryCard(
                                    title = stringResource(R.string.other_information),
                                ) {
                                    currentContact.events.forEachIndexed { index, event ->
                                        val isBirthday =
                                            event.type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                                        val label = getEventTypeText(context, event.type, event.label)
                                        RillListItem(
                                            headline = event.date,
                                            supporting = label,
                                            leadingIcon = if (isBirthday) Icons.Outlined.Cake else Icons.Outlined.Event,
                                            onClick = {
                                                val dateParts = event.date.split("-")
                                                if (dateParts.size == 3) {
                                                    val month = dateParts[1].toInt()
                                                    val day = dateParts[2].toInt()

                                                    val currentYear =
                                                        Calendar.getInstance().get(Calendar.YEAR)
                                                    val calendar = Calendar.getInstance().apply {
                                                        set(Calendar.YEAR, currentYear)
                                                        set(
                                                            Calendar.MONTH,
                                                            month - 1
                                                        ) // Month in Calendar: 0 = January
                                                        set(Calendar.DAY_OF_MONTH, day)
                                                        set(Calendar.HOUR_OF_DAY, 12)
                                                        set(Calendar.MINUTE, 0)
                                                        set(Calendar.SECOND, 0)
                                                        set(Calendar.MILLISECOND, 0)
                                                    }
                                                    val uri =
                                                        "content://com.android.calendar/time/${calendar.timeInMillis}".toUri()
                                                    context.startActivity(
                                                        Intent(
                                                            Intent.ACTION_VIEW,
                                                            uri
                                                        )
                                                    )
                                                }
                                            },
                                            onLongClick = { context.copyToClipboard(event.date) }
                                        )
                                    }
                                    currentContact.addresses.forEachIndexed { index, address ->
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val label =
                                            getAddressTypeText(context, address.type, address.label)
                                        RillListItem(
                                            headline = address.formattedAddress,
                                            supporting = label,
                                            leadingIcon = Icons.Rounded.LocationOn,
                                            trailingIcon = Icons.Outlined.Directions,
                                            modifierTrailingIcon = Modifier
                                                .padding(end = 8.dp)
                                                .size(24.dp)
                                                .combinedClickable(
                                                    interactionSource = interactionSource,
                                                    indication = ripple(
                                                        bounded = false,
                                                        radius = 24.dp
                                                    ),
                                                    onClick = {
                                                        context.startActivity(
                                                            Intent(
                                                                Intent.ACTION_VIEW,
                                                                "google.navigation:q=$address".toUri()
                                                            )
                                                        )
                                                    }),
                                            onClick = {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        "geo:0,0?q=$address".toUri()
                                                    )
                                                )
                                            },
                                            onLongClick = { context.copyToClipboard(address.formattedAddress) }
                                        )
                                    }
                                }
                            }
                        }

                        // Recent Activity
                        if (contactLogs.isNotEmpty()) {
                            val hasMultipleNumbers = contactLogs.distinctBy { it.number }.size > 1 ||
                                (currentContact?.phoneNumbers?.size ?: 0) > 1
                            item {
                                ContactDetailsCategoryCard(
                                    title = stringResource(R.string.recent_activity),
                                ) {
                                    Column(modifier = Modifier.animateContentSize()) {
                                        contactLogs.take(3).forEachIndexed { index, log ->
                                            CallLogTileSimple(log, showNumber = hasMultipleNumbers, showSimLabel = showSimLabel)
                                            if (index < 2 && index < contactLogs.size - 1) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                            }
                                        }
                                        if (contactLogs.size > 3) {
                                            val finalContactId = currentContact?.id ?: contactId?.takeUnless { it == "null" }
                                            val finalPhoneNumber = phoneNumber ?: currentContact?.phoneNumbers?.firstOrNull() ?: contactLogs.firstOrNull()?.number
                                            TextButton(
                                                onClick = {
                                                    navigator.navigate(CallLogFullScreenDestination(
                                                        contactId = finalContactId,
                                                        phoneNumber = finalPhoneNumber,
                                                        numbersList = currentContact?.phoneNumbers?.toTypedArray()
                                                    ))
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .offset(
                                                        x = 0.dp,
                                                        y = (-2).dp
                                                    ), // We'll make up for the indentation
                                                colors = ButtonDefaults.textButtonColors()
                                                    .copy(containerColor = cardColor),
                                                shape = MaterialTheme.shapes.extraLarge.copy(
                                                    topStart = CornerSize(cardCornerExtraSmall),
                                                    topEnd = CornerSize(cardCornerExtraSmall)
                                                ),
                                            ) {
                                                Text(stringResource(R.string.show_full_history))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Contact Settings
                        item {
                            ContactDetailsCategoryCard(title = stringResource(R.string.contacts_settings)) {
                                if (currentContact != null) {
                                    val customRingtoneLabel = stringResource(R.string.ringtone_custom)
                                    val currentRingtone = currentContact.customRingtone?.let { uriStr ->
                                        ringtoneTitleOrFallback(context, uriStr, customRingtoneLabel)
                                    } ?: stringResource(R.string.default_number)
                                    val contactRingtone = stringResource(R.string.contact_ringtone)
                                    RillListItem(
                                        headline = contactRingtone,
                                        supporting = currentRingtone,
                                        leadingIcon = Icons.Rounded.MusicNote,
                                        onClick = {
                                            val intent =
                                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_TYPE,
                                                        RingtoneManager.TYPE_RINGTONE
                                                    )
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_TITLE,
                                                        contactRingtone
                                                    )
                                                    putExtra(
                                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                                        currentContact.customRingtone?.toUri()
                                                    )
                                                }
                                            ringtonePickerLauncher.launch(intent)
                                        }
                                    )
                                    val accountInfos = remember { getCallingAccounts(context) }
                                    val preferredAccountKey = prefs.getFavoriteSim(currentContact.id)
                                    val preferredAccountLabel = accountInfos.firstOrNull {
                                        it.key == preferredAccountKey || it.handle.id == preferredAccountKey
                                    }?.label ?: stringResource(R.string.use_app_default)
                                    RillListItem(
                                        headline = stringResource(R.string.preferred_calling_account),
                                        supporting = preferredAccountLabel,
                                        leadingIcon = Icons.Rounded.SimCard,
                                        onClick = { showCallingAccountDialog = true },
                                    )
                                    CallBackgroundRow(
                                        background = callBackground,
                                        saving = backgroundSaving,
                                        onClick = onBackgroundClick
                                    )
                                }
                                val interactionSource = remember { MutableInteractionSource() }
                                RillListItem(
                                    headline = stringResource(R.string.share),
                                    supporting = stringResource(R.string.share_subtitle),
                                    leadingIcon = Icons.Rounded.Share,
                                    trailingIcon = Icons.Rounded.QrCode2,
                                    modifierTrailingIcon = Modifier
                                        .padding(end = 8.dp)
                                        .size(24.dp)
                                        .combinedClickable(
                                            interactionSource = interactionSource,
                                            indication = ripple(
                                                bounded = false,
                                                radius = 24.dp
                                            ),
                                            onClick = {
                                                if ((currentContact?.phoneNumbers?.size ?: 0) > 1) showQrDialogPicker =
                                                    true
                                                else {
                                                    pendingQrNumber = null; showQrDialog = true
                                                }
                                            }
                                        ),
                                    onClick = {
                                        shareContact(displayPhone)
                                    }
                                )
                                if (currentContact != null || shortcutNumbers.isNotEmpty()) {
                                    RillListItem(
                                        headline = stringResource(R.string.add_to_home_screen),
                                        leadingIcon = Icons.Rounded.Widgets,
                                        onClick = {
                                            if (currentContact != null) {
                                                if (shortcutNumbers.size > 1) {
                                                    showShortcutNumberPicker = true
                                                } else if (shortcutNumbers.isEmpty()) {
                                                    ContactShortcutUtils.pinOpenContactShortcut(
                                                        context,
                                                        currentContact.id,
                                                        displayName,
                                                        currentContact.photoUri
                                                    )
                                                } else {
                                                    pendingShortcutNumber = shortcutNumbers.first()
                                                    showShortcutActionPicker = true
                                                }
                                            } else if (shortcutNumbers.isNotEmpty()) {
                                                val shortcutNumber = shortcutNumbers.first()
                                                ContactShortcutUtils.pinCallShortcut(
                                                    context,
                                                    shortcutNumber,
                                                    displayName,
                                                    shortcutNumber,
                                                    null
                                                )
                                            }
                                        }
                                    )
                                }
                                if (currentContact != null && contactSources.size < 2) {
                                    RillListItem(
                                        headline = stringResource(R.string.move_contact),
                                        supporting = stringResource(R.string.move_to_another_account),
                                        leadingIcon = Icons.AutoMirrored.Rounded.DriveFileMove,
                                        onClick = { showMoveDialog = true }
                                    )
                                }
                                val contactNumbers = currentContact?.phoneNumbers ?: listOf(displayPhone)
                                val contactBlocked = contactNumbers.isNotEmpty() && contactNumbers.all { isNumberBlocked(it) }
                                RillListItem(
                                    headline =
                                        if (contactBlocked) {
                                            if (currentContact != null) stringResource(R.string.contact_unblock) else stringResource(R.string.action_unblock_number)
                                        } else {
                                            if (currentContact != null) stringResource(R.string.contact_block) else stringResource(R.string.action_block_number)
                                        },
                                    supporting = if (contactBlocked) stringResource(R.string.contact_unblock_supporting) else stringResource(R.string.contact_block_supporting),
                                    leadingIcon = if (contactBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                                    iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                    iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                    onClick = {
                                        contactNumbers.forEach { number ->
                                            if (contactBlocked) {
                                                BlockedNumbersManager.unblock(context, number)
                                            } else {
                                                BlockedNumbersManager.block(context, number)
                                            }
                                        }
                                        blockedVersion++
                                    }
                                )
                                if (currentContact != null) {
                                    RillListItem(
                                        headline = stringResource(R.string.delete),
                                        leadingIcon = ImageVector.vectorResource(id = R.drawable.ic_delete),
                                        iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                                        iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                                        onClick = { showDeleteDialog = true }
                                    )
                                }

                                if (showMoveDialog && currentContact != null) {
                                    val textToast = stringResource(R.string.contact_moved_successfully)
                                    MoveSingleContactDialog(
                                        contact = currentContact,
                                        availableAccounts = availableAccountsForMoving,
                                        currentAccountKey = if (currentContact.accountName == null && currentContact.accountType == null && currentContact.isPrivate) private_only
                                                            else if (currentContact.accountName == null && currentContact.accountType == null) device_only
                                                            else "${currentContact.accountName}|${currentContact.accountType}",
                                        contactsViewModel = contactsViewModel,
                                        onDismiss = { showMoveDialog = false },
                                        onSuccess = { selectedAccount, isPrivate, newContactId ->
                                            context.toast(textToast)
                                            // Updating local data
                                            contact = currentContact.copy(
                                                id = newContactId,
                                                accountName = selectedAccount?.name,
                                                accountType = selectedAccount?.type,
                                                isPrivate = isPrivate
                                            )
                                            contactAccount = selectedAccount
                                        }
                                    )
                                }
                            }
                        }

                        // Contact Sources
                        if (currentContact != null && contactSources.size > 1) {
                            item {
                                ContactDetailsCategoryCard(title = stringResource(R.string.contact_sources)) {
                                    contactSources.forEachIndexed { index, source ->
                                        val account = Account(source.accountName ?: "", source.accountType ?: "")
                                        SourceItem(
                                            modifier = Modifier.combinedClickable(
                                                onClick = { showSourcesDialog = true },
                                            ),
                                            leadingIcon = ContactUtils.getAccountIcon(account, currentContact.isPrivate),
                                            headline = ContactUtils.getAccountName(account),
                                            supporting = ContactUtils.getFriendlyAccountName(account),
                                        )
                                    }
                                }
                            }
                        } else if (currentContact != null) {
                            item {
                                ContactDetailsCategoryCard(title = stringResource(R.string.contact_sources)) {
                                    val currentAccount = contactAccount
                                    SourceItem(
                                        modifier = Modifier,
                                        leadingIcon = ContactUtils.getAccountIcon(currentAccount, currentContact.isPrivate),
                                        headline = currentAccount?.let { ContactUtils.getAccountName(it) }
                                            ?: ContactUtils.getFriendlyAccountName(null, currentContact.isPrivate),
                                        supporting = currentAccount?.let { ContactUtils.getFriendlyAccountName(it) },
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(100.dp)) }
                    }

                }

            }
        }
    }
}
