package dev.goodwy.rphone.controller

import dev.goodwy.rphone.modal.data.CallLogEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogSnapshotTest {
    private val cached = entry(photoUri = null, contactId = "old-id")

    @Test
    fun `photo changes invalidate the snapshot`() {
        assertTrue(
            callLogSnapshotsDiffer(
                cached = listOf(cached),
                fresh = listOf(cached.copy(photoUri = "content://contacts/photo")),
            ),
        )
    }

    @Test
    fun `contact id changes invalidate the snapshot`() {
        assertTrue(
            callLogSnapshotsDiffer(
                cached = listOf(cached),
                fresh = listOf(cached.copy(contactId = "current-id")),
            ),
        )
    }

    @Test
    fun `identical snapshots do not trigger a redundant update`() {
        assertFalse(callLogSnapshotsDiffer(listOf(cached), listOf(cached.copy())))
    }

    private fun entry(photoUri: String?, contactId: String?) = CallLogEntry(
        id = 1,
        number = "0721234567",
        name = "Maria",
        type = 1,
        date = 1_000L,
        duration = 0L,
        photoUri = photoUri,
        contactId = contactId,
    )
}
