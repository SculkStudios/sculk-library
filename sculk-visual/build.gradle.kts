plugins {
    id("sculk.module")
}

description = "Sculk Studio — particles, sounds, timelines, packet-only holograms and nametags"

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-series"))
    api(project(":sculk-text"))
    // Holograms and nametags are packets, not entities — but only through the neutral contract.
    // Importing a backend here is what made the previous hologram service PacketEvents-only.
    api(project(":sculk-packets-api"))
    compileOnly(libs.adventure.api)

    testImplementation(testFixtures(project(":sculk-common")))
    testImplementation(testFixtures(project(":sculk-packets-api")))
    testImplementation(libs.mockito.kotlin)
}
