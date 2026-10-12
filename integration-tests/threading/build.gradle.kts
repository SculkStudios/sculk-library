import xyz.jpenilla.runpaper.task.RunServer
import xyz.jpenilla.runtask.service.DownloadsAPIService
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest
import java.time.Duration
import java.util.Properties

plugins {
    id("sculk.test-plugin")
}

// canvas-api contains the whole Paper API and declares the same capability, so it replaces paper-api here.
dependencies {
    compileOnly(libs.canvas.api)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-canvas"))
}

configurations.compileClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}

/** One real server the threading scenarios run on. */
data class Target(
    val name: String,
    val platform: String,
    val minecraft: String,
    val java: Int,
    val port: Int,
    val build: Int? = null,
    val serverJarUrl: String? = null,
    /** SHA-256 of [serverJarUrl], checked after download; a CI server jar is code we run. */
    val serverJarSha256: String? = null,
    /** Folia's and Canvas's threaded-regions.grid-exponent; null leaves the server default. */
    val gridExponent: Int? = null,
)

val targets = listOf(
    Target("Paper1214", "PAPER", "1.21.4", 21, 25611),
    Target("Paper263", "PAPER", "26.3", 25, 25612),
    Target("Folia2612", "FOLIA", "26.1.2", 25, 25613, build = 8, gridExponent = 4),
    Target("Folia2612Grid5", "FOLIA", "26.1.2", 25, 25615, build = 8, gridExponent = 5),
    Target(
        "Canvas263",
        "CANVAS",
        "26.3",
        25,
        25614,
        serverJarUrl = "https://jenkins.canvasmc.io/job/Canvas/978/artifact/canvas-server/build/libs/canvas-build.978.jar",
        serverJarSha256 = "011d9096792b218456cbca2b962883f852da9ff8b9674d884ead9b9671801356",
        gridExponent = 4,
    ),
)

val runTasks = targets.map { target ->
    val serverDir = layout.buildDirectory.dir("servers/${target.name}")
    val download = target.serverJarUrl?.let { url ->
        val jar = layout.buildDirectory.file("server-jars/${target.name}.jar")
        val expected = requireNotNull(target.serverJarSha256) { "${target.name} downloads a server jar without a checksum." }
        tasks.register("download${target.name}") {
            inputs.property("url", url)
            inputs.property("sha256", expected)
            outputs.file(jar)
            doLast {
                val file = jar.get().asFile.apply { parentFile.mkdirs() }
                URI(url).toURL().openStream().use { input -> file.outputStream().use(input::copyTo) }
                val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
                if (actual != expected) {
                    file.delete()
                    throw GradleException("${target.name} server jar has SHA-256 $actual, expected $expected.")
                }
            }
        }
    }
    tasks.register<RunServer>("threading${target.name}") {
        group = "sculk integration"
        description = "Runs the threading scenarios on ${target.platform} ${target.minecraft} with Java ${target.java}."
        minecraftVersion(target.minecraft)
        if (download != null) {
            dependsOn(download)
            serverJar(layout.buildDirectory.file("server-jars/${target.name}.jar"))
        } else {
            if (target.platform == "FOLIA") downloadsApiService = DownloadsAPIService.folia(project)
            target.build?.let { build = DownloadsAPIService.Build.Specific(it) }
        }
        pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
        runDirectory = serverDir
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(target.java) }
        jvmArgs("-Xmx2g", "-Dcom.mojang.eula.agree=true", "-Dsculk.itest.platform=${target.platform}", "-Dsculk.development=true")
        target.gridExponent?.let { jvmArgs("-Dsculk.itest.gridExponent=$it") }
        standardInput = InputStream.nullInputStream()
        timeout = Duration.ofMinutes(10)
        doFirst {
            val dir = serverDir.get().asFile
            dir.resolve("plugins/SculkThreading/results.properties").delete()
            dir.mkdirs()
            dir.resolve("eula.txt").writeText("eula=true\n")
            dir.resolve("server.properties").writeText("online-mode=false\nserver-port=${target.port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\nspawn-protection=0\n")
            // A minimal file; the server fills in every other key and keeps this one.
            target.gridExponent?.let { exponent ->
                dir.resolve("config").mkdirs()
                dir.resolve("config/paper-global.yml").writeText("threaded-regions:\n  grid-exponent: $exponent\n")
            }
        }
    }
}

tasks.register("verifyThreading") {
    group = "sculk integration"
    description = "Runs the threading scenarios on Paper 1.21.4, Paper 26.3, Folia 26.1.2 (grid-exponent 4 and 5) and Canvas 26.3 and checks every result."
    dependsOn(runTasks)
    val results = targets.map { it to layout.buildDirectory.file("servers/${it.name}/plugins/SculkThreading/results.properties") }
    doLast {
        val report = StringBuilder()
        var failed = false
        results.forEach { (target, file) ->
            val path = file.get().asFile
            val grid = target.gridExponent?.let { ", grid-exponent $it" }.orEmpty()
            report.appendLine("${target.platform} ${target.minecraft} on Java ${target.java}$grid:")
            if (!path.isFile) {
                failed = true
                report.appendLine("  FAIL: no results were written")
                return@forEach
            }
            val properties = Properties().apply { path.reader().use(::load) }
            properties.stringPropertyNames().sorted().forEach { key ->
                val value = properties.getProperty(key)
                if (!value.startsWith("PASS") && !value.startsWith("SKIP")) failed = true
                report.appendLine("  $key: $value")
            }
            if ("shutdown-cancels-pending-work" !in properties.stringPropertyNames()) {
                failed = true
                report.appendLine("  FAIL: the shutdown scenario did not report")
            }
        }
        logger.lifecycle(report.toString())
        if (failed) throw GradleException("Threading scenarios failed; see the report above.")
    }
}
