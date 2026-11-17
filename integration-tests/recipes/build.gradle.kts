import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-recipes"))
}

// Trim recipes and brewing changed between versions, so every Paper anchor runs, with Folia and Canvas.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("recipes${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25681 + index)
}

registerResultsVerification(
    "verifyRecipes",
    "Registers every recipe type on every Paper anchor, Folia and Canvas, crafts with them and removes them.",
    runs,
    resultsPath = "plugins/SculkRecipes/results.properties",
    required = listOf("shaped-crafts", "every-type-registers", "removed-with-scope"),
)
