pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://repo.dmulloy2.net/repository/public/")
    }
}

rootProject.name = "sculk-studio"

include(
    ":sculk-common",
    ":sculk-adventure",
    ":sculk-commands",
    ":sculk-gui",
    ":sculk-events",
    ":sculk-config",
    ":sculk-series",
    ":sculk-items",
    ":sculk-effects",
    ":sculk-data",
    ":sculk-platform",
    ":sculk-integrations",
    ":sculk-packets-api",
    ":sculk-packets-packetevents",
    ":sculk-packets-protocollib",
    ":sculk-content",
    ":sculk-text",
    ":sculk-tasks",
)

// Examples (not published)
include(
    ":examples:basic-plugin",
    ":examples:commands-showcase",
    ":examples:gui-showcase",
    ":examples:config-showcase",
    ":examples:items-showcase",
    ":examples:packets-showcase",
    ":examples:economy-plugin",
    ":examples:player-profiles",
    ":examples:server-menu",
    ":examples:staff-tools",
    ":examples:crate-system",
    ":examples:kits-plugin",
)

// Benchmarks (not published)
include(":benchmarks")
