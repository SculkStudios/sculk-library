plugins {
    id("sculk.paper-adapters")
    id("sculk.publish")
}

description = "Sculk Studio worlds: typed game rules on every Paper version, world clocks and timelines where the server has them."

dependencies {
    api(project(":sculk-paper"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
}
