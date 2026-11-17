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
        // MCProtocolLib and its libraries, for the test client only (ADR-0029); never a published dependency.
        maven("https://repo.opencollab.dev/main/") {
            content {
                includeGroupAndSubgroups("org.geysermc")
                includeGroupAndSubgroups("org.cloudburstmc")
                includeGroup("com.nukkitx.fastutil")
            }
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
    ":sculk-testkit-paper",
    ":sculk-paper",
    ":sculk-canvas",
    ":sculk-velocity",
    ":sculk-items",
    ":sculk-registry",
    ":sculk-recipes",
    ":sculk-inventory",
    ":sculk-dialogs",
    ":sculk-presentation",
    ":sculk-config-admin",
)

// Never published. Each proves something about the published modules on a real server.
include(
    ":benchmarks",
    ":examples:economy",
    ":examples:network",
    ":integration-tests:bot",
    ":integration-tests:bot-driver",
    ":integration-tests:boot",
    ":integration-tests:client",
    ":integration-tests:commands",
    ":integration-tests:config-admin",
    ":integration-tests:dialogs",
    ":integration-tests:hud",
    ":integration-tests:examples",
    ":integration-tests:items",
    ":integration-tests:menus",
    ":integration-tests:recipes",
    ":integration-tests:registry",
    ":integration-tests:threading",
    ":integration-tests:velocity",
)

// Build tooling that is not itself a Gradle plugin.
include(":tooling:compatibility")
