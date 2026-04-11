plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — config showcase example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-config-${project.version}.jar"
}
