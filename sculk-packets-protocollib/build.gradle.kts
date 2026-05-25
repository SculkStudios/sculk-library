plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio - ProtocolLib packet backend adapter"

dependencies {
    api(project(":sculk-packets-api"))
    compileOnly(libs.protocollib)
    testImplementation(libs.junit.jupiter)
}
