package dev.goodwy.rphone.view.components

import dev.goodwy.rphone.view.theme.accentText

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.StarHalf
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import dev.goodwy.rphone.R
import dev.goodwy.rphone.bottomBarHeight
import dev.goodwy.rphone.controller.util.PreferenceManager
import dev.goodwy.rphone.controller.util.SocialUtils
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.generated.destinations.ContactDetailsScreenDestination
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.generated.destinations.DialPadScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.controller.util.copyToClipboard
import dev.goodwy.rphone.controller.util.makeCall
import dev.goodwy.rphone.controller.util.shareContactsAsVCard
import dev.goodwy.rphone.modal.data.getDisplayContactInfo
import dev.goodwy.rphone.modal.data.getDisplayName
import dev.goodwy.rphone.view.screen.CONTACT_ALPHABET
import dev.goodwy.rphone.view.screen.HomeResultRow
import dev.goodwy.rphone.view.screen.resolvedContactAlphabetTargets
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinActivityViewModel

internal const val CONTACT_ALPHABET_INDICATOR_SHADOW_ELEVATION_DP = 3

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AZListScroll(
    contacts: List<Contact>,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
    grouped: Map<Char, List<Contact>>? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    AZListContent(
        contacts = contacts,
        navigator = navigator,
        modifier = modifier,
        listState = listState,
        selectedIds = selectedIds,
        onToggleSelection = onToggleSelection,
        grouped = grouped,
        headerContent = headerContent,
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun AZListContent(
    contacts: List<Contact>,
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: ((String) -> Unit)? = null,
    descending: Boolean = false,
    grouped: Map<Char, List<Contact>>? = null,
    headerContent: (@Composable () -> Unit)? = null,
) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
    val showFavorites = false //!prefs.getBoolean(PreferenceManager.KEY_TAB_SHOW_FAVORITES, true)

    val displayOrder = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CONTACT_DISPLAY_ORDER, 0) }
    val sortOrder = remember(settingsState) { prefs.getInt(PreferenceManager.KEY_CONTACT_SORT_ORDER, 0) }
    val rowPreferences = remember(settingsState) { prefs.contactListRowPreferences() }

    val finalGrouped = remember(contacts, grouped, sortOrder, descending) {
        if (grouped != null) return@remember grouped

        val mainGroups = contacts.groupBy {
            val sortName = if (sortOrder == 1) {
                it.familyName.ifBlank { it.displayName }
            } else {
                it.givenName.ifBlank { it.displayName }
            }
            val firstChar = SearchTextNormalizer
                .normalize(sortName.trim(), foldDiacritics = true)
                .firstOrNull()
                ?.uppercaseChar()
                ?: '#'
            if (firstChar.isLetter()) firstChar else '#'
        }.toMutableMap()

        val finalMap = linkedMapOf<Char, List<Contact>>()

        mainGroups.keys.filter { it.isLetter() }.sorted().forEach { char ->
            finalMap[char] = mainGroups.getValue(char)
        }

        val hashGroup = mainGroups['#']
        if (hashGroup != null) finalMap['#'] = hashGroup

        if (!descending) finalMap else finalMap.entries.reversed().associateTo(linkedMapOf()) {
            (letter, rows) -> letter to rows.reversed()
        }
    }

    // Map each letter to its first LazyColumn item index for sidebar jump
    val hasHeader = headerContent != null
    val alphabetIndices = remember(finalGrouped, hasHeader) {
        val map = mutableMapOf<Char, Int>()
        var currentIndex = if (hasHeader) 1 else 0
        finalGrouped.forEach { (char, group) ->
            map[char] = currentIndex          // stickyHeader index
            currentIndex += 1 + group.size   // header + N contact items
        }
        map
    }
    val resolvedAlphabetIndices = remember(alphabetIndices) {
        resolvedContactAlphabetTargets(
            alphabetIndices.mapKeys { (letter, _) -> letter.toString() },
        ).mapKeys { (letter, _) -> letter.single() }
    }

    val scope = rememberCoroutineScope()
    var draggingChar by remember { mutableStateOf<Char?>(null) }
    var railScrollJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val scrollingChar by remember(alphabetIndices, listState) {
        derivedStateOf {
            // A preceding sticky header may remain pinned while the next section's rows
            // are visible. Highlight the first actual contact, not that pinned header.
            val layout = listState.layoutInfo
            val viewportStart = maxOf(0, layout.viewportStartOffset)
            val leadingHeaderBottom = layout.visibleItemsInfo
                .filter { it.key.toString().startsWith("header_") && it.offset <= viewportStart }
                .maxOfOrNull { it.offset + it.size } ?: viewportStart
            val visibleLetter = layout.visibleItemsInfo.firstNotNullOfOrNull { item ->
                val key = item.key as? String
                key?.takeIf { it.length > 1 && it[1] == '_' && it[0] in alphabetIndices &&
                    item.offset + item.size > leadingHeaderBottom }?.first()
            }
            val firstVisible = listState.firstVisibleItemIndex
            visibleLetter ?: alphabetIndices.entries
                .filter { it.value <= firstVisible }
                .maxByOrNull { it.value }
                ?.key ?: alphabetIndices.keys.firstOrNull()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val configuration = LocalConfiguration.current
        val screenHeightDp = configuration.screenHeightDp.dp
        val bottomPadding = (screenHeightDp - 340.dp).coerceAtLeast(0.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding)
        ) {
            headerContent?.let { content ->
                item(key = "contact_list_header", contentType = "contactListHeader") {
                    content()
                }
            }
            if (contacts.isEmpty()) {
                item(key = "emptyContacts") {
                    Text(stringResource(R.string.no_results), modifier = Modifier.padding(32.dp))
                }
            }
            finalGrouped.forEach { (initial, contactsForChar) ->
                // ── Letter header ──────────────────────────────────────────
                stickyHeader(key = "header_$initial", contentType = "letterHeader") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 28.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (initial == '❤') "Favorites" else initial.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.accentText,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                // ── One lazy item per contact for smooth scrolling ─────────
                val selectionMode = selectedIds.isNotEmpty()
                itemsIndexed(
                    items = contactsForChar,
                    key = { _, contact -> "${initial}_${contact.id}" },
                    contentType = { _, _ -> "contact" }
                ) { index, contact ->
                    val isFirst  = index == 0
                    val isLast   = index == contactsForChar.lastIndex
                    val isSelected = selectedIds.contains(contact.id)

                    ContactListItem(
                        contact = contact,
                        navigator = navigator,
                        selectionMode = selectionMode,
                        isSelected = isSelected,
                        isFirstInSection = isFirst,
                        isLastInSection = isLast,
                        displayOrder = displayOrder,
                        rowPreferences = rowPreferences,
                        onSelectToggle = onToggleSelection?.let { toggle ->
                            { toggle(contact.id) }
                        }
                    )

                    // Gap between letter groups
                    if (isLast) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val alphabet = CONTACT_ALPHABET.map(String::single)
        if (!isLandscape && alphabetIndices.isNotEmpty()) {
            val pillNav = remember { prefs.getBoolean(PreferenceManager.KEY_PILL_NAV, false) }
            val paddingBottom = if (pillNav) 0.dp else bottomBarHeight
            AlphabetSideBar(
                alphabet = alphabet,
                selectedChar = draggingChar ?: scrollingChar,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .navigationBarsPadding()
                    .padding(end = 0.dp, bottom = paddingBottom),
                onLetterSelected = { char ->
                    draggingChar = char
                    val index = resolvedAlphabetIndices[char] ?: return@AlphabetSideBar
                    railScrollJob?.cancel()
                    railScrollJob = scope.launch { listState.scrollToItem(index) }
                },
                onDragEnd = { draggingChar = null }
            )
        }

        if (draggingChar != null) {
            Surface(
                modifier = Modifier
                    .size(100.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(36.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = CONTACT_ALPHABET_INDICATOR_SHADOW_ELEVATION_DP.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = draggingChar.toString(),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContactListItem(
    contact: Contact,
    navigator: DestinationsNavigator,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    isFirstInSection: Boolean = true,
    isLastInSection: Boolean = true,
    displayOrder: Int,
    rowPreferences: ContactListRowPreferences? = null,
    onSelectToggle: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val prefs = koinInject<PreferenceManager>()
    val resolvedRowPreferences = if (rowPreferences != null) {
        rowPreferences
    } else {
        val settingsState by prefs.settingsChanged.collectAsStateWithLifecycle()
        remember(settingsState) { prefs.contactListRowPreferences() }
    }
    val contactsVM: ContactsViewModel = koinActivityViewModel()
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showGroups by remember { mutableStateOf(false) }
    var pendingNumberAction by remember { mutableStateOf<ContactMenuNumberAction?>(null) }
    val headline =  getDisplayName(contact, displayOrder)
    val contactInfo = getDisplayContactInfo(contact)
    val phoneNumber = contact.phoneDetails.firstOrNull { it.isPrimary }?.number
        ?: contact.phoneNumbers.firstOrNull()

    fun runNumberAction(action: ContactMenuNumberAction, number: String) {
        when (action) {
            ContactMenuNumberAction.Call -> makeCall(context, number)
            ContactMenuNumberAction.Message -> context.startActivity(
                Intent(Intent.ACTION_VIEW, "sms:$number".toUri())
            )
            ContactMenuNumberAction.EditBeforeCall ->
                navigator.navigate(DialPadScreenDestination(initialNumber = number))
            ContactMenuNumberAction.Copy -> context.copyToClipboard(number)
        }
    }

    fun chooseNumber(action: ContactMenuNumberAction) {
        val numbers = contact.phoneNumbers.distinct()
        when (numbers.size) {
            0 -> Unit
            1 -> runNumberAction(action, numbers.first())
            else -> pendingNumberAction = action
        }
    }

    pendingNumberAction?.let { action ->
        NumberPickerDialog(
            numbers = contact.phoneNumbers.distinct(),
            onDismissRequest = { pendingNumberAction = null },
            onNumberSelected = { number ->
                pendingNumberAction = null
                runNumberAction(action, number)
            },
        )
    }

    if (showGroups) {
        ManageContactGroupsDialog(
            contact = contact,
            contactsViewModel = contactsVM,
            onDismissRequest = { showGroups = false },
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        RillDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.delete_contact),
            icon = ImageVector.vectorResource(id = R.drawable.ic_delete),
            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    contactsVM.deleteContact(contact.id)
                }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
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

    val onClick: () -> Unit = {
        if (selectionMode && onSelectToggle != null) {
            onSelectToggle()
        } else {
            navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
        }
    }
    Box {
        HomeResultRow(
            title = headline,
            avatarColorSeed = contact.displayName,
            subtitle = contactInfo,
            photoUri = contact.photoUri,
            phoneNumber = phoneNumber,
            swipeEnabled = resolvedRowPreferences.swipeEnabled && phoneNumber != null && !selectionMode,
            swipeRightAction = resolvedRowPreferences.swipeRightAction,
            swipeLeftAction = resolvedRowPreferences.swipeLeftAction,
            haptics = resolvedRowPreferences.haptics,
            hapticsStrength = resolvedRowPreferences.hapticsStrength,
            hapticsIntensity = resolvedRowPreferences.hapticsIntensity,
            onAction = { action ->
                val number = phoneNumber ?: return@HomeResultRow
                when (dev.goodwy.rphone.controller.util.supportedHomeSwipeAction(action)) {
                    PreferenceManager.SWIPE_ACTION_CALL -> makeCall(context, number)
                    PreferenceManager.SWIPE_ACTION_MESSAGE -> context.startActivity(
                        Intent(Intent.ACTION_VIEW, "sms:$number".toUri())
                    )
                    PreferenceManager.SWIPE_ACTION_CONTACT ->
                        navigator.navigate(ContactDetailsScreenDestination(contactId = contact.id))
                    PreferenceManager.SWIPE_ACTION_WHATSAPP -> SocialUtils.openWhatsApp(context, number)
                    PreferenceManager.SWIPE_ACTION_COPY_NUMBER -> context.copyToClipboard(number)
                }
            },
            onAvatarClick = onClick,
            onClick = onClick,
            onLongClick = { showMenu = true },
            selected = isSelected,
            isFirstInSection = isFirstInSection,
            isLastInSection = isLastInSection,
            contactNameSize = contactsListNameSizeSp(resolvedRowPreferences.contactNameSize),
            avatarSize = resolvedRowPreferences.avatarSize,
            expandedListDetails = resolvedRowPreferences.expandedListDetails,
        )

        AnimatedVisibility(
            visible = showMenu,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(tween(280)),
            exit  = slideOutVertically(targetOffsetY = { -it }, animationSpec = tween(420, easing = FastOutLinearInEasing)) + fadeOut(tween(380))
        ) {
            DropdownMenu(
                shape = MaterialTheme.shapes.large,
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                offset = DpOffset(62.dp, 48.dp),
            ) {
                if (onSelectToggle != null) {
                    DropdownMenuItem(
                        contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                        text = { Text(stringResource(R.string.select)) },
                        leadingIcon = { Icon(Icons.Rounded.CheckBox, null) },
                        onClick = {
                            showMenu = false
                            onSelectToggle()
                        }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.call)) },
                    leadingIcon = { Icon(DotDialerHandsetIcon, null) },
                    enabled = contact.phoneNumbers.isNotEmpty(),
                    onClick = {
                        showMenu = false
                        chooseNumber(ContactMenuNumberAction.Call)
                    }
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.message)) },
                    leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_message_filled), null) },
                    enabled = contact.phoneNumbers.isNotEmpty(),
                    onClick = {
                        showMenu = false
                        chooseNumber(ContactMenuNumberAction.Message)
                    }
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.edit_number_before_call)) },
                    leadingIcon = { Icon(Icons.Default.Dialpad, null) },
                    enabled = contact.phoneNumbers.isNotEmpty(),
                    onClick = {
                        showMenu = false
                        chooseNumber(ContactMenuNumberAction.EditBeforeCall)
                    }
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.copy_number)) },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                    enabled = contact.phoneNumbers.isNotEmpty(),
                    onClick = {
                        showMenu = false
                        chooseNumber(ContactMenuNumberAction.Copy)
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(if (contact.isFavorite) stringResource(R.string.remove_from_favorites) else stringResource(R.string.add_to_favorites)) },
                    leadingIcon = { Icon(if (contact.isFavorite) Icons.AutoMirrored.Filled.StarHalf else Icons.Default.Star, null, tint = if (contact.isFavorite) MaterialTheme.colorScheme.error else LocalContentColor.current) },
                    onClick = {
                        showMenu = false
                        contactsVM.toggleFavorite(contact)
                    }
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.manage_groups)) },
                    leadingIcon = { Icon(Icons.Default.Group, null) },
                    enabled = !contact.isPrivate,
                    onClick = {
                        showMenu = false
                        showGroups = true
                    }
                )
                val shareText = stringResource(R.string.share_contact)
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(shareText) },
                    leadingIcon = { Icon(Icons.Default.Share, null) },
                    onClick = {
                        showMenu = false
                        shareContactsAsVCard(context, listOf(contact), shareText)
                    }
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.edit_contact)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                    onClick = {
                        showMenu = false
                        navigator.navigate(ContactEditScreenDestination(contactId = contact.id))
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                DropdownMenuItem(
                    contentPadding = PaddingValues(start = 20.dp, end = 26.dp),
                    text = { Text(stringResource(R.string.delete_contact), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(ImageVector.vectorResource(id = R.drawable.ic_delete), null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        showDeleteConfirm = true
                    }
                )
            }
        }
    }
} // end AZListContent

private enum class ContactMenuNumberAction {
    Call,
    Message,
    EditBeforeCall,
    Copy,
}

