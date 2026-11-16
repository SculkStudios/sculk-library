package studio.sculk.gradle

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration

/**
 * Reads the newest stable builds from Fill (Paper, Folia, Velocity) and from Canvas's build API.
 * Only `updateServerMatrix` and the canary call it; ordinary builds never touch the network for
 * server pins.
 */
class UpstreamBuilds {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).followRedirects(HttpClient.Redirect.NORMAL).build()

    /** The newest stable builds of [pinned]'s versions, and what is newer than the pins. */
    fun latest(pinned: ServerMatrix): Latest {
        val notes = mutableListOf<String>()
        fun newest(server: ServerMatrix.Server, project: String): ServerMatrix.Server {
            val build = latestStableBuild(project, server.version) ?: throw GradleException("Fill lists no stable ${server.platform.label} ${server.version} build.")
            if (build != server.build) notes += "${server.platform.label} ${server.version}: build ${server.build} -> $build"
            return server.withBuild(build)
        }
        val paper = pinned.paper.map { newest(it, "paper") }
        val folia = newest(pinned.folia, "folia")
        val canvasBuild = latestCanvasBuild(pinned.canvas.version) ?: throw GradleException("Canvas lists no stable ${pinned.canvas.version} build.")
        val canvas = if (canvasBuild == pinned.canvas.build) {
            pinned.canvas
        } else {
            notes += "Canvas ${pinned.canvas.version}: build ${pinned.canvas.build} -> $canvasBuild"
            pinned.canvas.withBuild(canvasBuild, sha256 = null)
        }
        val velocityVersion = versions("velocity").firstOrNull { !it.endsWith("-SNAPSHOT") } ?: pinned.velocity.version
        val velocity = if (velocityVersion == pinned.velocity.version) {
            newest(pinned.velocity, "velocity")
        } else {
            val build = latestStableBuild("velocity", velocityVersion) ?: throw GradleException("Fill lists no stable Velocity $velocityVersion build.")
            notes += "Velocity: ${pinned.velocity.version} -> $velocityVersion build $build (the velocity-api version moves with it)"
            pinned.velocity.withVersion(velocityVersion, build)
        }
        newerMinecraft(pinned.paperCurrent.version, "paper").forEach { notes += "Paper $it is on Fill with a stable build; it is not an anchor yet." }
        newerMinecraft(pinned.folia.version, "folia").forEach { notes += "Folia $it is on Fill with a stable build; the Folia anchor is ${pinned.folia.version}." }
        return Latest(ServerMatrix(paper, folia, canvas, velocity), notes)
    }

    /** Downloads the Canvas jar for [server] and returns its SHA-256, the pin for that build. */
    fun hashCanvas(server: ServerMatrix.Server, scratch: Path): String {
        val url = checkNotNull(server.jarUrl) { "${server.label} has no jar URL." }
        Files.createDirectories(scratch.parent)
        client.send(request(url), HttpResponse.BodyHandlers.ofFile(scratch)).also { check(it.statusCode() == 200) { "$url answered ${it.statusCode()}." } }
        return try {
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(scratch)).joinToString("") { "%02x".format(it) }
        } finally {
            Files.deleteIfExists(scratch)
        }
    }

    class Latest(val matrix: ServerMatrix, val notes: List<String>)

    private fun latestStableBuild(project: String, version: String): Int? =
        (json("$FILL/$project/versions/$version/builds?channel=STABLE") as? List<*>)
            ?.mapNotNull { ((it as? Map<*, *>)?.get("id") as? Number)?.toInt() }
            ?.maxOrNull()

    /** Every version Fill lists for [project], newest first, as Fill orders them. */
    private fun versions(project: String): List<String> =
        ((json("$FILL/$project") as Map<*, *>)["versions"] as Map<*, *>).values.flatMap { it as List<*> }.map { it.toString() }

    /** Release versions listed before [current] that already have a stable build. */
    private fun newerMinecraft(current: String, project: String): List<String> =
        versions(project).takeWhile { it != current }.filter { PRERELEASE.containsMatchIn(it).not() }.filter { latestStableBuild(project, it) != null }

    private fun latestCanvasBuild(version: String): Int? =
        ((json(CANVAS) as Map<*, *>)["builds"] as List<*>)
            .mapNotNull { it as? Map<*, *> }
            .filter { it["channelVersion"] == version && it["result"] == "SUCCESS" && it["isExperimental"] != true }
            .mapNotNull { (it["buildNumber"] as? Number)?.toInt() }
            .maxOrNull()

    private fun json(url: String): Any {
        val response = client.send(request(url), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) throw GradleException("$url answered ${response.statusCode()}.")
        return JsonSlurper().parseText(response.body())
    }

    private fun request(url: String): HttpRequest =
        HttpRequest.newBuilder(URI(url)).timeout(Duration.ofMinutes(2)).header("User-Agent", USER_AGENT).GET().build()

    private companion object {
        const val FILL = "https://fill.papermc.io/v3/projects"
        const val CANVAS = "https://canvasmc.io/api/v2/builds?project=canvas"

        // Fill asks automated clients to identify themselves.
        const val USER_AGENT = "sculk-studio-build/6 (+https://github.com/SculkStudios/sculk-library)"
        val PRERELEASE = Regex("-(pre|rc|snapshot|SNAPSHOT)|w\\d")
    }
}
