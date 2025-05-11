import org.jetbrains.dokka.gradle.DokkaMultiModuleTask

/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.9.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    }
}

plugins {
    id("org.jetbrains.dokka") version libs.versions.dokka
    id("me.tylerbwong.gradle.metalava") version "0.3.5" apply false
    id("com.vanniktech.maven.publish") version "0.31.0" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version libs.versions.kotlin apply false
    id("org.jetbrains.kotlin.plugin.compose") version libs.versions.kotlin apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.withType<DokkaMultiModuleTask>().configureEach {
    outputDirectory.set(rootProject.file("docs/api"))
    failOnWarning.set(true)
}

subprojects {
    if (project.hasProperty("POM_ARTIFACT_ID") && project.properties["POM_ARTIFACT_ID"] != "modernstorage-bom") {
        apply(plugin = "me.tylerbwong.gradle.metalava")

        metalava {
            filename = "api/current.api"
            reportLintsAsErrors = true
        }
    }
}

// Extension function due to metalava not having proper Kotlin DSL
fun Project.metalava(configure: Action<me.tylerbwong.gradle.metalava.extension.MetalavaExtension>): Unit =
    (this as ExtensionAware).extensions.configure("metalava", configure)
