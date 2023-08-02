// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false

    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.android) apply false
}

subprojects {
    tasks.withType<Javadoc>().configureEach { enabled = false }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}