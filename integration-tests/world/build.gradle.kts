import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun
import studio.sculk.gradle.serverMatrix

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-world"))
}

// Game rules are named by registry key from 1.21.11 and world clocks arrive in 26.3, so every Paper
// anchor runs, with Folia and Canvas (ADR-0034).
val matrix = serverMatrix()
val runs = (matrix.paper + matrix.folia + matrix.canvas).mapIndexed { index, server ->
    server.label to registerServerRun("world${server.id}", server, tasks.shadowJar.flatMap { it.archiveFile }, port = 25771 + index)
}

registerResultsVerification(
    "verifyWorld",
    "Reads and sets game rules on every Paper anchor, Folia and Canvas, and world clocks where the server has them.",
    runs,
    resultsPath = "plugins/SculkWorld/results.properties",
    required = listOf(
        "vanilla-rules-read-on-every-version",
        "renamed-rules-reach-papers-own-constants",
        "inverted-rules-keep-their-meaning",
        "fire-radius-on-every-version",
        "defaults-are-vanillas",
        "out-of-range-values-are-refused",
        "set-hops-to-the-global-region",
        "set-now-refuses-other-threads",
        "changes-are-observed",
        "available-lists-the-servers-rules",
        "clocks-or-unsupported-capability",
        "clock-writes-hop-and-read-back",
        "timelines-follow-their-clock",
    ),
)
