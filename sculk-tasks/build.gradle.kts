plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — coroutine scheduling: repeating tasks, cron, debounce/throttle"

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-core"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.coroutines.test)
}
