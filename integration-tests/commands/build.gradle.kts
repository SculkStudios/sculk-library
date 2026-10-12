import studio.sculk.gradle.ServerMatrix
import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-commands-paper"))
}

val runs = ServerMatrix.PAPER_AND_FOLIA.mapIndexed { index, server ->
    "${server.platform} ${server.minecraft} on Java ${server.java}" to
        registerServerRun("commands${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25621 + index)
}

registerResultsVerification(
    "verifyCommands",
    "Runs the command conformance suite on Paper and Folia and checks every result.",
    runs,
    resultsPath = "plugins/SculkCommands/results.properties",
    required = listOf("paper-and-interpreter-agree"),
)
