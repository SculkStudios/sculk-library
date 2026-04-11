plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — basic plugin example (vertical slice)"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-basic-${project.version}.jar"
}
