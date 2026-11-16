// An example Velocity plugin: built like a proxy-side test plugin, with kotlinx.serialization for
// its config. Examples are compile gates and documentation, never published.

plugins {
    id("sculk.test-proxy-plugin")
    kotlin("plugin.serialization")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = versionCatalogs.named("libs")

ktlint {
    version = libs.findVersion("ktlint").get().requiredVersion
}

dependencies {
    "implementation"(libs.findLibrary("serialization-core").get())
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("velocity-plugin.json") { expand("version" to pluginVersion) }
}
