plugins {
    id("sculk.example")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — visual-showcase example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-visual-showcase-${project.version}.jar"
}
