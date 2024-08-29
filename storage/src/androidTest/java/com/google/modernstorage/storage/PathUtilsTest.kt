package com.google.modernstorage.storage

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import assertk.assertThat
import assertk.assertions.isEqualTo
import okio.Path.Companion.toPath
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathUtilsTest {
    @Test fun canary() {
        val uri = Uri.parse("content://com.example.foo/path/to/image.jpg")
        assertThat(uri.toOkioPath().toUri()).isEqualTo(uri)
    }

    @Test fun file_paths() {
        val path = "/data/user/0/com.example.test/cache/image.jpg".toPath()
        assertThat(path.toUri()).isEqualTo(Uri.fromFile(path.toFile()))
    }
}
