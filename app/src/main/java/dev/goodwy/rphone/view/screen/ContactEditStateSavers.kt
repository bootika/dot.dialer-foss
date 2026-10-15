package dev.goodwy.rphone.view.screen

import android.accounts.Account
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal inline fun <reified T> contactEditListSaver(): Saver<SnapshotStateList<T>, String> =
    Saver(save = { Json.encodeToString(it.toList()) },
        restore = { mutableStateListOf<T>().apply { addAll(Json.decodeFromString<List<T>>(it)) } })

internal val contactEditUriSaver = Saver<Uri?, String>(
    save = { it?.toString().orEmpty() }, restore = { it.takeIf(String::isNotBlank)?.let(Uri::parse) })
internal val contactEditAccountSaver = Saver<Account?, ArrayList<String>>(
    save = { arrayListOf(it?.name.orEmpty(), it?.type.orEmpty()) },
    restore = { if (it[0].isBlank() || it[1].isBlank()) null else Account(it[0], it[1]) })
