import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.shadow)
}

description = "Sculk Studio - item API showcase"

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    // sculk-platform transitively re-exports the entire DSL (commands, GUI, items, config, data, …).
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-items-${project.version}.jar"
}
