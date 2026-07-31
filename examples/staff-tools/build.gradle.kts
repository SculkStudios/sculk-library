plugins {
    id("sculk.example")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — staff-tools example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-staff-tools-${project.version}.jar"
}
