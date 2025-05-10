/*
 * Copyright 2025 Google LLC
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
import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.test.core.app.launchActivityForResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentAccessTest {
    private lateinit var device: UiDevice
    private lateinit var appContext: Context
    private lateinit var fileSystem: AndroidFileSystem

    @get:Rule(order = 1)
    var storagePermission = GrantPermissionRule.grant(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    @Before
    fun setup() {
        device = UiDevice.getInstance(getInstrumentation())
        appContext = getInstrumentation().targetContext
        fileSystem = AndroidFileSystem(appContext)
    }

    @Test
    fun listTreeNode() {
        val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                "content://com.android.externalstorage.documents/tree/primary".toUri()
            )
        }

        val scenario = launchActivityForResult<TestingActivity>()
        scenario.onActivity { testingActivity ->
            testingActivity.startActivityForResult(treeIntent, 545)
        }

        // content://com.android.externalstorage.documents/tree/primary%3ADocuments
        device.findObject(UiSelector().text("Documents")).apply {
            // May not show on second run
            if (waitForExists(2_000)) {
                click()
            }
        }
        device.findObject(UiSelector().text("USE THIS FOLDER")).apply {
            click()
        }
        device.findObject(UiSelector().text("ALLOW")).apply {
            click()
        }

        val uri = scenario.result.resultData.data!!

        // No results, so not recursive
        // Ensure we avoid an IllegalArgumentException for listing a tree URI
        // content:/com.android.externalstorage.documents/tree/10EC-2814%3APodcasts
        fileSystem.listRecursively(uri.toOkioPath())
    }
}
