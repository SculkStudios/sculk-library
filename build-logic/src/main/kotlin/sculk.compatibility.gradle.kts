import studio.sculk.gradle.CompatibilityScan
import studio.sculk.gradle.serverMatrix

plugins {
    base
}

// The Paper anchors pinned in gradle/servers.json, each with the API built from the same commit.
val anchors = serverMatrix().paper.associate { it.version to it.apiVersion }

val anchorConfigurations = anchors.map { (minecraft, artifactVersion) ->
    configurations.create("paperApi${minecraft.replace(".", "_")}") {
        isCanBeConsumed = false
        isTransitive = false
        dependencies.add(project.dependencies.create("io.papermc.paper:paper-api:$artifactVersion"))
    }
}

// The same anchors resolved with their dependencies, read for metadata only: which Adventure,
// Brigadier and JSpecify each Paper line ships.
val anchorLibraries = anchors.mapValues { (minecraft, artifactVersion) ->
    configurations.create("paperApiLibraries${minecraft.replace(".", "_")}") {
        isCanBeConsumed = false
        dependencies.add(project.dependencies.create("io.papermc.paper:paper-api:$artifactVersion"))
    }
}

tasks.register<CompatibilityScan>("scanPaperApi") {
    group = "sculk compatibility"
    description = "Writes the classes whose presence, kind or experimental status differs across the Paper anchors, and the libraries each ships."
    anchorNames = anchors.keys.toList()
    anchorConfigurations.forEach { anchorJars.from(it) }
    libraries = provider {
        anchorLibraries.mapValues { (_, configuration) ->
            configuration.incoming.resolutionResult.allComponents
                .mapNotNull { it.moduleVersion }
                .filter { "${it.group}:${it.name}" in CompatibilityScan.LIBRARIES }
                .associate { "${it.group}:${it.name}" to it.version }
        }
    }
    // The floor's Adventure is the oldest any supported server ships, so shared modules compile against it.
    expectedFloorAdventure = versionCatalogs.named("libs").findVersion("adventure-floor").get().requiredVersion
    // Classes whose members appear, disappear or change status within a supported range; each feeds a
    // capability or a per-member opt-in (COMPATIBILITY.md).
    memberProbes.addAll(
        "io.papermc.paper.command.brigadier.argument.ArgumentTypes",
        "io.papermc.paper.registry.RegistryKey",
        "io.papermc.paper.registry.event.RegistryEvents",
        "io.papermc.paper.datacomponent.DataComponentTypes",
        "org.bukkit.inventory.MenuType",
        "io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents",
    )
    output = layout.buildDirectory.file("compatibility/paper-api.json")
}
