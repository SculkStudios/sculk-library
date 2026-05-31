plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — Brigadier-native command DSL with typed arguments and cooldowns"

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-adventure"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
}
