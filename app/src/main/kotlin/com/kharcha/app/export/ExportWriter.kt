package com.kharcha.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes export data to a URI provided by the Storage Access Framework.
 * Intended to be called on a background dispatcher; all I/O is blocking.
 */
class ExportWriter(private val context: Context) {

    /**
     * Write content to the given URI (from ACTION_CREATE_DOCUMENT result).
     * Throws if writing fails.
     *
     * @param uri The URI returned from the user's file picker
     * @param content The text content to write (CSV or JSON)
     * @param mimeType The MIME type (text/csv or application/json)
     */
    suspend fun write(uri: Uri, content: String, mimeType: String) {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Failed to open output stream for $uri")
        }
    }

    /**
     * Build an ACTION_CREATE_DOCUMENT intent for the user to pick a save location.
     *
     * @param filename Suggested filename (e.g., "kharcha-2026-01-01-to-2026-01-31.csv")
     * @param mimeType The MIME type (text/csv or application/json)
     */
    fun buildCreateDocumentIntent(filename: String, mimeType: String): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, filename)
        }
    }
}
