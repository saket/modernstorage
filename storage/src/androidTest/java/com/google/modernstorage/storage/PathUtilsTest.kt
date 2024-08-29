/*
 * Copyright 2024 Google LLC
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
