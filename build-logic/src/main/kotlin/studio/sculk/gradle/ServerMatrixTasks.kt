package studio.sculk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Fails when the version catalog or COMPATIBILITY.md disagrees with `gradle/servers.json`, so a pin
 * cannot move without the API it compiles against and the document that promises it.
 */
abstract class CheckServerMatrix : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val servers: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalog: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val compatibility: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val matrix = ServerMatrix.read(servers.get().asFile)
        val catalogVersions = catalogVersions(catalog.get().asFile)
        val document = compatibility.get().asFile.readText()
        val problems = buildList {
            fun catalogPin(key: String, expected: String) {
                val actual = catalogVersions[key]
                if (actual != expected) add("libs.versions.toml '$key' is ${actual ?: "missing"}; servers.json pins $expected.")
            }
            catalogPin("paper-floor", matrix.paperFloor.apiVersion)
            catalogPin("canvas", matrix.canvas.apiVersion)
            catalogPin("velocity", matrix.velocity.version)
            val protocolLib = catalogVersions["mcprotocollib"]
            if (protocolLib?.substringBefore('-') != matrix.client.version) {
                add("libs.versions.toml 'mcprotocollib' is $protocolLib; the test client in servers.json speaks ${matrix.client.version}.")
            }
            (matrix.paper.map { it.apiVersion } + "velocity-api:${matrix.velocity.version}")
                .filterNot(document::contains)
                .forEach { add("COMPATIBILITY.md does not mention $it.") }
            if ("Folia ${matrix.folia.version}" !in document) add("COMPATIBILITY.md does not name the Folia anchor, Folia ${matrix.folia.version}.")
            if ("build ${matrix.canvas.build}" !in document) add("COMPATIBILITY.md does not name the pinned Canvas build ${matrix.canvas.build}.")
        }
        report.get().asFile.writeText(problems.joinToString("\n", postfix = "\n"))
        if (problems.isNotEmpty()) {
            throw GradleException("The server pins disagree with what depends on them:\n" + problems.joinToString("\n") { "  - $it" })
        }
    }

    private fun catalogVersions(file: File): Map<String, String> {
        val versions = file.readText().substringAfter("[versions]").substringBefore("\n[")
        return VERSION_LINE.findAll(versions).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private companion object {
        val VERSION_LINE = Regex("""(?m)^([\w-]+)\s*=\s*"([^"]+)"""")
    }
}

/**
 * Moves `gradle/servers.json` to the newest stable build of every pinned version, and the version
 * catalog with it. With `--report-only` it only reports, which is what the canary does.
 */
@DisableCachingByDefault(because = "Reads the network")
abstract class UpdateServerMatrix : DefaultTask() {
    @get:Internal
    abstract val servers: RegularFileProperty

    @get:Internal
    abstract val catalog: RegularFileProperty

    @get:Internal
    abstract val scratch: RegularFileProperty

    @get:Input
    @get:Option(option = "report-only", description = "Report newer builds without changing any file.")
    abstract val reportOnly: Property<Boolean>

    init {
        reportOnly.convention(false)
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun update() {
        val file = servers.get().asFile
        val pinned = ServerMatrix.read(file)
        val upstream = UpstreamBuilds()
        val latest = upstream.latest(pinned)
        val summary = if (latest.notes.isEmpty()) "Every server pin is the newest stable build." else latest.notes.joinToString("\n") { "- $it" }
        logger.lifecycle(summary)
        System.getenv("GITHUB_STEP_SUMMARY")?.let { File(it).appendText("### Server builds\n\n$summary\n") }
        if (reportOnly.get() || latest.notes.isEmpty()) return

        val canvas = latest.matrix.canvas.let { if (it.sha256 != null) it else it.withBuild(it.build, upstream.hashCanvas(it, scratch.get().asFile.toPath())) }
        val updated = ServerMatrix(latest.matrix.paper, latest.matrix.folia, canvas, latest.matrix.velocity, latest.matrix.client)
        file.writeText(updated.toJson())
        val catalogFile = catalog.get().asFile
        catalogFile.writeText(
            catalogFile.readText()
                .replace(Regex("""(?m)^canvas = "[^"]+""""), "canvas = \"${updated.canvas.apiVersion}\"")
                .replace(Regex("""(?m)^velocity = "[^"]+""""), "velocity = \"${updated.velocity.version}\""),
        )
        logger.lifecycle(
            "Updated ${file.name} and the version catalog. Next: regenerate dependency verification (CONTRIBUTING.md), " +
                "update COMPATIBILITY.md until checkServerMatrix passes, and run the real-server suites.",
        )
    }
}
