plugins {
    `kotlin-dsl`
}

// Hardcoded here intentionally — build-logic is bootstrap code that runs
// before the version catalog is fully available. Keep in sync with libs.versions.toml.
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.3.0")
}
