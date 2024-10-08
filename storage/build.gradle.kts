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
plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

android {
    namespace = "com.google.modernstorage.storage"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}

dependencies {
    implementation(libs.coroutines.android)
    androidTestImplementation(libs.coroutines.android)

    implementation(androidx.corektx)
    implementation(androidx.appcompat)
    implementation(libs.okio)

    testImplementation(libs.junit)
    androidTestImplementation(androidx.junit)
    androidTestImplementation(androidx.junitktx)
    androidTestImplementation(androidx.espresso)
    androidTestImplementation(androidx.rules)
    androidTestImplementation(androidx.uiautomator)
    androidTestImplementation(androidx.runner)
    androidTestImplementation(androidx.assertk)
}
repositories {
    mavenCentral()
}
