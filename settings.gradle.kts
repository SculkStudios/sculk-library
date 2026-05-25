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
    }
}

rootProject.name = "sculk-studio"

include(
    ":sculk-core",
    ":sculk-config",
    ":sculk-series",
    ":sculk-items",
    ":sculk-effects",
    ":sculk-data",
    ":sculk-platform",
    ":sculk-integrations",
)

// Examples (not published)
include(
    ":examples:basic-plugin",
    ":examples:commands-showcase",
    ":examples:gui-showcase",
    ":examples:config-showcase",
    ":examples:items-showcase",
)

// Benchmarks (not published)
include(":benchmarks")
