import studio.sculk.gradle.RegistryAliasCheck
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
val paperAnchors = serverMatrix().paper
val runs = paperAnchors.mapIndexed { index, server ->
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

// Each boot writes its registry keys; a key gone between anchors needs an alias line (sculk-registry).
val checkRegistryAliases = tasks.register<RegistryAliasCheck>("checkRegistryAliases") {
    group = "verification"
    description = "Fails when a registry key disappears between supported Paper versions without an alias."
    mustRunAfter(runs.map { it.second })
    anchors = paperAnchors.map { server ->
        "${server.version}=" + layout.buildDirectory.file("servers/boot${server.id}/plugins/SculkBoot/registry-keys.txt").get().asFile.path
    }
    aliases = rootProject.layout.projectDirectory.file("sculk-registry/src/main/resources/studio/sculk/registry/aliases.txt")
    report = layout.buildDirectory.file("registry-changes.txt")
}

tasks.named("verifyBoot") { finalizedBy(checkRegistryAliases) }
