plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — localization: per-player locale, message bundles, MiniMessage templates"

// Allow framework internals (YamlMapper) within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-adventure"))
    implementation(project(":sculk-config"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
