plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio example - config-backed kits, cooldowns, item descriptors, and previews"

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-items"))
    testImplementation(libs.junit.jupiter)
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-kits-${project.version}.jar"
}
