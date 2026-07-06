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
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.test.core.app.launchActivityForResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DocumentAccessTest {
    private lateinit var device: UiDevice
    private lateinit var appContext: Context
    private lateinit var fileSystem: AndroidFileSystem
    private var pickerFolder: File? = null

    @get:Rule
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

    @After
    fun tearDown() {
        pickerFolder?.delete()
    }

    @Test
    fun listTreeNode() {
        if (Build.VERSION.SDK_INT >= 29) {
            listTreeNodeDirectly()
        } else {
            listTreeNodeUsingPicker()
        }
    }

    private fun listTreeNodeDirectly() {
        // Build a tree URI for a directory accessible to the test app's context.
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Documents"
        )

        // Grant URI permission through shell identity so the tree can be accessed.
        val uiAutomation = getInstrumentation().uiAutomation
        uiAutomation.adoptShellPermissionIdentity()
        try {
            // Avoid an IllegalArgumentException when listing a tree URI
            // content://com.android.externalstorage.documents/tree/primary%3ADocuments
            fileSystem.listRecursively(treeUri.toOkioPath())
        } finally {
            uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun listTreeNodeUsingPicker() {
        val rootLabel = "Downloads"
        val folderName = "modernstorage-${System.currentTimeMillis()}"
        pickerFolder = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            folderName
        ).also { directory ->
            check(directory.mkdirs()) { "Failed to create $directory" }
        }
        val treeIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(
                DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildRootUri("com.android.providers.downloads.documents", "downloads")
            )
        }

        val scenario = launchActivityForResult<TestingActivity>()
        scenario.onActivity { testingActivity ->
            testingActivity.startActivityForResult(treeIntent, 545)
        }

        val folder = device.findObject(UiSelector().text(folderName))
        if (!folder.waitForExists(5_000)) {
            openNavigationDrawer()
            device.findObject(UiSelector().text(rootLabel)).apply {
                check(waitForExists(5_000)) { "$rootLabel root not found" }
                click()
            }
            check(folder.waitForExists(5_000)) { "$folderName folder not found" }
        }
        folder.click()

        waitForSelectFolderButton().click()

        device.findObject(UiSelector().text("ALLOW")).apply {
            if (waitForExists(5_000)) {
                click()
            }
        }

        val uri = scenario.result.resultData.data!!

        // Avoid an IllegalArgumentException when listing a tree URI
        fileSystem.listRecursively(uri.toOkioPath())
    }

    /**
     * Opens the system picker navigation drawer so storage roots such as Downloads
     * can be selected. Different picker versions expose different drawer affordances,
     * so accessibility labels are tried before failing.
     */
    private fun openNavigationDrawer() {
        val showRoots = device.findObject(UiSelector().descriptionContains("Show roots"))
        if (showRoots.waitForExists(2_000)) {
            showRoots.click()
            return
        }

        val openDrawer = device.findObject(UiSelector().descriptionContains("Open navigation drawer"))
        if (openDrawer.waitForExists(2_000)) {
            openDrawer.click()
            return
        }

        error("Picker navigation drawer button not found")
    }

    private fun waitForSelectFolderButton(): UiObject {
        val useThisFolder = device.findObject(UiSelector().text("USE THIS FOLDER"))
        if (useThisFolder.waitForExists(5_000)) {
            return useThisFolder
        }

        val select = device.findObject(UiSelector().text("SELECT"))
        if (select.waitForExists(2_000)) {
            return select
        }

        val button = device.findObject(UiSelector().resourceId("android:id/button1"))
        check(button.waitForExists(2_000)) { "Select folder button not found" }
        return button
    }
}
