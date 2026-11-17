plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio test kit for Paper: fakes for the Paper interfaces Sculk's Paper modules use, for testing without a server."

dependencies {
    api(project(":sculk-testkit"))
    // Tests that use these fakes already compile against Paper; the kit never brings its own copy.
    compileOnly(libs.paper.api.floor)
}
