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

// The 5.0 rebuild adds modules back one phase at a time. Deleting the Java-parity surface from
// sculk-common turns every downstream module red at once, so rather than carry compatibility
// shims until the end — which are exactly the thing being deleted — the build is narrowed to
// what has actually been rebuilt. `./gradlew build` then means something at every phase boundary.
//
// Restore order: common, text, config, series/items/integrations, commands, gui, data,
// packets-*, visual, hud, platform/bom, examples, benchmarks.
include(
    ":sculk-common",
    ":sculk-text",
    ":sculk-config",
    ":sculk-series",
    ":sculk-items",
    ":sculk-integrations",
    ":sculk-commands",
    ":sculk-gui",
    ":sculk-data",
    ":sculk-packets-api",
    ":sculk-packets-packetevents",
    ":sculk-packets-protocollib",
    ":sculk-visual",
    ":sculk-hud",
    ":sculk-platform",
    ":sculk-bom",
)
