import studio.sculk.gradle.serverMatrix
import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":integration-tests:bot-driver"))
}

val examples = listOf("economy", "hud", "shop", "staff").map { name ->
    project(":examples:$name").tasks.named<Jar>("shadowJar").flatMap { it.archiveFile }
}

// The examples run together on every server the matrix pins, Canvas included, with a real client.
val runs = serverMatrix().all.mapIndexed { index, server ->
    server.label to registerServerRun(
        "examples${server.id}",
        server,
        tasks.shadowJar.flatMap { it.archiveFile },
        port = 25661 + index,
        otherPlugins = examples,
        client = true,
    )
}

registerResultsVerification(
    "verifyExamples",
    "Runs the example plugins on every server in the matrix and checks them the way an operator would.",
    runs,
    resultsPath = "plugins/SculkExampleCheck/results.properties",
    required = listOf(
        "economy-enabled",
        "reset-asks-for-confirmation",
        "hud-shows-on-join",
        "hud-turns-off-and-on",
        "shop-sells-for-emeralds",
        "trader-offers-its-trades",
        "staff-freezes-and-notes",
        "staff-removes-with-a-reason",
    ),
)
