plugins {
    id("sculk.module")
}

description = "Sculk Studio — sidebar, action bar, tab list and boss bars driven by one task"

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-text"))
    compileOnly(libs.adventure.api)

    testImplementation(testFixtures(project(":sculk-common")))
    testImplementation(libs.mockito.kotlin)
}
