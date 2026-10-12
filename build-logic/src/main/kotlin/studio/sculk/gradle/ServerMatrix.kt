package studio.sculk.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.Properties
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.time.Duration

/**
 * Every real server Sculk's integration suites run on, pinned to exact builds. This is the one
 * place a server version or build changes; COMPATIBILITY.md lists the same anchors, and the canary
 * workflow reports when a newer build is available.
 */
object ServerMatrix {
    enum class Platform { PAPER, FOLIA, CANVAS }

    class Server(
        val id: String,
        val platform: Platform,
        val minecraft: String,
        val java: Int,
        /** A Fill build for Folia; Paper runs its newest build of [minecraft]. */
        val build: Int? = null,
        /** A server jar from outside Fill, with its SHA-256: a CI server jar is code that runs. */
        val jar: Pair<String, String>? = null,
    ) {
        val isRegionized: Boolean get() = platform != Platform.PAPER
    }

    val PAPER_FLOOR = Server("Paper1214", Platform.PAPER, "1.21.4", java = 21)
    val PAPER_CURRENT = Server("Paper263", Platform.PAPER, "26.3", java = 25)
    val FOLIA = Server("Folia2612", Platform.FOLIA, "26.1.2", java = 25, build = 8)
    val CANVAS = Server(
        "Canvas263",
        Platform.CANVAS,
        "26.3",
        java = 25,
        jar = "https://jenkins.canvasmc.io/job/Canvas/978/artifact/canvas-server/build/libs/canvas-build.978.jar" to
            "011d9096792b218456cbca2b962883f852da9ff8b9674d884ead9b9671801356",
    )

    val ALL: List<Server> = listOf(PAPER_FLOOR, PAPER_CURRENT, FOLIA, CANVAS)
    val PAPER_AND_FOLIA: List<Server> = listOf(PAPER_FLOOR, PAPER_CURRENT, FOLIA)
    val PAPER: List<Server> = listOf(PAPER_FLOOR, PAPER_CURRENT)
}

/**
 * Registers a task that runs [server] with [pluginJar] in [runDirectory] and stops when the plugin
 * shuts the server down. [gridExponent] configures Folia's and Canvas's region size;
 * [systemProperties] reach the plugin through `System.getProperty`.
 */
fun Project.registerServerRun(
    taskName: String,
    server: ServerMatrix.Server,
    pluginJar: Provider<RegularFile>,
    port: Int,
    gridExponent: Int? = null,
    systemProperties: Map<String, String> = emptyMap(),
): TaskProvider<RunServer> {
    val runDirectory = layout.buildDirectory.dir("servers/${taskName.removePrefix("run")}")
    val download = server.jar?.let { (url, sha256) -> registerVerifiedDownload(server.id, url, sha256) }
    val toolchains = extensions.getByType<JavaToolchainService>()
    return tasks.register<RunServer>(taskName) {
        group = "sculk integration"
        description = "Runs ${server.platform} ${server.minecraft} on Java ${server.java}${gridExponent?.let { " at grid-exponent $it" }.orEmpty()}."
        minecraftVersion(server.minecraft)
        if (download != null) {
            dependsOn(download)
            serverJar(layout.buildDirectory.file("server-jars/${server.id}.jar"))
        } else {
            if (server.platform == ServerMatrix.Platform.FOLIA) downloadsApiService.set(DownloadsAPIService.folia(project))
            server.build?.let { build.set(DownloadsAPIService.Build.Specific(it)) }
        }
        pluginJars(pluginJar)
        this.runDirectory.set(runDirectory)
        javaLauncher.set(toolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(server.java)) })
        jvmArgs("-Xmx2g", "-Dcom.mojang.eula.agree=true", "-Dsculk.development=true", "-Dsculk.itest.platform=${server.platform}")
        systemProperties.forEach { (key, value) -> jvmArgs("-D$key=$value") }
        gridExponent?.let { jvmArgs("-Dsculk.itest.gridExponent=$it") }
        standardInput = InputStream.nullInputStream()
        timeout.set(Duration.ofMinutes(10))
        doFirst {
            val dir = runDirectory.get().asFile.apply { mkdirs() }
            dir.resolve("eula.txt").writeText("eula=true\n")
            dir.resolve("server.properties").writeText(
                "online-mode=false\nserver-port=$port\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-protection=0\n",
            )
            // A minimal file: the server fills in every other key and keeps this one.
            gridExponent?.let { exponent ->
                dir.resolve("config").mkdirs()
                dir.resolve("config/paper-global.yml").writeText("threaded-regions:\n  grid-exponent: $exponent\n")
            }
        }
    }
}

private fun Project.registerVerifiedDownload(id: String, url: String, sha256: String) =
    tasks.findByName("download$id")?.let { tasks.named("download$id") } ?: tasks.register("download$id") {
        val jar = layout.buildDirectory.file("server-jars/$id.jar")
        inputs.property("url", url)
        inputs.property("sha256", sha256)
        outputs.file(jar)
        doLast {
            val file = jar.get().asFile.apply { parentFile.mkdirs() }
            URI(url).toURL().openStream().use { input -> file.outputStream().use(input::copyTo) }
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            if (actual != sha256) {
                file.delete()
                throw GradleException("$id server jar has SHA-256 $actual, expected $sha256.")
            }
        }
    }

/**
 * Registers [taskName], which runs after [runs] and fails unless every line each plugin wrote to its
 * results file starts with PASS or SKIP, and every key in [required] is present.
 */
fun Project.registerResultsVerification(
    taskName: String,
    description: String,
    runs: List<Pair<String, TaskProvider<RunServer>>>,
    resultsPath: String,
    required: List<String> = emptyList(),
) = tasks.register(taskName) {
    group = "sculk integration"
    this.description = description
    dependsOn(runs.map { it.second })
    val files = runs.map { (label, run) -> label to run.flatMap { it.runDirectory }.map { it.file(resultsPath).asFile } }
    doLast {
        val report = StringBuilder()
        var failed = false
        files.forEach { (label, file) ->
            report.appendLine("$label:")
            val path: File = file.get()
            if (!path.isFile) {
                failed = true
                report.appendLine("  FAIL: no results were written")
                return@forEach
            }
            val results = Properties().apply { path.reader().use(::load) }
            results.stringPropertyNames().sorted().forEach { key ->
                val value = results.getProperty(key)
                if (!value.startsWith("PASS") && !value.startsWith("SKIP")) failed = true
                report.appendLine("  $key: $value")
            }
            required.filterNot(results::containsKey).forEach { missing ->
                failed = true
                report.appendLine("  FAIL: $missing did not report")
            }
        }
        logger.lifecycle(report.toString())
        if (failed) throw GradleException("Integration scenarios failed; see the report above.")
    }
}
