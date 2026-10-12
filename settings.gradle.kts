pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
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
                includeGroup("com.velocitypowered")
            }
        }
        maven("https://maven.canvasmc.io/releases") {
            content { includeGroupAndSubgroups("io.canvasmc") }
        }
    }
}

rootProject.name = "sculk-studio"

include(
    ":sculk-core",
    ":sculk-coroutines",
    ":sculk-platform-api",
    ":sculk-text",
    ":sculk-placeholders",
    ":sculk-config",
    ":sculk-events",
    ":sculk-permissions",
    ":sculk-commands",
    ":sculk-commands-paper",
    ":sculk-commands-velocity",
    ":sculk-testkit",
    ":sculk-paper",
    ":sculk-canvas",
    ":sculk-velocity",
)

// Never published. Each proves something about the published modules on a real server.
include(
    ":benchmarks",
    ":examples:economy",
    ":integration-tests:boot",
    ":integration-tests:commands",
    ":integration-tests:examples",
    ":integration-tests:threading",
    ":integration-tests:velocity",
)

// Build tooling that is not itself a Gradle plugin.
include(":tooling:compatibility")
