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
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.google.modernstorage.sample"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.google.modernstorage.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
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

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = compose.versions.compiler.get()
    }

    packagingOptions {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(androidx.corektx)
    implementation(androidx.appcompat)
    implementation(androidx.material)
    implementation(compose.ui)
    implementation(compose.material)
    implementation(compose.icons)
    implementation(compose.uitoolingpreview)
    implementation(androidx.lifecycleruntimektx)
    implementation(compose.activity)
    implementation(compose.navigation)

    /*
     * In a real world project you can use the BOM to import the different dependencies without needing
     * to define version for each of them
     * implementation("me.saket.modernstorage:modernstorage-bom:{{ version }}")
     * implementation("me.saket.modernstorage:modernstorage-permissions")
     * implementation("me.saket.modernstorage:modernstorage-photopicker")
     * implementation("com.squareup.okio:okio")
     * implementation("me.saket.modernstorage:modernstorage-storage")
     */
    implementation(project(":permissions"))
    implementation(libs.okio)
    implementation(project(":storage"))

    implementation(libs.coil)

    testImplementation(libs.junit)
    androidTestImplementation(androidx.junit)
    androidTestImplementation(androidx.espresso)
    androidTestImplementation(compose.junit)
    debugImplementation(compose.uitooling)
}
