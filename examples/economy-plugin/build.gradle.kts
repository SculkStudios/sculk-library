plugins {
    id("sculk.example")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlin.serialization)
}

description = "Sculk Studio — economy-plugin example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-economy-plugin-${project.version}.jar"
}
