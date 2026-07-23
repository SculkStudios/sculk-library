plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio - item builders, persistent item data, skulls, and item descriptors"

dependencies {
    api(project(":sculk-common"))
    // Item names and lore are messages too, so they go through the same text
    // style layer as chat rather than parsing MiniMessage on their own.
    api(project(":sculk-adventure"))
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.mini)
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
}
