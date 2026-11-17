import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-inventory"))
    implementation(project(":integration-tests:bot-driver"))
}

// A real client clicks menus on every Paper anchor, Folia and Canvas: the M4 gate's inventory
// ownership tests (ADR-0029).
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("menus${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25701 + index, client = true)
}

registerResultsVerification(
    "verifyMenus",
    "Opens menus for a real client on every Paper anchor, Folia and Canvas and checks clicks, the lock, redraws and ownership.",
    runs,
    resultsPath = "plugins/SculkMenus/results.properties",
    required = listOf("menu-shows", "button-runs-on-the-players-thread", "player-items-never-enter", "title-change-reopens", "closing-runs-on-close"),
)
