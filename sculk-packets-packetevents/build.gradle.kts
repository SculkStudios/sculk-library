plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio - PacketEvents packet backend adapter"

dependencies {
    api(project(":sculk-packets-api"))
    compileOnly(libs.packetevents.spigot)
    testImplementation(libs.junit.jupiter)
}
