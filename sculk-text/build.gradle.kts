plugins {
    id("sculk.module")
}

description = "Sculk Studio — theme, message rendering, text measurement and per-player localisation"

dependencies {
    api(project(":sculk-common"))

    // Adventure ships inside Paper; declared so the module still compiles standalone.
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.mini)

    // The bundle loader parses YAML directly rather than going through sculk-config: a dependency
    // that way round would put the whole config system on the compile classpath of gui, items and
    // commands, none of which want it.
    implementation(libs.kaml)

    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.mini)
    testImplementation(libs.mockito.kotlin)
}
