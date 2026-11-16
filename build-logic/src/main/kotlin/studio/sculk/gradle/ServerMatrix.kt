package studio.sculk.gradle

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties

/**
 * Every real server Sculk's suites run on, read from `gradle/servers.json`: the one place a server
 * version or build changes. `updateServerMatrix` moves the pins to the newest stable builds and
 * `checkServerMatrix` keeps the version catalog and COMPATIBILITY.md in step with them.
 */
class ServerMatrix(
    /** Every Paper anchor, oldest first. */
    val paper: List<Server>,
    val folia: Server,
    val canvas: Server,
    val velocity: Server,
) {
    enum class Platform(val label: String) { PAPER("Paper"), FOLIA("Folia"), CANVAS("Canvas"), VELOCITY("Velocity") }

    class Server(
        val platform: Platform,
        val version: String,
        val build: Int,
        val java: Int,
        /** A 1.21.x Paper API snapshot; 26.x APIs are published per build as `<version>.build.<n>-stable`. */
        private val api: String? = null,
        /** Canvas is not on Fill, so its jar is pinned by hash here; null only for an unpinned canary build. */
        val sha256: String? = null,
    ) {
        val id: String get() = platform.label + version.filter(Char::isDigit)
        val isRegionized: Boolean get() = platform == Platform.FOLIA || platform == Platform.CANVAS

        /** The API artifact version built from the same commit as this server. */
        val apiVersion: String get() = api ?: "$version.build.$build-stable"

        val jarUrl: String?
            get() = if (platform == Platform.CANVAS) "https://jenkins.canvasmc.io/job/Canvas/$build/artifact/canvas-server/build/libs/canvas-build.$build.jar" else null

        val label: String get() = "${platform.name} $version on Java $java"

        fun withBuild(build: Int, sha256: String? = this.sha256): Server = Server(platform, version, build, java, api, sha256)

        fun withVersion(version: String, build: Int): Server = Server(platform, version, build, java, null, null)

        internal fun toJson(): String = buildString {
            append("{ \"version\": \"$version\", \"build\": $build, \"java\": $java")
            api?.let { append(", \"api\": \"$it\"") }
            sha256?.let { append(", \"sha256\": \"$it\"") }
            append(" }")
        }
    }

    val paperFloor: Server get() = paper.first()
    val paperCurrent: Server get() = paper.last()

    /** One server per threading model: the Paper floor and current anchors, Folia and Canvas. */
    val all: List<Server> get() = listOf(paperFloor, paperCurrent, folia, canvas)
    val paperAndFolia: List<Server> get() = listOf(paperFloor, paperCurrent, folia)

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"paper\": [")
        appendLine(paper.joinToString(",\n") { "    ${it.toJson()}" })
        appendLine("  ],")
        appendLine("  \"folia\": ${folia.toJson()},")
        appendLine("  \"canvas\": ${canvas.toJson()},")
        appendLine("  \"velocity\": ${velocity.toJson()}")
        appendLine("}")
    }

    companion object {
        const val FILE: String = "gradle/servers.json"

        /** `-Psculk.servers=latest` runs the newest stable builds of the pinned versions instead: the canary. */
        const val MODE_PROPERTY: String = "sculk.servers"

        private const val CACHE_KEY = "studio.sculk.serverMatrix"

        /** The matrix for this build, read once per build and kept on the root project. */
        fun of(project: Project): ServerMatrix {
            val extra = project.rootProject.extensions.extraProperties
            if (extra.has(CACHE_KEY)) return extra.get(CACHE_KEY) as ServerMatrix
            val pinned = read(project.rootProject.file(FILE))
            val matrix = when (val mode = project.providers.gradleProperty(MODE_PROPERTY).orNull) {
                null, "pinned" -> pinned
                "latest" -> UpstreamBuilds().latest(pinned).matrix.also { project.logger.lifecycle("Running the newest server builds:\n${it.toJson()}") }
                else -> throw GradleException("-P$MODE_PROPERTY=$mode: expected 'pinned' or 'latest'.")
            }
            extra.set(CACHE_KEY, matrix)
            return matrix
        }

        fun read(file: File): ServerMatrix {
            if (!file.isFile) throw GradleException("$file is missing; it pins every server the suites run on.")
            val root = JsonSlurper().parse(file) as Map<*, *>

            fun server(platform: Platform, node: Any?): Server {
                val map = node as? Map<*, *> ?: throw GradleException("$file: '${platform.label.lowercase()}' must be an object.")
                fun int(key: String) = (map[key] as? Number)?.toInt() ?: throw GradleException("$file: ${platform.label} '$key' is missing.")
                val version = map["version"] as? String ?: throw GradleException("$file: ${platform.label} 'version' is missing.")
                return Server(platform, version, int("build"), int("java"), map["api"] as? String, map["sha256"] as? String)
            }

            val paper = (root["paper"] as? List<*>)?.map { server(Platform.PAPER, it) }.orEmpty()
            if (paper.isEmpty()) throw GradleException("$file: 'paper' must list at least one anchor, oldest first.")
            val canvas = server(Platform.CANVAS, root["canvas"])
            if (canvas.sha256 == null) throw GradleException("$file: Canvas needs a 'sha256'; its jar does not come from Fill.")
            return ServerMatrix(paper, server(Platform.FOLIA, root["folia"]), canvas, server(Platform.VELOCITY, root["velocity"]))
        }
    }
}

