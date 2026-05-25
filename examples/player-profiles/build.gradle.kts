plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio example - player profile lifecycle and async handoff"

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-items"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-player-profiles-${project.version}.jar"
}
