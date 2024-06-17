plugins {
    id("com.android.library")
    id("maven-publish")
    id("kotlin-android")
}

android {
    namespace = "com.github.pelmenstar1.rangecalendar"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        // Tests don't run when targetSdk is not set.
        @Suppress("DEPRECATION")
        targetSdk = 34

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles (getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.appcompat)

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlin.stdlib)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlin.test.junit)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                pom {
                    name.set("Range Calendar")
                    description.set("Calendar for Android with support of range selection")
                    url.set("https://github.com/pelmenstar1/rangeCalendar/")
                    inceptionYear.set("2022")

                    licenses {
                        license {
                            name.set("The MIT License")
                            url.set("https://github.com/pelmenstar1/rangeCalendar/blob/master/LICENSE")
                        }
                    }

                    scm {
                        url.set("https://github.com/pelmenstar1/rangeCalendar/")
                        connection.set("scm:git:git://github.com/pelmenstar1/rangeCalendar.git")
                        developerConnection.set("scm:git:ssh://git@github.com/pelmenstar1/rangeCalenadar.git")
                    }

                    developers {
                        developer {
                            id.set("pelmenstar1")
                            name.set("Oleg Khmaruk")
                            url.set("https://github.com/pelmenstar1/")
                        }
                    }
                }

                groupId = "com.github.pelmenstar1"
                artifactId = "rangeCalendar"
                version = "0.9.3"

                from(components.getByName("release"))
            }
        }
    }
}