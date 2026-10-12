import studio.sculk.gradle.ServerMatrix
import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
}

val economy = project(":examples:economy").tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }

// The example runs on every server the matrix pins, Canvas included.
val runs = ServerMatrix.ALL.mapIndexed { index, server ->
    "${server.platform} ${server.minecraft} on Java ${server.java}" to registerServerRun(
        "examples${server.id}",
        server,
        tasks.shadowJar.flatMap { it.archiveFile },
        port = 25661 + index,
        otherPlugins = listOf(economy),
    )
}

registerResultsVerification(
    "verifyExamples",
    "Runs the example plugins on every server in the matrix and checks them the way an operator would.",
    runs,
    resultsPath = "plugins/SculkExampleCheck/results.properties",
    required = listOf("economy-enabled", "reset-asks-for-confirmation"),
)
