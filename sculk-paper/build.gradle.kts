plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio runtime for Paper and Folia: execution ownership, lifecycle and capabilities."

dependencies {
    api(project(":sculk-platform-api"))
    api(project(":sculk-events"))
    api(project(":sculk-permissions"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
}
