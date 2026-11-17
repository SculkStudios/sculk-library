plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio menus: state-driven inventory menus with diff rendering, a locked interaction policy and player-owned sessions."

dependencies {
    api(project(":sculk-items"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
}
