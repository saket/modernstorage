/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.modernstorage.storage

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.Path
import okio.Sink
import okio.Source
import okio.sink
import okio.source
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.coroutines.resume

class AndroidFileSystem(private val context: Context) : FileSystem() {
    private val contentResolver = context.contentResolver
    private val physicalFileSystem: FileSystem by lazy(NONE) { SYSTEM }

    private fun isPhysicalFile(file: Path): Boolean {
        return file.toString().first() == '/'
    }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        if (isPhysicalFile(file)) {
            return physicalFileSystem.appendingSink(file)
        }

        if (!mustExist) {
            throw IOException("Appending on an inexisting path isn't supported ($file)")
        }

        val uri = file.toUri()
        val outputStream = contentResolver.openOutputStream(uri, "a")

        if (outputStream == null) {
            throw IOException("Couldn't open an OutputStream ($file)")
        } else {
            return outputStream.sink()
        }
    }

    /**
     * Not yet implemented
     */
    override fun atomicMove(source: Path, target: Path) {
        TODO("Not yet implemented")
    }

    override fun canonicalize(path: Path): Path {
        throw UnsupportedOperationException("Paths can't be canonicalized in AndroidFileSystem")
    }

    /**
     * Not yet implemented for Uris.
     */
    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        if (isPhysicalFile(dir)) {
            physicalFileSystem.createDirectory(dir, mustCreate)
        } else {
            TODO("Not yet implemented")
        }
    }

    override fun createSymlink(source: Path, target: Path) {
        throw UnsupportedOperationException("Symlinks can't be created in AndroidFileSystem")
    }

    override fun delete(path: Path, mustExist: Boolean) {
        if (isPhysicalFile(path)) {
            physicalFileSystem.delete(path, mustExist)
        } else {
            val uri = path.toUri()
            val deletedRows = contentResolver.delete(uri, null, null)

            if (deletedRows == 0) {
                throw IOException("failed to delete $path")
            }
        }
    }

    override fun list(dir: Path): List<Path> {
        return if (isPhysicalFile(dir)) {
            physicalFileSystem.list(dir)
        } else {
            listDocumentProvider(dir, throwOnFailure = true)!!
        }
    }

    override fun listOrNull(dir: Path): List<Path>? {
        return if (isPhysicalFile(dir)) {
            physicalFileSystem.listOrNull(dir)
        } else {
            listDocumentProvider(dir, throwOnFailure = false)
        }
    }

    private fun listDocumentProvider(dir: Path, throwOnFailure: Boolean): List<Path>? {
        // TODO: Verify path is a directory
        val rootUri = dir.toUri()
        val documentId = DocumentsContract.getDocumentId(rootUri)
        val treeUri = DocumentsContract.buildChildDocumentsUriUsingTree(rootUri, documentId)

        val cursor = contentResolver.query(
            treeUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null,
            null
        )

        if (cursor == null) {
            if (throwOnFailure) {
                throw IOException("failed to list $dir")
            } else {
                return null
            }
        }

        val result = mutableListOf<Path>()

        cursor.use { cursor ->
            while (cursor.moveToNext()) {
                result.add(DocumentsContract.buildDocumentUriUsingTree(rootUri, documentId).toOkioPath())
            }
        }

        return result
    }

    override fun metadataOrNull(path: Path): FileMetadata? {
        val uri = path.toUri()

        return when {
            uri.isPhysicalFile() -> fetchMetadataFromPhysicalFile(path)
            uri.authority == MediaStore.AUTHORITY -> fetchMetadataFromMediaStore(path, uri)
            else -> fetchMetadataFromDocumentProvider(path, uri)
        }
    }

    private fun fetchMetadataFromPhysicalFile(path: Path): FileMetadata? {
        val metadata = physicalFileSystem.metadataOrNull(path) ?: return null

        val fileExtension: String = MimeTypeMap.getFileExtensionFromUrl(path.toString())
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase(Locale.getDefault()))

        val androidExtras = mutableMapOf(
            Path::class to path,
            MetadataExtras.DisplayName::class to MetadataExtras.DisplayName(path.name),
            MetadataExtras.FilePath::class to MetadataExtras.FilePath(path.toFile().absolutePath),
        )
        if (mimeType != null) {
            androidExtras[MetadataExtras.MimeType::class] = MetadataExtras.MimeType(mimeType)
        }

        return metadata.copy(
            extras = metadata.extras + androidExtras
        )
    }

    private fun fetchMetadataFromMediaStore(path: Path, uri: Uri): FileMetadata? {
        if (uri.pathSegments.firstOrNull().isNullOrBlank()) {
            return null
        }

        val isPhotoPickerUri = uri.pathSegments.firstOrNull() == "picker"

        val projection = if (isPhotoPickerUri) {
            arrayOf(
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATA,
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATA,
            )
        }

        val cursor = contentResolver.query(
            uri,
            projection,
            null,
            null,
            null
        ) ?: return null

        cursor.use { cursor ->
            if (!cursor.moveToNext()) {
                return null
            }

            val createdTime: Long
            var lastModifiedTime: Long? = null

            if (isPhotoPickerUri) {
                createdTime = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
            } else {
                createdTime = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED))
                lastModifiedTime = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
            }

            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
            val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE))
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
            val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))

            return FileMetadata(
                isRegularFile = true,
                isDirectory = false,
                symlinkTarget = null,
                size = size,
                createdAtMillis = createdTime,
                lastModifiedAtMillis = lastModifiedTime,
                lastAccessedAtMillis = null,
                extras = mapOf(
                    Path::class to path,
                    Uri::class to uri,
                    MetadataExtras.DisplayName::class to MetadataExtras.DisplayName(displayName),
                    MetadataExtras.MimeType::class to MetadataExtras.MimeType(mimeType),
                    MetadataExtras.FilePath::class to MetadataExtras.FilePath(filePath),
                )
            )
        }
    }

    private fun fetchMetadataFromDocumentProvider(path: Path, uri: Uri): FileMetadata? {
        val cursor = contentResolver.query(
            uri,
            null,
            null,
            null,
            null
        ) ?: return null

        cursor.use { cursor ->
            if (!cursor.moveToNext()) {
                return null
            }

            val displayName = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            val size = cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))

            // These two columns are optional and may not be implemented by the providing app.
            val lastModifiedTime = cursor.getLongOrNull(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
            val mimeType = cursor.getStringOrNull(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE))
                ?: contentResolver.getType(uri)

            val isFolder = mimeType == DocumentsContract.Document.MIME_TYPE_DIR ||
                mimeType == DocumentsContract.Root.MIME_TYPE_ITEM

            return FileMetadata(
                isRegularFile = !isFolder,
                isDirectory = isFolder,
                symlinkTarget = null,
                size = size,
                createdAtMillis = null,
                lastModifiedAtMillis = lastModifiedTime,
                lastAccessedAtMillis = null,
                extras = buildMap {
                    put(Path::class, path)
                    put(Uri::class, uri)
                    put(MetadataExtras.DisplayName::class, MetadataExtras.DisplayName(displayName))
                    if (mimeType != null) {
                        put(MetadataExtras.MimeType::class, MetadataExtras.MimeType(mimeType))
                    }
                }
            )
        }
    }

    /**
     * Not yet implemented
     */
    override fun openReadOnly(file: Path): FileHandle {
        TODO("Not yet implemented")
    }

    /**
     * Not yet implemented
     */
    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle {
        TODO("Not yet implemented")
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        if (isPhysicalFile(file)) {
            return physicalFileSystem.sink(file, mustCreate)
        }

        if (mustCreate) {
            throw IOException("Path creation isn't supported ($file)")
        }

        val uri = file.toUri()
        val outputStream = contentResolver.openOutputStream(uri)

        if (outputStream == null) {
            throw IOException("Couldn't open an OutputStream ($file)")
        } else {
            return outputStream.sink()
        }
    }

    override fun source(file: Path): Source {
        if (isPhysicalFile(file)) {
            return physicalFileSystem.source(file)
        }

        val uri = file.toUri()
        val inputStream = contentResolver.openInputStream(uri)

        if (inputStream == null) {
            throw IOException("Couldn't open an InputStream ($file)")
        } else {
            return inputStream.source()
        }
    }

    @Deprecated(
        "Use the updated createMediaStoreUri() method",
        ReplaceWith("createMediaStoreUri(filename, collection, directory)"),
        DeprecationLevel.WARNING
    )
    fun createMediaStoreUri(filename: String, directory: String): Uri? {
        val newEntry = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.DATA, "$directory/$filename")
        }

        return context.contentResolver.insert(MediaStore.Files.getContentUri("external"), newEntry)
    }

    fun createMediaStoreUri(
        filename: String,
        collection: Uri = MediaStore.Files.getContentUri("external"),
        directory: String?
    ): Uri? {
        val newEntry = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            if (directory !== null) put(MediaStore.MediaColumns.DATA, "$directory/$filename")
        }

        return context.contentResolver.insert(collection, newEntry)
    }

    suspend fun scanUri(uri: Uri, mimeType: String): Uri? {
        val cursor = contentResolver.query(
            uri,
            arrayOf(MediaStore.Files.FileColumns.DATA),
            null,
            null,
            null
        ) ?: throw Exception("Uri $uri could not be found")

        val path = cursor.use {
            if (!cursor.moveToFirst()) {
                throw Exception("Uri $uri could not be found")
            }

            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
        }

        return suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(path),
                arrayOf(mimeType)
            ) { _, scannedUri ->
                if (scannedUri == null) {
                    continuation.cancel(Exception("File $path could not be scanned"))
                } else {
                    continuation.resume(scannedUri)
                }
            }
        }
    }

    suspend fun scanFile(file: File, mimeType: String): Uri? {
        return suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.toString()),
                arrayOf(mimeType)
            ) { _, scannedUri ->
                if (scannedUri == null) {
                    continuation.cancel(Exception("File $file could not be scanned"))
                } else {
                    continuation.resume(scannedUri)
                }
            }
        }
    }
}
