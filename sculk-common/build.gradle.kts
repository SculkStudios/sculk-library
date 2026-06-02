plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — shared base: result/handle types, coroutines, scheduler, version, annotations"

// Allow framework internals to reference @SculkInternal APIs within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
}
