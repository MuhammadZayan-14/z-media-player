pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "Resonance"

include(":app")
include(":core:common")
include(":core:model")
include(":core:domain")
include(":core:database")
include(":core:datastore")
include(":core:data")
include(":core:media")
include(":core:permissions")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")
include(":feature:home")
include(":feature:music")
include(":feature:video")
include(":feature:player")
include(":feature:playlists")
include(":feature:search")
include(":feature:settings")
