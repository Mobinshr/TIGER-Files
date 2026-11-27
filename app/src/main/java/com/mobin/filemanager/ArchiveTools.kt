package com.mobin.filemanager

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal const val MAX_TEXT_EDITOR_BYTES = 1_048_576L
internal const val MAX_TEXT_EDITOR_CHARS = 1_048_576

private const val MAX_ARCHIVE_ENTRIES = 5000
private const val MAX_ARCHIVE_DEPTH = 32
private const val MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L

private val EDITABLE_TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "json", "xml", "html", "htm", "css", "js",
    "kt", "kts", "java", "py", "php", "csv", "log", "ini", "cfg",
    "yml", "yaml", "sh", "properties", "sql", "gradle", "toml", "svg"
)

internal fun isEditableTextFile(file: DocumentFile): Boolean =
    !cachedIsDirectory(file) &&
        cachedName(file).substringAfterLast('.', "").lowercase(Locale.ROOT) in EDITABLE_TEXT_EXTENSIONS

private fun safeArchiveSegment(value: String): String {
    val cleaned = value
        .filterNot { it == '/' || it == '\\' || it == '\u0000' || it == ':' }
        .trim()
    return cleaned.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "item"
}

private fun uniqueNameInFolder(folder: DocumentFile, desired: String): String {
    if (folder.findFile(desired) == null) return desired
    val dot = desired.lastIndexOf('.')
    val base = if (dot > 0) desired.substring(0, dot) else desired
    val extension = if (dot > 0) desired.substring(dot) else ""
    var index = 2
    while (folder.findFile("$base ($index)$extension") != null) index++
    return "$base ($index)$extension"
}

/**
 * Streams selected SAF documents directly into a ZIP. No full-file buffering is used.
 * The output is removed if any entry fails or safety limits are exceeded.
 */
