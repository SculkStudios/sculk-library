plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — GUI showcase example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-gui-${project.version}.jar"
}
