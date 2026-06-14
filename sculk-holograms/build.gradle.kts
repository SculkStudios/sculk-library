plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — packet-based virtual holograms (PacketEvents backend)"

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-adventure"))
    api(project(":sculk-packets-api"))
    api(project(":sculk-packets-packetevents"))
    compileOnly(libs.packetevents.spigot)
    testImplementation(libs.junit.jupiter)
}
