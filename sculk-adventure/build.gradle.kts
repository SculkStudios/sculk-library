plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — Adventure/MiniMessage messaging helpers and templates"

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-common"))
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.mini)
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
}
