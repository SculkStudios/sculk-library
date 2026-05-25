plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio example - packet backend selection, debug listeners, and client block previews"

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-packets-api"))
    implementation(project(":sculk-packets-packetevents"))
    implementation(project(":sculk-packets-protocollib"))
}
