import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-registry"))
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("paper-plugin.yml") { expand("version" to pluginVersion) }
}

// The registry event changed from freeze to compose at 1.21.11, so every Paper anchor runs, with
// Folia and Canvas.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("registry${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25671 + index)
}

registerResultsVerification(
    "verifyRegistry",
    "Changes registries during bootstrap on every Paper anchor, Folia and Canvas and checks them once the server runs.",
    runs,
    resultsPath = "plugins/SculkRegistry/results.properties",
    required = listOf("composed-enchantment", "modified-entry", "datapack-tag", "entry-set"),
)
