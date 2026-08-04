plugins {
    id("sculk.module")
}

description = "Sculk Studio — the Ktor backend for sculk-web"

dependencies {
    api(project(":sculk-web"))

    // compileOnly, like every other backend adapter here. A consumer declares Ktor itself: a Paper
    // plugin through `libraries:` in its plugin.yml, a standalone tool as a normal runtime
    // dependency. Bundling it would put a full HTTP server inside whatever this is shipped in,
    // whether or not that consumer serves anything.
    compileOnly(libs.ktor.server.core)
    compileOnly(libs.ktor.server.cio)

    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cio)
    testImplementation(testFixtures(project(":sculk-web")))
    testImplementation(libs.coroutines.test)
}
