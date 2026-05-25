import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio - item API showcase"

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    implementation(project(":sculk-platform"))
    implementation(project(":sculk-items"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-items-${project.version}.jar"
}
