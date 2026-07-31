plugins {
    id("sculk.example")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — basic-plugin example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-basic-plugin-${project.version}.jar"
}
