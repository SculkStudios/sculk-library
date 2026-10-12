plugins {
    id("sculk.library")
    id("sculk.publish")
}

description =
    "Sculk Studio commands on Paper, Folia and Canvas: compiles command specs into Paper's Brigadier tree with native argument types."

dependencies {
    api(project(":sculk-commands"))
    api(project(":sculk-paper"))
    // Paper brings Adventure and its own Brigadier; Sculk never declares either.
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
}
