package dev.goodwy.rphone.controller.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.util.Log

private const val BLOCKED_NUMBERS_LOG_TAG = "BlockedNumbers"

data class BlockedNumber(
    val id: Long,
    val originalNumber: String,
    val strippedNumber: String?
)

object BlockedNumbersManager {

    fun canBlockNumbers(context: Context): Boolean {
        return try {
            BlockedNumberContract.canCurrentUserBlockNumbers(context)
        } catch (error: Exception) {
            Log.w(BLOCKED_NUMBERS_LOG_TAG, "Unable to query blocking capability", error)
            false
        }
    }

    fun isBlocked(context: Context, number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        return try {
            BlockedNumberContract.isBlocked(context, number)
        } catch (error: Exception) {
            Log.w(BLOCKED_NUMBERS_LOG_TAG, "Unable to query blocked-number state", error)
            false
        }
    }

    fun block(context: Context, number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        if (isBlocked(context, number)) return true
        return try {
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                values
            ) != null
        } catch (error: Exception) {
            Log.w(BLOCKED_NUMBERS_LOG_TAG, "Unable to block a number", error)
            false
        }
    }

    fun unblock(context: Context, number: String?): Boolean {
        if (number.isNullOrBlank()) return false
        return try {
            BlockedNumberContract.unblock(context, number)
            true
        } catch (error: Exception) {
            Log.w(BLOCKED_NUMBERS_LOG_TAG, "Unable to unblock a number", error)
            false
        }
    }

    fun unblockById(context: Context, id: Long): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                id
            )
            context.contentResolver.delete(uri, null, null) > 0
        } catch (error: Exception) {
            Log.w(BLOCKED_NUMBERS_LOG_TAG, "Unable to unblock a number by provider id", error)
            false
        }
    }

    fun getAll(context: Context): List<BlockedNumber> {
        val results = mutableListOf<BlockedNumber>()
        try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ID,
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                    BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER
                ),
                null,
                null,
                null
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
                val originalIndex = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER)
                val e164Index = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER)
                while (cursor.moveToNext()) {
                    val original = cursor.getString(originalIndex) ?: continue
                    results.add(
                        BlockedNumber(
                            id = cursor.getLong(idIndex),
                            originalNumber = original,
                            strippedNumber = cursor.getString(e164Index)
                        )
                    )
                }
            }
        } catch (error: Exception) {
            Log.w(BLOCKED_NUMBERS_LOG_TAG, "Unable to read blocked numbers", error)
        }
        return results
    }
}
