import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-items"))
}

// Item components changed shape at 1.21.5 and again since, so items run on every Paper anchor as
// well as Folia and Canvas.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("items${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25631 + index)
}

registerResultsVerification(
    "verifyItems",
    "Builds item specs on every Paper anchor, Folia and Canvas and checks the stacks through version-neutral API.",
    runs,
    resultsPath = "plugins/SculkItems/results.properties",
    required = listOf(
        "name-and-lore",
        "values-are-plain-text",
        "unbreakable",
        "hidden-parts",
        "persistent-data-survives-bytes",
        "custom-items",
    ),
)
