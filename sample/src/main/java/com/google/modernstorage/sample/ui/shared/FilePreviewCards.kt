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
package com.google.modernstorage.sample.ui.shared

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.modernstorage.storage.MetadataExtras.DisplayName
import com.google.modernstorage.storage.MetadataExtras.MimeType

@Composable
fun FileDetailsCard(fileDetails: FileDetails, preview: @Composable (() -> Unit)? = null) {
    val context = LocalContext.current

    val metadata = fileDetails.metadata
    val formattedFileSize = Formatter.formatShortFileSize(context, metadata.size ?: -1)
    val fileDescription = "${metadata.extra(MimeType::class)} - $formattedFileSize"

    Card(
        elevation = 0.dp,
        border = BorderStroke(width = 1.dp, color = Color.DarkGray),
        modifier = Modifier.padding(16.dp)
    ) {
        Column {
            Column(modifier = Modifier.padding(16.dp)) {
                metadata.extra(DisplayName::class)?.let {
                    Text(text = it.value, style = MaterialTheme.typography.subtitle2)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(text = fileDescription, style = MaterialTheme.typography.caption)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = fileDetails.uri.toString(), style = MaterialTheme.typography.caption)
            }

            if (preview != null) {
                preview()
            }
        }
    }
}

@Composable
fun MediaPreviewCard(fileDetails: FileDetails) {
    FileDetailsCard(fileDetails) {
        AsyncImage(
            modifier = Modifier.height(200.dp),
            model = fileDetails.uri,
            contentScale = ContentScale.FillWidth,
            contentDescription = null,
        )
    }
}
