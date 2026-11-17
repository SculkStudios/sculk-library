import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-presentation"))
    implementation(project(":integration-tests:bot-driver"))
}

// A real client watches every surface on every Paper anchor, Folia (no sidebar) and Canvas
// (scoreboard writes on the global tick): ADR-0030.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("hud${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25721 + index, client = true)
}

registerResultsVerification(
    "verifyHud",
    "Shows a HUD to a real client on every Paper anchor, Folia and Canvas and checks what it sees as values change.",
    runs,
    resultsPath = "plugins/SculkHud/results.properties",
    required = listOf("surfaces-show", "a-change-redraws-what-used-it", "sidebar-yields-to-another-board", "closing-removes-everything"),
)
