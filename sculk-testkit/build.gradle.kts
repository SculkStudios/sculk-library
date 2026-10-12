plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio test kit: a deterministic runtime for testing plugin logic without a server."

dependencies {
    api(project(":sculk-platform-api"))
    api(testFixtures(project(":sculk-coroutines")))
    api(libs.coroutines.test)
}
