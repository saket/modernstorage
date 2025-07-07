package com.google.modernstorage.storage

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.Path.Companion.toOkioPath
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetadataTest {
    private val appContext: Context by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext
    }
    private val fileSystem: AndroidFileSystem by lazy {
        AndroidFileSystem(appContext)
    }

    @Test fun read_file_extension_manually_when_MimeTypeMap_fails() {
        val dest = appContext.cacheDir
        val path = dest.toOkioPath() / "Mickey.17.2025.[YTS.MX].mkv"

        fileSystem.write(path) {
            writeUtf8("fin.")
        }

        val metadata = fileSystem.metadata(path)
        val mimeType = metadata.extra(MetadataExtras.MimeType::class)!!.value
        assertThat(mimeType).isEqualTo("video/x-matroska")

        fileSystem.delete(path)
    }
}
