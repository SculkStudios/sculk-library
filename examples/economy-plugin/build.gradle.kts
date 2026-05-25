plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio example - economy command, config, and profile workflow"

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-items"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-economy-${project.version}.jar"
}
