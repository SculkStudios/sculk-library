plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio - item builders, persistent item data, skulls, and item descriptors"

dependencies {
    api(project(":sculk-common"))
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.mini)
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
}
