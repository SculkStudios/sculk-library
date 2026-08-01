plugins {
    id("sculk.module")
}

description = "Sculk Studio — the plugin base class, service registry and bootstrap that wires everything together"

dependencies {
    // The one-line install: depending on sculk-platform gets the whole framework.
    api(project(":sculk-common"))
    api(project(":sculk-text"))
    api(project(":sculk-config"))
    api(project(":sculk-series"))
    api(project(":sculk-items"))
    api(project(":sculk-commands"))
    api(project(":sculk-gui"))
    api(project(":sculk-data"))
    api(project(":sculk-visual"))
    api(project(":sculk-hud"))
    api(project(":sculk-integrations"))
    api(project(":sculk-packets-api"))
    compileOnly(libs.adventure.api)

    testImplementation(testFixtures(project(":sculk-common")))
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.kotlin)
    // Stability markers are BINARY-retention, so they exist in the class file but not at runtime.
    // Reading them needs a class-file reader; StabilityMarkerTest is the only thing that uses this.
    testImplementation(libs.asm)
}
