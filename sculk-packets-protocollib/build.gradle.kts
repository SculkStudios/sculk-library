plugins {
    id("sculk.module")
}

description = "Sculk Studio — ProtocolLib backend for the packet API"

dependencies {
    api(project(":sculk-packets-api"))
    compileOnly(libs.protocollib)

    testImplementation(testFixtures(project(":sculk-common")))
    testImplementation(libs.protocollib)
    testImplementation(libs.mockito.kotlin)
}
