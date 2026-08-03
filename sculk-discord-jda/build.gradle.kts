plugins {
    id("sculk.module")
}

description = "Sculk Studio — the JDA backend for sculk-discord"

dependencies {
    api(project(":sculk-discord"))

    // compileOnly, like every other backend adapter here. A consumer declares JDA itself: a Paper
    // plugin through `libraries:` in its paper-plugin.yml, a standalone bot as a normal runtime
    // dependency. Bundling it would drag two LGPL-2.1 transitives into whatever it is shipped in.
    compileOnly(libs.jda)

    testImplementation(libs.jda)
    testImplementation(testFixtures(project(":sculk-discord")))
    testImplementation(libs.coroutines.test)
}
