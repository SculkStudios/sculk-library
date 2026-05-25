plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio example - production-style server menu"

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-items"))
    testImplementation(libs.junit.jupiter)
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-server-menu-${project.version}.jar"
}
