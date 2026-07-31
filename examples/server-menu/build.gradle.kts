plugins {
    id("sculk.example")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — server-menu example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-server-menu-${project.version}.jar"
}
