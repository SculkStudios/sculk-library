import xyz.jpenilla.runvelocity.task.RunVelocity
import java.io.InputStream
import java.time.Duration
import java.util.Properties

plugins {
    id("sculk.test-proxy-plugin")
}

dependencies {
    compileOnly(libs.velocity.api)
    implementation(project(":sculk-velocity"))
    implementation(project(":sculk-commands-velocity"))
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("velocity-plugin.json") { expand("version" to pluginVersion) }
}


val proxyDir = layout.buildDirectory.dir("proxy")
val resultsFile = proxyDir.map { it.file("plugins/sculk-threading/results.properties") }

val runProxy = tasks.register<RunVelocity>("threadingVelocity") {
    group = "sculk integration"
    description = "Runs the proxy scenarios on Velocity ${libs.versions.velocity.get()} with Java 25."
    velocityVersion(libs.versions.velocity.get())
    pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
    runDirectory = proxyDir
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    jvmArgs("-Xmx512m", "-Dsculk.development=true")
    standardInput = InputStream.nullInputStream()
    timeout = Duration.ofMinutes(5)
    doFirst { resultsFile.get().asFile.delete() }
}

tasks.register("verifyVelocity") {
    group = "sculk integration"
    description = "Runs the proxy scenarios on a real Velocity and checks every result."
    dependsOn(runProxy)
    doLast {
        val file = resultsFile.get().asFile
        if (!file.isFile) throw GradleException("Velocity wrote no results; the plugin did not run.")
        val properties = Properties().apply { file.reader().use(::load) }
        val lines = properties.stringPropertyNames().sorted().map { "  $it: ${properties.getProperty(it)}" }
        logger.lifecycle("Velocity ${libs.versions.velocity.get()} on Java 25:\n" + lines.joinToString("\n"))
        val failed = properties.stringPropertyNames().filterNot { properties.getProperty(it).startsWith("PASS") }
        if (failed.isNotEmpty() || "shutdown-reached" !in properties.stringPropertyNames()) {
            throw GradleException("Velocity scenarios failed: ${failed.ifEmpty { listOf("shutdown-reached missing") }}")
        }
    }
}
