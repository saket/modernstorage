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

import android.content.ContentResolver
import android.net.Uri
import okio.Path
import okio.Path.Companion.toPath

fun Path.toUri(): Uri {
    val str = this.toString()

    if (str.startsWith("content:/")) {
        return Uri.parse(str.replace("content:/", "content://"))
    }

    val uri = Uri.parse(str)
    return if (uri.isPhysicalFile()) Uri.fromFile(toFile()) else uri
}

fun Uri.toOkioPath(): Path {
    asPhysicalPathOrNull()?.let {
        return it
    }
    return this.toString().toPath(false)
}

@Deprecated(
    "Use the Uri.toOkioPath() method instead",
    ReplaceWith("toOkioPath()"),
    DeprecationLevel.WARNING
)
fun Uri.toPath() = this.toOkioPath()

private fun Uri.asPhysicalPathOrNull(): Path? {
    if (!isPhysicalFile()) {
        return null
    }
    return when (scheme) {
        ContentResolver.SCHEME_FILE -> path!!.toPath()
        else -> toString().toPath() // Probably represents a literal path on disk.
    }
}

// Rules taken from Uri.fromFile() and combined with Coil's:
// https://github.com/coil-kt/coil/blob/da3736114ec3ae4e86cbc0768ec98808f90dca2a/coil-base/src/main/java/coil/map/FileUriMapper.kt#L24
internal fun Uri.isPhysicalFile(): Boolean {
    return (scheme == null || scheme == ContentResolver.SCHEME_FILE) &&
        authority.isNullOrBlank() &&
        path?.startsWith("/") == true &&
        pathSegments.isNotEmpty() &&
        pathSegments.first() != "android_asset"
}
