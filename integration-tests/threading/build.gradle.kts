import studio.sculk.gradle.ServerMatrix
import studio.sculk.gradle.registerResultsVerification
import studio.sculk.gradle.registerServerRun

plugins {
    id("sculk.test-plugin")
}

// canvas-api contains the whole Paper API and declares the same capability, so it replaces paper-api here.
dependencies {
    compileOnly(libs.canvas.api)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-canvas"))
}

configurations.compileClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}

val plugin = tasks.shadowJar.flatMap { it.archiveFile }

fun label(server: ServerMatrix.Server, grid: Int?) =
    "${server.platform} ${server.minecraft} on Java ${server.java}" + grid?.let { ", grid-exponent $it" }.orEmpty()

// Regionized servers run at Folia's default region size, and Folia once more at 5 so an assumed size cannot hide.
val runs = ServerMatrix.ALL.mapIndexed { index, server ->
    val grid = if (server.isRegionized) 4 else null
    label(server, grid) to registerServerRun("threading${server.id}", server, plugin, port = 25611 + index, gridExponent = grid)
} + (label(ServerMatrix.FOLIA, 5) to registerServerRun("threading${ServerMatrix.FOLIA.id}Grid5", ServerMatrix.FOLIA, plugin, 25615, 5))

registerResultsVerification(
    "verifyThreading",
    "Runs the threading scenarios on every server in the matrix and checks every result.",
    runs,
    resultsPath = "plugins/SculkThreading/results.properties",
    required = listOf("shutdown-cancels-pending-work"),
)
