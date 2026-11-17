import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-npcs"))
    implementation(project(":integration-tests:bot-driver"))
}

// Mannequins exist from 1.21.9: the newer anchors, Folia and Canvas show NPCs to a real client and
// take its clicks, 1.21.4 proves the UnsupportedCapability failure (ADR-0033).
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("npcs${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25751 + index, client = true)
}

registerResultsVerification(
    "verifyNpcs",
    "Shows NPCs to a real client on every Paper anchor, Folia and Canvas, or proves the failure where mannequins do not exist.",
    runs,
    resultsPath = "plugins/SculkNpcs/results.properties",
    required = listOf(
        "capability-matches-the-server",
        "spawns-with-skin-and-name",
        "clicks-run-on-the-players-thread",
        "looks-at-the-nearest-player",
        "despawns-when-its-scope-closes",
    ),
)
