plugins {
    kotlin("jvm")
    id("com.vanniktech.maven.publish")
}

dependencies {
    constraints {
        api(project(":permissions"))
        api(project(":storage"))
        api(libs.okio)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
