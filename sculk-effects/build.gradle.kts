plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — particle builders, sound builders, animation timelines"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-series"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testRuntimeOnly(libs.paper.api)
}
