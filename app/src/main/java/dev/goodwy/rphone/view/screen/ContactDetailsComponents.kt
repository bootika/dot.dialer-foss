package dev.goodwy.rphone.view.screen

import dev.goodwy.rphone.view.theme.accentText

import android.accounts.Account
import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.PeopleAlt
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ramcosta.composedestinations.generated.destinations.ContactEditScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import dev.goodwy.rphone.R
import dev.goodwy.rphone.cardCornerExtraSmall
import dev.goodwy.rphone.cardSpacedBy
import dev.goodwy.rphone.controller.ContactsViewModel
import dev.goodwy.rphone.controller.util.ContactUtils
import dev.goodwy.rphone.controller.util.QrCodeUtils
import dev.goodwy.rphone.controller.util.getPhoneTypeText
import dev.goodwy.rphone.modal.data.Contact
import dev.goodwy.rphone.modal.repository.ContactsRepository
import dev.goodwy.rphone.view.components.RillAvatar
import dev.goodwy.rphone.view.components.RillDialog
import dev.goodwy.rphone.view.components.RillExpressiveCard
import dev.goodwy.rphone.view.components.RillListItem
import dev.goodwy.rphone.view.theme.MyColors.cardColor
import org.koin.compose.viewmodel.koinActivityViewModel

internal suspend fun loadPhoneSourceData(
    contactsViewModel: ContactsViewModel,
    sources: List<ContactsRepository.ContactSource>,
): List<ContactPhoneSourceData> = sources.mapNotNull { source ->
    try {
        contactsViewModel.getRawContactData(source.rawContactId)?.let { rawContact ->
            ContactPhoneSourceData(source.accountName, source.accountType, rawContact.phoneDetails)
        }
    } catch (error: Exception) {
        android.util.Log.w("ContactDetails", "Unable to load phone-number source", error)
        null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContactPhoneMetadata(
    context: Context,
    phoneRow: ContactPhoneRow,
    isDefault: Boolean,
    isRecent: Boolean,
) {
    val statusLabels = buildList {
        if (isDefault) add(stringResource(R.string.default_number))
        if (isRecent) add(stringResource(R.string.recent_number))
    }
    val sourceLabels = phoneRow.sources
        .map { source ->
            val account = if (source.accountName != null || source.accountType != null) {
                Account(source.accountName.orEmpty(), source.accountType.orEmpty())
            } else null
            ContactUtils.getFriendlyAccountName(account)
        }
        .distinct()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ContactPhoneMetadataPill(
                text = getPhoneTypeText(context, phoneRow.detail.type, phoneRow.detail.label),
            )
            statusLabels.forEach { status ->
                ContactPhoneMetadataPill(text = status, highlighted = true)
            }
        }

        if (sourceLabels.isNotEmpty()) {
            Text(
                text = stringResource(R.string.contact_sources),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sourceLabels.forEach { source ->
                    ContactPhoneMetadataPill(text = source)
                }
            }
        }
    }
}