internal fun createZipArchive(
    context: Context,
    sources: List<DocumentFile>,
    destination: DocumentFile,
    requestedName: String
): DocumentFile? {
    if (sources.isEmpty()) return null
    val base = requestedName.trim().removeSuffix(".zip").ifBlank { "FileManager_Archive" }
    val outputName = uniqueNameInFolder(destination, "$base.zip")
    val outputFile = destination.createFile("application/zip", outputName) ?: return null

    return try {
        val rawOutput = context.contentResolver.openOutputStream(outputFile.uri, "w")
            ?: throw IOException("Cannot open archive output")
        ZipOutputStream(BufferedOutputStream(rawOutput)).use { zip ->
            val usedPaths = HashSet<String>()
            val usedRoots = HashSet<String>()
            val visitedDirectories = HashSet<String>()
            val buffer = ByteArray(64 * 1024)
            var entryCount = 0
            var totalBytes = 0L

            fun addNode(node: DocumentFile, relativePath: String, depth: Int) {
                if (depth > MAX_ARCHIVE_DEPTH) throw IOException("Archive depth limit exceeded")
                val path = relativePath.trim('/').split('/').filter { it.isNotBlank() }
                    .joinToString("/") { safeArchiveSegment(it) }
                if (path.isBlank()) return

                if (node.isDirectory) {
                    val directoryUri = runCatching { node.uri.toString() }.getOrDefault("")
                    if (!visitedDirectories.add(directoryUri)) return
                    val directoryPath = "$path/"
                    if (usedPaths.add(directoryPath)) {
                        entryCount++
                        if (entryCount > MAX_ARCHIVE_ENTRIES) throw IOException("Too many archive entries")
                        zip.putNextEntry(ZipEntry(directoryPath))
                        zip.closeEntry()
                    }
                    val children = node.listFiles().toList()
                    children.forEach { child ->
                        val childName = safeArchiveSegment(child.name ?: "item")
                        addNode(child, "$path/$childName", depth + 1)
                    }
                } else {
                    if (!usedPaths.add(path)) return
                    entryCount++
                    if (entryCount > MAX_ARCHIVE_ENTRIES) throw IOException("Too many archive entries")
                    zip.putNextEntry(ZipEntry(path))
                    val input = context.contentResolver.openInputStream(node.uri)
                        ?: throw IOException("Cannot open source file")
                    input.use { stream ->
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            totalBytes += read.toLong()
                            if (totalBytes > MAX_ARCHIVE_BYTES) throw IOException("Archive size limit exceeded")
                            zip.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }

            sources.forEach { source ->
                val rawRoot = safeArchiveSegment(source.name ?: "item")
                var root = rawRoot
                var suffix = 2
                while (!usedRoots.add(root)) {
                    root = "$rawRoot ($suffix)"
                    suffix++
                }
                addNode(source, root, 0)
            }
        }
        outputFile
    } catch (_: Exception) {
        runCatching { outputFile.delete() }
        null
    }
}

/**
 * Extracts ZIP entries under a newly created folder. Absolute paths and parent traversal
 * are ignored; entry count, depth, and total uncompressed bytes are capped.
 */
internal fun extractZipArchive(
    context: Context,
    archive: DocumentFile,
    destination: DocumentFile
): Pair<DocumentFile, Int>? {
    val archiveName = archive.name.orEmpty()
    val baseName = archiveName.substringBeforeLast('.', "فایل‌های استخراج‌شده")
        .ifBlank { "فایل‌های استخراج‌شده" }
    val folderName = uniqueNameInFolder(destination, safeArchiveSegment(baseName))
    val outputFolder = destination.createDirectory(folderName) ?: return null

    return try {
        val rawInput = context.contentResolver.openInputStream(archive.uri)
            ?: throw IOException("Cannot open ZIP input")
        var entryCount = 0
        var extractedFiles = 0
        var totalBytes = 0L
        val buffer = ByteArray(64 * 1024)

        ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ARCHIVE_ENTRIES) throw IOException("Too many ZIP entries")

                val rawPath = entry.name.replace('\\', '/')
                val parts = rawPath.split('/').filter { it.isNotBlank() }
                val unsafe = rawPath.startsWith("/") ||
                    rawPath.startsWith("\\\\") ||
                    Regex("^[A-Za-z]:").containsMatchIn(rawPath) ||
                    parts.any { it == "." || it == ".." }

                if (!unsafe && parts.isNotEmpty() && parts.size <= MAX_ARCHIVE_DEPTH) {
                    var parent = outputFolder
                    parts.dropLast(1).forEach { rawSegment ->
                        val segment = safeArchiveSegment(rawSegment)
                        val existing = parent.findFile(segment)
                        parent = when {
                            existing?.isDirectory == true -> existing
                            existing == null -> parent.createDirectory(segment)
                            else -> parent.createDirectory(uniqueNameInFolder(parent, segment))
                        } ?: throw IOException("Cannot create extraction folder")
                    }

                    val leaf = safeArchiveSegment(parts.last())
                    if (entry.isDirectory || rawPath.endsWith("/")) {
                        val existing = parent.findFile(leaf)
                        if (existing == null) {
                            parent.createDirectory(leaf) ?: throw IOException("Cannot create directory")
                        } else if (!existing.isDirectory) {
                            parent.createDirectory(uniqueNameInFolder(parent, leaf))
                                ?: throw IOException("Cannot create directory")
                        }
                    } else {
                        val outputName = uniqueNameInFolder(parent, leaf)
                        val outputFile = parent.createFile(mimeForExtractedName(outputName), outputName)
                            ?: throw IOException("Cannot create extracted file")
                        val output = context.contentResolver.openOutputStream(outputFile.uri, "w")
                            ?: throw IOException("Cannot open extracted file")
                        output.use { stream ->
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                totalBytes += read.toLong()
                                if (totalBytes > MAX_ARCHIVE_BYTES) throw IOException("Extraction size limit exceeded")
                                stream.write(buffer, 0, read)
                            }
                        }
                        extractedFiles++
                    }
                }

                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        outputFolder to extractedFiles
    } catch (_: Exception) {
        deleteDocumentTree(outputFolder)
        null
    }
}

private fun mimeForExtractedName(name: String): String = when (
    name.substringAfterLast('.', "").lowercase(Locale.ROOT)
) {
    "txt", "md", "log", "csv" -> "text/plain"
    "html", "htm" -> "text/html"
    "css" -> "text/css"
    "js" -> "text/javascript"
    "json" -> "application/json"
    "xml" -> "application/xml"
    "pdf" -> "application/pdf"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    else -> "application/octet-stream"
}

private fun deleteDocumentTree(file: DocumentFile, depth: Int = 0) {
    if (depth > MAX_ARCHIVE_DEPTH + 8) return
    if (runCatching { file.isDirectory }.getOrDefault(false)) {
        runCatching { file.listFiles().forEach { child -> deleteDocumentTree(child, depth + 1) } }
    }
    runCatching { file.delete() }
}
