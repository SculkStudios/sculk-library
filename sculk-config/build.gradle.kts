plugins {
    id("sculk.module")
    alias(libs.plugins.kotlin.serialization)
}

description = "Sculk Studio — typed YAML config: generated defaults, merge-on-load, validation, migrations"

kotlin {
    compilerOptions {
        // The descriptor annotations and element metadata this module is built on are still
        // marked experimental upstream, and every file here touches them.
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    api(project(":sculk-common"))
    api(libs.serialization.core)
    implementation(libs.kaml)
}