@Composable
private fun ContactPhoneMetadataPill(
    text: String,
    highlighted: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ContactDetailsCategoryCard(
    title: String,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    RillExpressiveCard(
        title = title,
        titleColor = MaterialTheme.colorScheme.onSurface,
        icon = icon,
        trailingIcon = trailingIcon,
        onTrailingIconClick = onTrailingIconClick,
        content = content,
    )
}

@Composable
internal fun ContactNotesCategoryCard(content: @Composable ColumnScope.() -> Unit) {
    ContactDetailsCategoryCard(title = stringResource(R.string.call_notes)) {
        Text(stringResource(R.string.call_notes_local_summary),
            modifier = Modifier.fillMaxWidth().background(cardColor, RoundedCornerShape(cardCornerExtraSmall))
                .padding(horizontal = 24.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
internal fun CallBackgroundRow(
    background: String?,
    saving: Boolean,
    onClick: () -> Unit,
) {
    val previewDescription = stringResource(R.string.contact_call_background_preview)
    val supporting = when {
        saving -> stringResource(R.string.contact_call_background_saving)
        background != null -> stringResource(R.string.contact_call_background_set)
        else -> stringResource(R.string.contact_call_background_none)
    }
    RillListItem(
        headline = stringResource(R.string.contact_call_background),
        supporting = supporting,
        leadingIcon = Icons.Rounded.Wallpaper,
        onClick = onClick,
        trailingContent = background?.let { image ->
            {
                AsyncImage(
                    model = image,
                    contentDescription = previewDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(width = 28.dp, height = 48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        },
    )
}

@Composable
internal fun QrCodeDialog(name: String, phone: String?, email: String?, onDismiss: () -> Unit) {
    val vCard = remember(name, phone, email) { QrCodeUtils.generateVCard(name, phone, email) }
    val qrBitmap = remember(vCard) { QrCodeUtils.generateQrCode(vCard, 600) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.contact_qr), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                qrBitmap?.let {
                    Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code", modifier = Modifier
                        .size(240.dp)
                        .background(Color.White, MaterialTheme.shapes.medium)
                        .padding(12.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                Text(phone ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp), shape = MaterialTheme.shapes.medium) { Text(stringResource(R.string.close)) }
            }
        }
    }
}

@Composable
internal fun SourceItem(
    modifier: Modifier,
    leadingIcon: ImageVector,
    headline: String,
    supporting: String?,
) {
    Surface(
        modifier = modifier,
        color = cardColor,
        shape = RoundedCornerShape(cardCornerExtraSmall),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun SourcesDialog(
    contact: Contact,
    sources: List<ContactsRepository.ContactSource>,
    navigator: DestinationsNavigator,
    navigateBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val contactsViewModel: ContactsViewModel = koinActivityViewModel()
    var sourceData by remember { mutableStateOf<Map<String, Contact>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sources) {
        isLoading = true
        val data = mutableMapOf<String, Contact>()
        sources.forEach { source ->
            try {
                val contactData = contactsViewModel.getRawContactData(source.rawContactId)
                if (contactData != null) {
                    data[source.rawContactId] = contactData
                }
            } catch (error: Exception) {
                android.util.Log.w(
                    "ContactDetails",
                    "Unable to load a raw contact source",
                    error,
                )
            }
        }
        sourceData = data
        isLoading = false
    }

    RillDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.contact_sources),
        icon = Icons.Rounded.PeopleAlt,
        confirmButton = {
            TextButton(onClick = onDismiss, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.accentText)) {
                Text(stringResource(R.string.close))
            }
        }
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(cardSpacedBy)
            ) {
                Text(
                    stringResource(R.string.unmerging_contacts_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 16.dp)
                )

                RillExpressiveCard(shape = MaterialTheme.shapes.large) {
                    sources.forEachIndexed { _, source ->
                        val account = if (!source.accountName.isNullOrEmpty()) {
                            Account(source.accountName, source.accountType ?: "")
                        } else {
                            null // If there is no name, do not create an account
                        }
                        val contactData = sourceData[source.rawContactId]

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(cardCornerExtraSmall),
                            colors = CardDefaults.cardColors(containerColor = cardColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismiss()
                                        navigator.navigate(
                                            ContactEditScreenDestination(
                                                contactId = contact.id,
                                                rawContactId = source.rawContactId,
                                                isEditingSource = true
                                            )
                                        )
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (contactData != null) {
                                    RillAvatar(
                                        name = contactData.displayName,
                                        photoUri = contactData.photoUri,
                                        modifier = Modifier.size(42.dp)
                                    )
                                } else {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = ContactUtils.getAccountIcon(account, contact.isPrivate),
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (contactData != null) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(start = 14.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        if (contactData.displayName.isNotBlank()) {
                                            Text(
                                                modifier = Modifier.padding(start = 2.dp),
                                                text = contactData.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        if (contactData.phoneDetails.isNotEmpty()) {
                                            Text(
                                                modifier = Modifier.padding(start = 2.dp),
                                                text = contactData.phoneDetails.joinToString(", ") { it.number },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        } else if (contactData.emails.isNotEmpty()) {
                                            Text(
                                                modifier = Modifier.padding(start = 2.dp),
                                                text =  contactData.emails.joinToString(", ") { it.value },
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                modifier = Modifier.size(16.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = ContactUtils.getAccountIcon(account, contact.isPrivate),
                                                        contentDescription = null,
                                                        modifier = Modifier.size(10.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = ContactUtils.getAccountName(account), // + "(${ContactUtils.getFriendlyAccountName(account)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            contactsViewModel.unmergeAll(contact.id)
                            onDismiss()
                            navigateBack()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(x = 0.dp, y = (-4).dp),
                        shape = MaterialTheme.shapes.large.copy(
                            topStart = CornerSize(cardCornerExtraSmall),
                            topEnd = CornerSize(cardCornerExtraSmall)
                        ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.CallSplit, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.unlink))
                    }
                }
            }
        }
    }
}
