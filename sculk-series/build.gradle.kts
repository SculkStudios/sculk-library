plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — registry-based cross-version material, sound, particle mapping"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-common"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
