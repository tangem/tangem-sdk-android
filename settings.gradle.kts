pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// include(":tangem-sdk-jvm-demo")
// include(":tangem-sdk-jvm")
include(":tangem-sdk-android-demo")
include(":tangem-sdk-android")
include(":tangem-sdk-core")