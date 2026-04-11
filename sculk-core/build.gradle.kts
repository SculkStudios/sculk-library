plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — core framework: commands, GUI, Adventure wrapper, scheduler"

// Allow framework internals to reference @SculkInternal APIs within this module.
// External modules still receive the error-level opt-in requirement.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
