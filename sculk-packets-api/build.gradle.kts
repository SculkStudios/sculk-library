plugins {
    id("sculk.module")
}

description = "Sculk Studio — backend-neutral packet contracts, client blocks and virtual entities"

dependencies {
    api(project(":sculk-common"))
    testImplementation(testFixtures(project(":sculk-common")))
    testImplementation(libs.mockito.kotlin)
}
