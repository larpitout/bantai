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
        // Para sa BlurView (frosted glass navbar) — wala sa Maven Central.
        maven("https://jitpack.io")
    }
}

rootProject.name = "Bantai"
include(":app")
