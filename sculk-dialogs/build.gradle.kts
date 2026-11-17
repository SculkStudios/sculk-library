plugins {
    id("sculk.paper-adapters")
    id("sculk.publish")
}

description = "Sculk Studio dialogs: forms with typed, validated answers on Paper 1.21.7 and later, with an explicit fallback elsewhere."

dependencies {
    api(project(":sculk-text"))
    api(project(":sculk-registry"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
    testImplementation(libs.adventure.plain)
}
