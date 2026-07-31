plugins {
    id("sculk.example")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — hud-showcase example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-hud-showcase-${project.version}.jar"
}
