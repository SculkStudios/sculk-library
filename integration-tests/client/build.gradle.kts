import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":integration-tests:bot-driver"))
}

// Proves the test client (ADR-0029) joins every server, ViaVersion included, and that its clicks
// reach the server as a player's would.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("client${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25691 + index, client = true)
}

registerResultsVerification(
    "verifyClient",
    "Joins every Paper anchor, Folia and Canvas with the test client and clicks an inventory.",
    runs,
    resultsPath = "plugins/SculkClient/results.properties",
    required = listOf("client-joins", "window-shows-the-item", "click-reaches-the-server"),
)
