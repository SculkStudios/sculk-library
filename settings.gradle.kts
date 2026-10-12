pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            content {
                includeGroupAndSubgroups("io.papermc")
                includeGroup("net.md-5")
                includeGroup("com.mojang")
            }
        }
    }
}

rootProject.name = "sculk-studio"

include(":sculk-core")

// Never published. Each proves something about the published modules on a real server.
include(":integration-tests:boot")

// Build tooling that is not itself a Gradle plugin.
include(":tooling:compatibility")
