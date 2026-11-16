import studio.sculk.gradle.serverMatrix
import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-core"))
}

// Every Paper anchor boots; the floor and current anchors also run every other suite.
val runs = serverMatrix().paper.mapIndexed { index, server ->
    "Paper ${server.version} on Java ${server.java}" to registerServerRun(
        "boot${server.id}",
        server,
        tasks.shadowJar.flatMap { it.archiveFile },
        port = 25601 + index,
        systemProperties = mapOf("sculk.itest.minecraft" to server.version, "sculk.itest.java" to server.java.toString()),
    )
}

registerResultsVerification(
    "verifyBoot",
    "Boots every Paper anchor and checks that sculk-core ran on the expected Minecraft and Java versions.",
    runs,
    resultsPath = "plugins/SculkBoot/results.properties",
    required = listOf("minecraft-version", "parsed-version", "java-version"),
)
