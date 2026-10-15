package dev.goodwy.rphone.controller.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class GoogleContactAccounts(private val context: Context) {
    suspend fun load(): List<String> = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext emptyList()
        }
        val accounts = linkedSetOf<String>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                "${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf("com.google"),
            ContactsContract.RawContacts.ACCOUNT_NAME + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.takeIf(String::isNotBlank)?.let(accounts::add)
            }
        }
        accounts.toList()
    }
}
