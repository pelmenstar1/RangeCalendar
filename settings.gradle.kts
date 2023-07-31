import java.net.URI

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        maven { url = URI("https://jitpack.io") }
    }
}

rootProject.name = "Range Calendar"
include(":library", ":demo", ":decortest", "movebuttontest", ":selectionviewtest")