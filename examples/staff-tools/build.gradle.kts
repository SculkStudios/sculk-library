plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio example - staff tools workflow"

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-effects"))
    implementation(project(":sculk-items"))
    testImplementation(libs.junit.jupiter)
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-staff-tools-${project.version}.jar"
}
