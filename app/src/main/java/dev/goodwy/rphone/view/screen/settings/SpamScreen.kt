package dev.goodwy.rphone.view.screen.settings

import dev.goodwy.rphone.view.theme.accentText

import dev.goodwy.rphone.view.components.DotDialerPhoneDisabledIcon

import android.content.Context
import android.view.Surface
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.controller.util.PreferenceManager
import io.github.bootika.dotdialer.core.search.SearchTextNormalizer
import dev.goodwy.rphone.modal.`interface`.ICallLogRepository
import dev.goodwy.rphone.modal.`interface`.IContactsRepository
import dev.goodwy.rphone.view.components.NavigationIcon
import dev.goodwy.rphone.view.screen.settings.components.SettingsImmediateSection
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.components.RillSwitchListItem
import dev.goodwy.rphone.view.components.rillSettingsIconPalette
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import dev.goodwy.rphone.view.theme.customColors
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.view.components.Title
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Destination<RootGraph>
@Composable
fun SpamScreen(navigator: DestinationsNavigator) {
    val prefs = koinInject<PreferenceManager>()
    val settingsState by prefs.settingsChanged.collectAsState()
    val normalizeSearchDiacritics = remember(settingsState) {
        prefs.getBoolean(PreferenceManager.KEY_SEARCH_DIACRITIC_NORMALIZATION, true)
    }
    val context = LocalContext.current

    var silenceUnknown by remember { mutableStateOf(prefs.getBoolean(PreferenceManager.KEY_SILENCE_UNKNOWN, false)) }

    // Blocked numbers dialog state
    var showBlockedNumbersDialog by remember { mutableStateOf(false) }
    var showBlockListDialog by remember { mutableStateOf(false) }
    var blockedNumbersTab by remember { mutableIntStateOf(0) }
    var blockedNumberInput by remember { mutableStateOf("") }
    var blockedContactsList by remember {
        mutableStateOf(
            prefs.getString(PreferenceManager.KEY_BLOCKED_CONTACTS, "")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    // ── Block List Detail Dialog ───────────────────────────────────────────────
    if (showBlockListDialog) {
        val headerIconPalette = rillSettingsIconPalette(accented = false)
        val contactsRepo: IContactsRepository = koinInject()
        var blockedWithInfo by remember { mutableStateOf<List<SpamNumberInfo>>(emptyList()) }
        LaunchedEffect(blockedContactsList) {
            blockedWithInfo = loadBlockedNumberInfo(blockedContactsList, contactsRepo)
        }

        Dialog(onDismissRequest = { showBlockListDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 20.dp, end = 20.dp), /*verticalArrangement = Arrangement.spacedBy(12.dp)*/) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = headerIconPalette.background,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Block,
                                    contentDescription = null,
                                    tint = headerIconPalette.foreground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text("Block List", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = headerIconPalette.background) {
                            Text("${blockedContactsList.size}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = headerIconPalette.foreground, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))

//                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))

                    if (blockedContactsList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.35f), modifier = Modifier.size(44.dp))
                                Text("No numbers blocked", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Surface(shape = MaterialTheme.shapes.large, color = Color.Transparent, modifier = Modifier.wrapContentSize()) {
                            LazyColumn(modifier = Modifier.heightIn(max = 340.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                itemsIndexed(blockedWithInfo) { index, (number, name, photoUri) ->
                                    Surface(shape = RoundedCornerShape(cardCornerExtraSmall), color = cardColor, modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            RillAvatar(name = name, photoUri = photoUri, modifier = Modifier.size(46.dp))
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                                if (name != number) Text(number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            }
                                            IconButton(onClick = {
                                                val updated = blockedContactsList.toMutableList().also { it.removeAt(index) }
                                                blockedContactsList = updated
                                                prefs.setString(PreferenceManager.KEY_BLOCKED_CONTACTS, updated.joinToString(","))
                                            }, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Rounded.Cancel, "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showBlockListDialog = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text("Close") }
                    }
                }
            }
        }
    }

    // ── Blocked Numbers Dialog ────────────────────────────────────────────────
    if (showBlockedNumbersDialog) {
        val headerIconPalette = rillSettingsIconPalette(accented = false)
        val callLogRepo: ICallLogRepository = koinInject()
        val contactsRepo: IContactsRepository = koinInject()

        var recentNumbers by remember { mutableStateOf<List<SpamNumberInfo>>(emptyList()) }
        var contactNumbers by remember { mutableStateOf<List<SpamNumberInfo>>(emptyList()) }
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            isLoading = true
            recentNumbers = loadRecentNumberInfo(callLogRepo, contactsRepo)
            contactNumbers = loadContactNumberInfo(contactsRepo)
            isLoading = false
        }

        val filteredRecents = remember(recentNumbers, searchQuery, normalizeSearchDiacritics) {
            if (searchQuery.isBlank()) recentNumbers
            else recentNumbers.filter { (num, name, _) ->
                SearchTextNormalizer.contains(name, searchQuery, normalizeSearchDiacritics) || num.contains(searchQuery)
            }
        }
        val filteredContacts = remember(contactNumbers, searchQuery, normalizeSearchDiacritics) {
            if (searchQuery.isBlank()) contactNumbers
            else contactNumbers.filter { (num, name, _) ->
                SearchTextNormalizer.contains(name, searchQuery, normalizeSearchDiacritics) || num.contains(searchQuery)
            }
        }

        fun blockNumber(number: String) {
            if (!blockedContactsList.contains(number)) {
                val updated = blockedContactsList + number
                blockedContactsList = updated
                prefs.setString(PreferenceManager.KEY_BLOCKED_CONTACTS, updated.joinToString(","))
            }
        }

        Dialog(onDismissRequest = { showBlockedNumbersDialog = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(top = 20.dp, bottom = 8.dp, start = 20.dp, end = 20.dp), /*verticalArrangement = Arrangement.spacedBy(12.dp)*/) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = headerIconPalette.background,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.PersonOff,
                                    contentDescription = null,
                                    tint = headerIconPalette.foreground,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text("Blocked Numbers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search…") },
                        leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(20.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Call Logs", "Contacts", "Manual").forEachIndexed { index, label ->
                            val selected = blockedNumbersTab == index
                            Surface(
                                onClick = { blockedNumbersTab = index },
                                shape = RoundedCornerShape(50),
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

//                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))

                    Spacer(Modifier.height(16.dp))
                    Box(modifier = Modifier.heightIn(min = 80.dp, max = 320.dp)) {
                        when (blockedNumbersTab) {
                            0 -> {
                                if (isLoading) {
                                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                } else if (filteredRecents.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                        Text(if (searchQuery.isBlank()) "No call logs found." else "No results for \"$searchQuery\"",
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Surface(shape = MaterialTheme.shapes.large, color = Color.Transparent, modifier = Modifier.wrapContentSize()) {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            items(filteredRecents, key = { it.number }) { (number, name, photoUri) ->
                                                val alreadyBlocked = blockedContactsList.contains(number)
                                                Surface(shape = RoundedCornerShape(cardCornerExtraSmall), color = if (alreadyBlocked) MaterialTheme.colorScheme.errorContainer.copy(0.3f) else cardColor) {
                                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        RillAvatar(name = name, photoUri = photoUri, modifier = Modifier.size(38.dp))
                                                        Spacer(Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                                            if (name != number) Text(number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        }
                                                        TextButton(onClick = { blockNumber(number) }, enabled = !alreadyBlocked, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                                                            Text(if (alreadyBlocked) "Blocked" else "Block", style = MaterialTheme.typography.labelSmall, color = if (alreadyBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.accentText)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                if (isLoading) {
                                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                    }
                                } else if (filteredContacts.isEmpty()) {
                                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                        Text(if (searchQuery.isBlank()) "No contacts found." else "No results for \"$searchQuery\"",
                                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Surface(shape = MaterialTheme.shapes.large, color = Color.Transparent, modifier = Modifier.wrapContentSize()) {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            items(filteredContacts, key = { it.number }) { (number, name, photoUri) ->
                                                val alreadyBlocked = blockedContactsList.contains(number)
                                                Surface(shape = RoundedCornerShape(cardCornerExtraSmall), color = if (alreadyBlocked) MaterialTheme.colorScheme.errorContainer.copy(0.3f) else cardColor) {
                                                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                        RillAvatar(name = name, photoUri = photoUri, modifier = Modifier.size(38.dp))
                                                        Spacer(Modifier.width(10.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                                                            if (name != number) Text(number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                        }
                                                        TextButton(onClick = { blockNumber(number) }, enabled = !alreadyBlocked, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                                                            Text(if (alreadyBlocked) "Blocked" else "Block", style = MaterialTheme.typography.labelSmall, color = if (alreadyBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.accentText)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            2 -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = blockedNumberInput,
                                        onValueChange = { blockedNumberInput = it },
                                        label = { Text("Enter number to block") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        trailingIcon = {
                                            if (blockedNumberInput.isNotBlank()) {
                                                IconButton(onClick = {
                                                    val num = blockedNumberInput.trim()
                                                    if (num.isNotBlank()) blockNumber(num)
                                                    blockedNumberInput = ""
                                                }) { Icon(Icons.Default.Add, "Add", tint = MaterialTheme.colorScheme.primary) }
                                            }
                                        }
                                    )
                                    if (searchQuery.isNotBlank()) {
                                        Button(
                                            onClick = { blockNumber(searchQuery.trim()); searchQuery = "" },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(50)
                                        ) { Text("Block \"${searchQuery.trim()}\"") }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showBlockedNumbersDialog = false }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) { Text("Done") }
                    }
                }
            }
        }
    }

    var visible by remember { mutableStateOf(false) }
    val screenAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(350),
        label = "spamAlpha"
    )
    LaunchedEffect(Unit) { visible = true }

    val rotation = context.currentDisplayRotation()
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val isRotation90 = rotation == if (isLtr) Surface.ROTATION_90 else Surface.ROTATION_270
    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.systemBars.only(
                    if (isRotation90) WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    else WindowInsetsSides.Top
                ),
                title = { Title("Spam") },
                navigationIcon = {
                    NavigationIcon(onClick = { navigator.navigateUp() })
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
//                .padding(padding)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = 0.dp,
                    end = 0.dp,
                    bottom = 0.dp
                )
                .alpha(screenAlpha),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                SettingsImmediateSection() {
                    RillExpressiveCard {
                        RillSwitchListItem(
                            headline   = "Silence Unknown Callers",
                            supporting = "Automatically decline calls from unknown numbers",
                            leadingIcon = DotDialerPhoneDisabledIcon,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkAmber,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorAmber,
                            checked = silenceUnknown,
                            onCheckedChange = {
                                silenceUnknown = it
                                prefs.setBoolean(PreferenceManager.KEY_SILENCE_UNKNOWN, it)
                            }
                        )
                    }
                }
            }

            item {
                SettingsImmediateSection() {
                    RillExpressiveCard {
                        RillListItem(
                                expandText = true,
                            headline = "Blocked Numbers",
                            supporting = "${blockedContactsList.size} number(s) blocked",
                            leadingIcon = Icons.Rounded.PersonOff,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                            trailingIcon = Icons.Default.ChevronRight,
                            onClick = { showBlockedNumbersDialog = true }
                        )
                        RillListItem(
                                expandText = true,
                            headline = "Tap to see the Block list",
                            supporting = if (blockedContactsList.isEmpty()) "No numbers blocked"
                            else "${blockedContactsList.size} number(s) blocked",
                            leadingIcon = Icons.Rounded.Block,
                            iconContainerColor = MaterialTheme.colorScheme.customColors.colorDarkRed,
                            iconBgContainerColor = MaterialTheme.colorScheme.customColors.colorRed,
                            trailingIcon = Icons.Default.ChevronRight,
                            onClick = { showBlockListDialog = true }
                        )
                    }
                }
            }

            item { SettingsBottomPadding() }
        }
    }
}
