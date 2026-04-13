import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio — basic plugin example (vertical slice)"

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-basic-${project.version}.jar"
}
