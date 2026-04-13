plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — commands showcase example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-commands-${project.version}.jar"
}
