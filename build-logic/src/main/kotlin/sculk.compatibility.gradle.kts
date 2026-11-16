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

tasks.register<CompatibilityScan>("scanPaperApi") {
    group = "sculk compatibility"
    description = "Writes the classes whose presence, kind or experimental status differs across the Paper anchors."
    anchorNames = anchors.keys.toList()
    anchorConfigurations.forEach { anchorJars.from(it) }
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
