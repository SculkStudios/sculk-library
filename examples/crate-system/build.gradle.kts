plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio example - crate preview with item descriptors"

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-items"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-crate-system-${project.version}.jar"
}
