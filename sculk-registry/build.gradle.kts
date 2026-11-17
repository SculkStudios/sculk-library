plugins {
    id("sculk.paper-adapters")
    id("sculk.publish")
}

description = "Sculk Studio registries: typed lookups, tags, entry sets from configuration, bootstrap registry changes and datapacks."

dependencies {
    api(project(":sculk-core"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
}
