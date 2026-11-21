package com.agentfold.app.nerv

import android.content.Context
import java.io.File

class FolderManager(context: Context) {

    data class Item(
        val name: String,
        val isDir: Boolean,
        val size: Long,
        val modified: Long,
        val file: File
    )

    val root: File = context.filesDir
    private val rootCanonical: String = root.canonicalPath

    fun insideRoot(file: File): Boolean {
        return try {
            val path = file.canonicalPath
            path == rootCanonical || path.startsWith(rootCanonical + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    fun list(dir: File?): List<Item> {
        val target = dir ?: root
        if (!insideRoot(target) || !target.isDirectory) return emptyList()
        return (target.listFiles() ?: emptyArray())
            .map { Item(it.name, it.isDirectory, if (it.isDirectory) 0L else it.length(), it.lastModified(), it) }
            .sortedWith(compareByDescending<Item> { it.isDir }.thenBy { it.name.lowercase() })
    }

    fun parentOf(dir: File): File? {
        if (!insideRoot(dir)) return null
        val parent = dir.parentFile ?: return null
        return if (insideRoot(parent)) parent else null
    }

    fun createFolder(parent: File, name: String): String? {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 60) return "Folder name must be 1–60 characters."
        if (clean.contains("/") || clean.contains("\\") || clean == "." || clean == "..") return "That name is not allowed."
        if (!insideRoot(parent) || !parent.isDirectory) return "That folder is outside the app's files."
        val folder = File(parent, clean)
        if (!insideRoot(folder)) return "That folder is outside the app's files."
        if (folder.exists()) return "A file or folder with that name already exists."
        return if (folder.mkdir()) null else "Could not create the folder."
    }

    fun rename(file: File, newName: String): String? {
        val clean = newName.trim()
        if (clean.isEmpty() || clean.length > 60) return "Name must be 1–60 characters."
        if (clean.contains("/") || clean.contains("\\") || clean == "." || clean == "..") return "That name is not allowed."
        if (!insideRoot(file) || file.canonicalPath == rootCanonical) return "That file cannot be renamed."
        val target = File(file.parentFile, clean)
        if (!insideRoot(target)) return "That name is not allowed."
        if (target.exists()) return "A file or folder with that name already exists."
        return if (file.renameTo(target)) null else "Could not rename it."
    }

    fun move(file: File, targetDir: File): String? {
        if (!insideRoot(file) || file.canonicalPath == rootCanonical) return "That file cannot be moved."
        if (!insideRoot(targetDir) || !targetDir.isDirectory) return "That folder is outside the app's files."
        val target = File(targetDir, file.name)
        if (!insideRoot(target)) return "That folder is outside the app's files."
        if (target.exists()) return "A file with that name already exists there."
        return if (file.renameTo(target)) null else "Could not move it."
    }

    fun delete(file: File): Boolean {
        if (!insideRoot(file) || file.canonicalPath == rootCanonical) return false
        return deleteRecursive(file)
    }

    private fun deleteRecursive(file: File): Boolean {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        return file.delete()
    }

    fun readText(file: File, maxBytes: Int = 65536): String? {
        if (!insideRoot(file) || !file.isFile || file.length() > maxBytes) return null
        return try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1048576.0)
    }

    fun summary(): String {
        val items = list(null)
        val files = items.count { !it.isDir }
        val folders = items.count { it.isDir }
        val total = items.filter { !it.isDir }.sumOf { it.size }
        return "$folders folders · $files files · ${formatSize(total)}"
    }
}
