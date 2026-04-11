plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — registry-based cross-version material, sound, particle mapping"

dependencies {
    api(project(":sculk-config"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
