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

import android.Manifest
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.runBlocking
import okio.Path
import okio.Path.Companion.toOkioPath
import okio.Path.Companion.toPath
import okio.source
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

@RunWith(AndroidJUnit4::class)
class MediaStoreTest {
    private lateinit var appContext: Context
    private lateinit var fileSystem: AndroidFileSystem

    @get:Rule
    var readStoragePermission = GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)
    @get:Rule
    var writeStoragePermission = GrantPermissionRule.grant(Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @Before
    fun setup() {
        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        fileSystem = AndroidFileSystem(appContext)
    }

    private fun addFileFromAssets(
        extension: String,
        mimeType: String,
        collection: Uri,
        relativePath: String? = null,
        expectedPath: String,
    ) {
        val filename = "added-${System.currentTimeMillis()}.$extension"
        val uri = fileSystem.createMediaStoreUri(
            filename = filename,
            collection = collection,
            relativePath = relativePath,
        )!!
        val path = uri.toOkioPath()

        fileSystem.write(path, false) {
            appContext.assets.open("sample.$extension").source().use { source ->
                writeAll(source)
            }
        }

        runBlocking {
            requireNotNull(fileSystem.scanUri(uri, mimeType))
        }

        val metadata = fileSystem.metadataOrNull(path)
        requireNotNull(metadata)

        Assert.assertEquals(filename, metadata.extra(MetadataExtras.DisplayName::class)!!.value)
        Assert.assertEquals(mimeType, metadata.extra(MetadataExtras.MimeType::class)!!.value)
        Assert.assertEquals(
            (expectedPath.toPath() / filename).toString(),
            metadata.extra(MetadataExtras.FilePath::class)!!.value
        )

        verifyBytes(appContext.assets.open("sample.$extension"), path)
        appContext.contentResolver.delete(uri, null, null)
    }

    private fun verifyBytes(original: InputStream, target: Path) {
        original.use { inputStream ->
            val iterator = inputStream.readBytes().iterator()
            fileSystem.read(target) {
                do {
                    val a = iterator.next()
                    val b = this.readByte()
                    Assert.assertEquals(a, b)
                } while (iterator.hasNext() && !this.exhausted())
            }
        }
    }

    @Test
    fun addImage() {
        addFileFromAssets(
            extension = "jpg",
            mimeType = "image/jpeg",
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            expectedPath = "/storage/emulated/0/Pictures/"
        )
    }

    @Test
    fun addVideo() {
        addFileFromAssets(
            extension = "mp4",
            mimeType = "video/mp4",
            collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            expectedPath = "/storage/emulated/0/Movies/",
        )
    }

    @Test
    fun addAudio() {
        addFileFromAssets(
            extension = "wav",
            mimeType = "audio/x-wav",
            collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
            expectedPath = "/storage/emulated/0/Music/",
        )
    }

    @Test
    fun addText() {
        addFileFromAssets(
            extension = "txt",
            mimeType = "text/plain",
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            expectedPath = "/storage/emulated/0/Download/",
        )
    }

    @Test
    fun addPdf() {
        addFileFromAssets(
            extension = "pdf",
            mimeType = "application/pdf",
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            expectedPath = "/storage/emulated/0/Download/",
        )
    }

    @Test
    fun addZip() {
        addFileFromAssets(
            extension = "zip",
            mimeType = "application/zip",
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            expectedPath = "/storage/emulated/0/Download/",
        )
    }

    @Test fun add_file_with_a_relative_path() {
        addFileFromAssets(
            extension = "jpg",
            mimeType = "image/jpeg",
            collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL),
            relativePath = "${Environment.DIRECTORY_DOWNLOADS}/modernstorage",
            expectedPath = "/storage/emulated/0/Download/modernstorage/",
        )
    }

    @Test
    @SdkSuppress(maxSdkVersion = 28)
    fun add_file_with_a_relative_path_on_legacy_media_store() {
        addFileFromAssets(
            extension = "jpg",
            mimeType = "image/jpeg",
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            relativePath = "${Environment.DIRECTORY_DOWNLOADS}/modernstorage",
            expectedPath = "/storage/emulated/0/Download/modernstorage/",
        )
    }

    @Test
    fun copyImageFromInternalStorage() {
        val internalFile = File(appContext.filesDir, "internal-${System.currentTimeMillis()}.jpg").also {
            appContext.assets.open("sample.jpg").copyTo(it.outputStream())
        }

        val uri = fileSystem.createMediaStoreUri(
            filename = "added-${System.currentTimeMillis()}.jpg",
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            relativePath = null,
        )!!
        val path = uri.toOkioPath()

        fileSystem.copy(internalFile.toOkioPath(), path)
        verifyBytes(internalFile.inputStream(), path)

        internalFile.delete()
        appContext.contentResolver.delete(uri, null, null)
    }

    @Test
    fun copyTextFromInternalStorage() {
        val internalFile = File(appContext.filesDir, "internal-${System.currentTimeMillis()}.txt").also {
            appContext.assets.open("sample.txt").copyTo(it.outputStream())
        }

        val uri = fileSystem.createMediaStoreUri(
            filename = "added-${System.currentTimeMillis()}.txt",
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            relativePath = null,
        )!!
        val path = uri.toOkioPath()

        fileSystem.copy(internalFile.toOkioPath(), path)
        verifyBytes(internalFile.inputStream(), path)

        internalFile.delete()
        appContext.contentResolver.delete(uri, null, null)
    }

    @Test
    fun copyPdfFromInternalStorage() {
        val internalFile = File(appContext.filesDir, "internal-${System.currentTimeMillis()}.pdf").also {
            appContext.assets.open("sample.pdf").copyTo(it.outputStream())
        }

        val uri = fileSystem.createMediaStoreUri(
            filename = "added-${System.currentTimeMillis()}.pdf",
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            relativePath = null,
        )!!
        val path = uri.toOkioPath()

        fileSystem.copy(internalFile.toOkioPath(), path)
        verifyBytes(internalFile.inputStream(), path)

        internalFile.delete()
        appContext.contentResolver.delete(uri, null, null)
    }
}
