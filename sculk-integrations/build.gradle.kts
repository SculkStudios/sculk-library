plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio - optional adapters for common Minecraft plugin integrations"

dependencies {
    api(project(":sculk-core"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
