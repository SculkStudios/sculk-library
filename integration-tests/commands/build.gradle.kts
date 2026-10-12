import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService
import java.io.InputStream
import java.time.Duration
import java.util.Properties

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-commands-paper"))
}

/** One real server the command conformance suite runs on. */
data class Target(val name: String, val platform: String, val minecraft: String, val java: Int, val port: Int, val build: Int? = null)

val targets = listOf(
    Target("Paper1214", "PAPER", "1.21.4", 21, 25621),
    Target("Paper263", "PAPER", "26.3", 25, 25622),
    Target("Folia2612", "FOLIA", "26.1.2", 25, 25623, build = 8),
)

val runTasks = targets.map { target ->
    val serverDir = layout.buildDirectory.dir("servers/${target.name}")
    tasks.register<RunServer>("commands${target.name}") {
        group = "sculk integration"
        description = "Runs the command conformance suite on ${target.platform} ${target.minecraft} with Java ${target.java}."
        minecraftVersion(target.minecraft)
        if (target.platform == "FOLIA") downloadsApiService = DownloadsAPIService.folia(project)
        target.build?.let { build = DownloadsAPIService.Build.Specific(it) }
        pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
        runDirectory = serverDir
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(target.java) }
        jvmArgs("-Xmx2g", "-Dcom.mojang.eula.agree=true", "-Dsculk.development=true")
        standardInput = InputStream.nullInputStream()
        timeout = Duration.ofMinutes(10)
        doFirst {
            val dir = serverDir.get().asFile
            dir.resolve("plugins/SculkCommands/results.properties").delete()
            dir.mkdirs()
            dir.resolve("eula.txt").writeText("eula=true\n")
            dir.resolve("server.properties").writeText("online-mode=false\nserver-port=${target.port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n")
        }
    }
}

tasks.register("verifyCommands") {
    group = "sculk integration"
    description = "Runs the command conformance suite on Paper 1.21.4, Paper 26.3 and Folia 26.1.2 and checks every result."
    dependsOn(runTasks)
    val results = targets.map { it to layout.buildDirectory.file("servers/${it.name}/plugins/SculkCommands/results.properties") }
    doLast {
        val report = StringBuilder()
        var failed = false
        results.forEach { (target, file) ->
            val path = file.get().asFile
            report.appendLine("${target.platform} ${target.minecraft} on Java ${target.java}:")
            if (!path.isFile) {
                failed = true
                report.appendLine("  FAIL: no results were written")
                return@forEach
            }
            val properties = Properties().apply { path.reader().use(::load) }
            properties.stringPropertyNames().sorted().forEach { key ->
                val value = properties.getProperty(key)
                if (!value.startsWith("PASS")) failed = true
                report.appendLine("  $key: $value")
            }
        }
        logger.lifecycle(report.toString())
        if (failed) throw GradleException("Command conformance failed; see the report above.")
    }
}
