plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — typed config, hot reload, message system"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-common"))
    implementation(libs.snakeyaml)
    implementation(kotlin("reflect"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
