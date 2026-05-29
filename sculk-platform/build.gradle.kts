plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — Paper platform integration: command registration, event DSL, GUI lifecycle"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-data"))
    api(project(":sculk-integrations"))
    api(project(":sculk-packets-api"))
    api(project(":sculk-text"))
    api(project(":sculk-tasks"))
    implementation(kotlin("reflect"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.mockito.kotlin)
}
