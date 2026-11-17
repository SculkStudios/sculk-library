plugins {
    id("sculk.paper-adapters")
    id("sculk.publish")
}

description = "Sculk Studio items: item specs compiled to data components on every Paper version, typed persistent data and custom items."

dependencies {
    api(project(":sculk-text"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
    testImplementation(project(":sculk-testkit-paper"))
}
