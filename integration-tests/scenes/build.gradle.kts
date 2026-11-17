import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-scenes"))
    implementation(project(":integration-tests:bot-driver"))
}

// Two real clients watch display entities on every Paper anchor, Folia and Canvas: ADR-0032.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("scenes${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25741 + index, client = true)
}

registerResultsVerification(
    "verifyScenes",
    "Shows scenes to two real clients on every Paper anchor, Folia and Canvas and checks the display entities they see.",
    runs,
    resultsPath = "plugins/SculkScenes/results.properties",
    required = listOf(
        "a-scene-spawns-every-kind-of-display",
        "an-update-sends-only-what-changed",
        "only-chosen-players-see-it",
        "per-viewer-text-reaches-each-viewer-alone",
        "it-waits-for-its-chunk-to-load",
        "it-comes-back-after-its-chunk-unloads",
        "it-follows-across-a-far-teleport",
        "closing-the-scope-removes-everything",
    ),
)
