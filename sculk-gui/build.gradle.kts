plugins {
    id("sculk.module")
}

description = "Sculk Studio — chest and container menus with animations, pagination and click routing"

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-text"))
    api(project(":sculk-items"))
    compileOnly(libs.adventure.api)
    // The scheduler fixture from sculk-common: this is what it is published for.
    testImplementation(testFixtures(project(":sculk-common")))
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.kotlin)
}
