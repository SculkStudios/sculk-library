plugins {
    id("sculk.module")
}

description = "Sculk Studio — command specs as data, a Brigadier adapter, and generated help"

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-text"))
    compileOnly(libs.adventure.api)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.mini)
    testImplementation(libs.coroutines.test)
}
