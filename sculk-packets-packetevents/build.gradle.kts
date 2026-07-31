plugins {
    id("sculk.module")
}

description = "Sculk Studio — PacketEvents backend for the packet API"

dependencies {
    api(project(":sculk-packets-api"))
    // PacketEvents is GPL-3.0. It stays compileOnly and confined to this module: if Sculk is ever
    // published, only this artifact would be affected by that licence, and a consumer that does not
    // use packets never pulls it in.
    compileOnly(libs.packetevents.spigot)

    testImplementation(testFixtures(project(":sculk-common")))
    testImplementation(libs.packetevents.spigot)
    testImplementation(libs.mockito.kotlin)
}
