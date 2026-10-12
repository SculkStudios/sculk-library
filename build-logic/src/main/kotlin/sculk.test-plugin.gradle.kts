import studio.sculk.gradle.VerifyBytecodeLevel

// A Paper plugin that exists only to run Sculk on a real server in CI. Never published.

plugins {
    id("sculk.jvm")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-paper")
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") { expand("version" to pluginVersion) }
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    val prefix = "studio.sculk.itest.${project.name.replace('-', '_')}.libs"
    relocate("kotlinx", "$prefix.kotlinx")
    relocate("kotlin", "$prefix.kotlin")
}

tasks.named<VerifyBytecodeLevel>("verifyBytecodeLevel") {
    archives.setFrom(tasks.shadowJar)
}

runPaper.disablePluginJarDetection()
