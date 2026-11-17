plugins {
    id("sculk.library")
    id("sculk.publish")
}

description =
    "Sculk Studio presentation: per-player HUDs (action bar, boss bars, tab list, sidebar) that redraw only what changed (ADR-0030)."

dependencies {
    api(project(":sculk-placeholders"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
    testImplementation(project(":sculk-testkit"))
}
