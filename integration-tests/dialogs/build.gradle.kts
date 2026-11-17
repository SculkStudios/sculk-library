import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-dialogs"))
    implementation(project(":integration-tests:bot-driver"))
}

// Dialogs exist from 1.21.7: the newer anchors show them to a real client, 1.21.4 proves the fallback.
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("dialogs${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25711 + index, client = true)
}

registerResultsVerification(
    "verifyDialogs",
    "Shows dialogs to a real client on every Paper anchor, Folia and Canvas, or their fallback where dialogs do not exist.",
    runs,
    resultsPath = "plugins/SculkDialogs/results.properties",
    required = listOf(
        "dialog-or-fallback-shows",
        "typed-answers-on-the-players-thread",
        "invalid-answers-read-as-null",
        "registered-dialogs-show-and-answer",
    ),
)
