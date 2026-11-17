plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio recipes: every vanilla recipe type, brewing included, as specs registered for a scope."

dependencies {
    api(project(":sculk-items"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
}
