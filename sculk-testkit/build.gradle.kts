plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio test kit: a deterministic executor, runtime and clock for testing plugin logic without a server."

dependencies {
    api(project(":sculk-platform-api"))
    api(libs.coroutines.test)
}
