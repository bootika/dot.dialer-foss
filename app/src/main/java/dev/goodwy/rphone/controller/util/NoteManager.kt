package dev.goodwy.rphone.controller.util

import android.content.Context
import android.util.Log
import java.io.File

object NoteManager {

    private const val LOG_TAG = "NoteManager"

    fun getNotesDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "Notes")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()

    fun getFileName(contactName: String, phoneNumber: String): String {
        val safeName = sanitizeFileName(contactName.ifBlank { "Unknown" })
        val safeNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        return "$safeName - [$safeNumber].txt"
    }

    // Search for a file using ONLY the phone number (ignore the name)
    fun findNoteFileByNumber(context: Context, phoneNumber: String): File? {
        val safeNumber = phoneNumber.filter { it.isDigit() || it == '+' }
        if (safeNumber.isEmpty()) return null
        return try {
            getNotesDir(context).listFiles()
                ?.firstOrNull { file ->
                    file.extension == "txt" &&
                            file.nameWithoutExtension.contains("[$safeNumber]")
                }
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to search the notes directory", error)
            null
        }
    }

    // Get the file to save (with the current contact name)
    fun getNoteFile(context: Context, contactName: String, phoneNumber: String): File {
        // First, let's try to find an existing file by its number
        val existingFile = findNoteFileByNumber(context, phoneNumber)
        return if (existingFile != null) {
            // If an existing file is found but its name has changed, rename it
            val expectedName = getFileName(contactName, phoneNumber)
            if (existingFile.name != expectedName) {
                val newFile = File(existingFile.parent, expectedName)
                existingFile.renameTo(newFile)
                newFile
            } else {
                existingFile
            }
        } else {
            // If the file doesn't exist, create a new one with the current name
            File(getNotesDir(context), getFileName(contactName, phoneNumber))
        }
    }

    fun readNote(context: Context, contactName: String, phoneNumber: String): String {
        val file = findNoteFileByNumber(context, phoneNumber)
        return try {
            file?.readText() ?: ""
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to read a contact note", error)
            ""
        }
    }

    // A simplified method for looking up items by number only
    fun readNoteByPhone(context: Context, phoneNumber: String): String {
        val file = findNoteFileByNumber(context, phoneNumber)
        return try {
            file?.readText() ?: ""
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to read a note by phone association", error)
            ""
        }
    }

    fun writeNote(context: Context, contactName: String, phoneNumber: String, content: String) {
        if (content.isBlank()) {
            deleteNote(context, phoneNumber)
            return
        }
        try {
            val file = getNoteFile(context, contactName, phoneNumber)
            file.parentFile?.mkdirs()
            file.writeText(content)
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to persist a contact note", error)
        }
    }

    fun deleteNote(context: Context, phoneNumber: String) {
        try {
            findNoteFileByNumber(context, phoneNumber)?.delete()
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to delete a contact note", error)
        }
    }

    fun deleteNoteFile(file: File) {
        try {
            file.delete()
        } catch (error: Exception) {
            Log.w(LOG_TAG, "Unable to delete a note file", error)
        }
    }

    fun getAllNotes(context: Context): List<NoteEntry> = try {
        getNotesDir(context).listFiles()
            ?.filter { it.extension == "txt" }
            ?.sortedByDescending { it.lastModified() }
            ?.mapNotNull { file ->
                try {
                    val base = file.nameWithoutExtension
                    val matchResult = Regex("(.*) - \\[(.*)\\]").find(base)
                    if (matchResult != null) {
                        val (contactName, phoneNumber) = matchResult.destructured
                        NoteEntry(
                            file = file,
                            contactName = contactName,
                            phoneNumber = phoneNumber,
                            content = file.readText(),
                            lastModified = file.lastModified()
                        )
                    } else {
                        null
                    }
                } catch (error: Exception) {
                    Log.w(LOG_TAG, "Unable to read one note while listing notes", error)
                    null
                }
            } ?: emptyList()
    } catch (error: Exception) {
        Log.w(LOG_TAG, "Unable to list contact notes", error)
        emptyList()
    }

    // Get all notes for the room list
    fun getAllNotesForNumbers(context: Context, phoneNumbers: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        phoneNumbers.forEach { phoneNumber ->
            val note = readNoteByPhone(context, phoneNumber)
            if (note.isNotBlank()) {
                result[phoneNumber] = note
            }
        }
        return result
    }
}

data class NoteEntry(
    val file: File,
    val contactName: String,
    val phoneNumber: String,
    val content: String,
    val lastModified: Long
)
