plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — Paper platform integration: command registration, event DSL, GUI lifecycle"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-adventure"))
    api(project(":sculk-commands"))
    api(project(":sculk-gui"))
    api(project(":sculk-events"))
    api(project(":sculk-config"))
    api(project(":sculk-series"))
    api(project(":sculk-items"))
    api(project(":sculk-effects"))
    api(project(":sculk-content"))
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
