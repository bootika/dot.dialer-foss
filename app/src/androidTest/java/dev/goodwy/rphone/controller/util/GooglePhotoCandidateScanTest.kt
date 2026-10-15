package dev.goodwy.rphone.controller.util

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in, local-only scan. No contact names, phone numbers or photo bytes are logged. */
@RunWith(AndroidJUnit4::class)
class GooglePhotoCandidateScanTest {
    @Test
    fun findSmallLocalGooglePhotos() {
        runBlocking {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(args.getString("runGooglePhotoCandidateScan") == "true")
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val preferences = PreferenceManager(context)
            assertTrue(preferences.getBoolean(GoogleContactPhotoPreferences.ENABLED, false))
            val accountName = requireNotNull(
                preferences.getString(GoogleContactPhotoPreferences.ACCOUNT, null),
            )
            val resolver = context.contentResolver

            val googleContactIds = mutableSetOf<Long>()
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} = ? AND " +
                    "${ContactsContract.RawContacts.ACCOUNT_NAME} = ? AND " +
                    "${ContactsContract.RawContacts.DELETED} = 0",
                arrayOf("com.google", accountName),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) googleContactIds += cursor.getLong(0)
            }

            val candidates = mutableListOf<Candidate>()
            resolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.PHOTO_ID,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(0)
                    if (contactId !in googleContactIds || cursor.isNull(1)) continue
                    val uri = ContentUris.withAppendedId(
                        ContactsContract.Contacts.CONTENT_URI,
                        contactId,
                    )
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    ContactsContract.Contacts.openContactPhotoInputStream(resolver, uri, true)
                        ?.use { BitmapFactory.decodeStream(it, null, options) }
                    val localEdge = maxOf(options.outWidth, options.outHeight)
                    if (localEdge <= 0 || localEdge >= 512) continue

                    val numbers = mutableSetOf<String>()
                    resolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(contactId.toString()),
                        null,
                    )?.use { phones ->
                        while (phones.moveToNext()) {
                            phones.getString(0)?.filter(Char::isDigit)
                                ?.takeIf { it.length >= 9 }
                                ?.let(numbers::add)
                        }
                    }
                    // One number avoids ambiguous contact matching and unnecessary Google lookups.
                    if (numbers.size == 1) {
                        candidates += Candidate(contactId, localEdge)
                    }
                }
            }
            candidates.sortWith(compareBy<Candidate> { it.localEdge }.thenBy { it.contactId })
            Log.i(TAG, "googleContacts=${googleContactIds.size}; smallSingleNumber=${candidates.size}")
            assertTrue("No Google contact with a small local photo and one number", candidates.isNotEmpty())
            for (candidate in candidates.take(MAX_DISPLAYED_CANDIDATES)) {
                Log.i(TAG, "candidateId=${candidate.contactId}; localEdge=${candidate.localEdge}")
            }
        }
    }

    private data class Candidate(val contactId: Long, val localEdge: Int)

    private companion object {
        const val TAG = "PhotoCandidateScan"
        const val MAX_DISPLAYED_CANDIDATES = 10
    }
}
