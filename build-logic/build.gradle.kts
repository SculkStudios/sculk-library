plugins {
    `kotlin-dsl`
}

// build-logic/settings.gradle.kts imports the root catalog, so these can reference it rather
// than being hardcoded and drifting — which is what happened to the Kotlin version previously.
dependencies {
    implementation(libs.build.kotlin.plugin)
    implementation(libs.build.ktlint.plugin)
}
