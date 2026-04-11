plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — Paper platform integration: command registration, event DSL, GUI lifecycle"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-data"))
    implementation(kotlin("reflect"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
