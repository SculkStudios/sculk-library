import studio.sculk.gradle.CompatibilityScan

plugins {
    base
}

// Pinned builds from COMPATIBILITY.md. 1.21.x anchors are Maven snapshots, so they are pinned to a
// timestamped build rather than the moving -SNAPSHOT alias.
val anchors = linkedMapOf(
    "1.21.4" to "1.21.4-R0.1-20250925.065901-231",
    "1.21.11" to "1.21.11-R0.1-20260511.115010-91",
    "26.1.2" to "26.1.2.build.74-stable",
    "26.2" to "26.2.build.133-stable",
    "26.3" to "26.3.build.175-stable",
)

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
    output = layout.buildDirectory.file("compatibility/paper-api.json")
}