/** The matrix for this build; see [ServerMatrix.of]. */
fun Project.serverMatrix(): ServerMatrix = ServerMatrix.of(this)

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
    /** Other plugins to load beside [pluginJar], such as an example the plugin checks. */
    otherPlugins: List<Provider<RegularFile>> = emptyList(),
): TaskProvider<RunServer> {
    val runDirectory = layout.buildDirectory.dir("servers/${taskName.removePrefix("run")}")
    val download = server.jarUrl?.let { url -> registerVerifiedDownload("${server.id}b${server.build}", url, server.sha256) }
    val toolchains = extensions.getByType<JavaToolchainService>()
    return tasks.register<RunServer>(taskName) {
        group = "sculk integration"
        description = "Runs ${server.platform.label} ${server.version} build ${server.build} on Java ${server.java}" +
            gridExponent?.let { " at grid-exponent $it" }.orEmpty() + "."
        minecraftVersion(server.version)
        if (download != null) {
            dependsOn(download)
            serverJar(layout.buildDirectory.file("server-jars/${server.id}b${server.build}.jar"))
        } else {
            // Fill builds are pinned by number; run-task checks each download against Fill's SHA-256.
            if (server.platform == ServerMatrix.Platform.FOLIA) downloadsApiService.set(DownloadsAPIService.folia(project))
            build.set(DownloadsAPIService.Build.Specific(server.build))
        }
        pluginJars(pluginJar)
        otherPlugins.forEach { pluginJars(it) }
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

/** Downloads [url] once and refuses it unless it hashes to [sha256]; a server jar is code that runs. */
private fun Project.registerVerifiedDownload(id: String, url: String, sha256: String?) =
    tasks.findByName("download$id")?.let { tasks.named("download$id") } ?: tasks.register("download$id") {
        val jar = layout.buildDirectory.file("server-jars/$id.jar")
        inputs.property("url", url)
        inputs.property("sha256", sha256.orEmpty())
        outputs.file(jar)
        doLast {
            val file = jar.get().asFile.apply { parentFile.mkdirs() }
            URI(url).toURL().openStream().use { input -> file.outputStream().use(input::copyTo) }
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            when {
                // Only the canary runs an unpinned jar: the newest build, to learn whether it breaks Sculk.
                sha256 == null -> logger.warn("$id server jar is not pinned; it hashed to $actual.")
                actual != sha256 -> {
                    file.delete()
                    throw GradleException("$id server jar has SHA-256 $actual, expected $sha256.")
                }
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
