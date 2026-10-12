import studio.sculk.gradle.ServerMatrix
import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-core"))
}

val runs = ServerMatrix.PAPER.mapIndexed { index, server ->
    "Paper ${server.minecraft} on Java ${server.java}" to registerServerRun(
        "boot${server.id}",
        server,
        tasks.shadowJar.flatMap { it.archiveFile },
        port = 25601 + index,
        systemProperties = mapOf("sculk.itest.minecraft" to server.minecraft, "sculk.itest.java" to server.java.toString()),
    )
}

registerResultsVerification(
    "verifyBoot",
    "Boots the oldest and newest Paper anchors and checks that sculk-core ran on the expected Minecraft and Java versions.",
    runs,
    resultsPath = "plugins/SculkBoot/results.properties",
    required = listOf("minecraft-version", "parsed-version", "java-version"),
)
