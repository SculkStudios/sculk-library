plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — particle builders, sound builders, animation timelines"

dependencies {
    api(project(":sculk-series"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
