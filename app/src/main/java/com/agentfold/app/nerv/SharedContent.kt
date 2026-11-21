package com.agentfold.app.nerv

import android.content.Context
import android.net.Uri
import java.io.File

object SharedContent {

    const val MAX_TEXT_CHARS = 4_000
    const val MAX_IMAGE_BYTES = 10L * 1024 * 1024

    fun sharedDir(context: Context): File = File(context.filesDir, "shared").apply { mkdirs() }

    fun isUrl(text: String): Boolean {
        val clean = text.trim().lowercase()
        return clean.startsWith("http://") || clean.startsWith("https://") || clean.startsWith("www.")
    }

    fun kindOf(text: String): String = if (isUrl(text)) "LINK" else "TEXT"

    fun preview(text: String, maxChars: Int = 500): String =
        if (text.length <= maxChars) text else text.take(maxChars) + "…"

    fun formatSize(bytes: Long): String = when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_024 * 1_024 -> "%.1f KB".format(bytes / 1_024.0)
        else -> "%.1f MB".format(bytes / (1_024.0 * 1_024.0))
    }

    fun saveSharedImage(context: Context, uri: Uri): File? {
        return try {
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            val stamp = System.currentTimeMillis()
            val file = File(sharedDir(context), "nerv-shared-$stamp.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > MAX_IMAGE_BYTES) {
                            try { file.delete() } catch (_: Exception) { }
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null
            file
        } catch (_: Exception) {
            null
        }
    }
}
