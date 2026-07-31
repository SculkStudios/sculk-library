plugins {
    id("sculk.module")
}

description = "Sculk Studio — registry lookups for materials, sounds, particles and the rest, with aliases"

dependencies {
    api(project(":sculk-common"))
    testImplementation(libs.mockito.kotlin)
}
