plugins {
    id("sculk.library")
    id("sculk.publish")
}

description =
    "Sculk Studio test kit: a deterministic executor, runtime, clock and command sender for testing plugin logic without a server."

dependencies {
    api(project(":sculk-platform-api"))
    api(project(":sculk-commands"))
    api(project(":sculk-permissions"))
    api(libs.coroutines.test)
    // Tests have no server to provide Adventure, so the test kit brings it.
    api(libs.adventure.api)
    api(libs.adventure.minimessage)
    api(libs.adventure.plain)
}
