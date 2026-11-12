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
    }
}

rootProject.name = "x-by-faz"

include(
    ":app",
    ":core:model",
    ":core:navigation",
    ":core:database",
    ":core:network",
    ":core:auth",
    ":core:web",
    ":core:player",
    ":core:ui",
    ":feature:home",
    ":feature:videos",
    ":feature:search",
    ":feature:profile",
    ":feature:history",
    ":feature:settings",
)
