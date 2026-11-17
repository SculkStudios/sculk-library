import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
    kotlin("plugin.serialization")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-config-admin"))
    implementation(project(":integration-tests:bot-driver"))
}

// The editor is a dialog from 1.21.7 and a menu before; a real client uses both.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("configAdmin${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25731 + index, client = true)
}

registerResultsVerification(
    "verifyConfigAdmin",
    "Edits a configuration in game with a real client on every Paper anchor, Folia and Canvas.",
    runs,
    resultsPath = "plugins/SculkConfigAdmin/results.properties",
    required = listOf("editor-saves-changes", "invalid-changes-are-refused"),
)
