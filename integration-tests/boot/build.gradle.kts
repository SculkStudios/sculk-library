import xyz.jpenilla.runpaper.task.RunServer
import java.io.InputStream
import java.time.Duration
import java.util.Properties

plugins {
    id("sculk.test-plugin")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-core"))
}

data class Anchor(val name: String, val minecraft: String, val java: Int, val port: Int)

val anchors = listOf(
    Anchor("Floor", "1.21.4", 21, 25601),
    Anchor("Current", "26.3", 25, 25602),
)

val bootTasks = anchors.map { anchor ->
    tasks.register<RunServer>("bootPaper${anchor.name}") {
        group = "sculk integration"
        description = "Boots Paper ${anchor.minecraft} on Java ${anchor.java} with the boot plugin."
        minecraftVersion(anchor.minecraft)
        pluginJars(tasks.shadowJar.flatMap { it.archiveFile })
        runDirectory = layout.buildDirectory.dir("servers/paper-${anchor.minecraft}")
        javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(anchor.java) }
        jvmArgs("-Xmx1536m", "-Dcom.mojang.eula.agree=true", "-Dpaper.disableChannelLimit=true")
        standardInput = InputStream.nullInputStream()
        timeout = Duration.ofMinutes(10)
        doFirst {
            val dir = runDirectory.get().asFile
            dir.resolve("plugins/SculkBoot/result.properties").delete()
            dir.mkdirs()
            dir.resolve("server.properties").writeText("online-mode=false\nserver-port=${anchor.port}\nlevel-type=minecraft\\:flat\ngenerate-structures=false\n")
        }
    }
}

val verifyBoot = tasks.register("verifyBoot") {
    group = "sculk integration"
    description = "Boots every anchor and checks that sculk-core ran on the expected Minecraft and Java versions."
    dependsOn(bootTasks)
    val results = anchors.map { anchor ->
        anchor to layout.buildDirectory.file("servers/paper-${anchor.minecraft}/plugins/SculkBoot/result.properties")
    }
    doLast {
        val failures = results.mapNotNull { (anchor, file) ->
            val path = file.get().asFile
            if (!path.isFile) return@mapNotNull "${anchor.minecraft}: no result written, the plugin did not run"
            val result = Properties().apply { path.reader().use(::load) }
            val problems = buildList {
                if (result.getProperty("minecraft") != anchor.minecraft) add("reported Minecraft ${result.getProperty("minecraft")}")
                if (result.getProperty("parsed") != anchor.minecraft) add("MinecraftVersion parsed '${result.getProperty("parsed")}'")
                if (result.getProperty("java") != anchor.java.toString()) add("ran on Java ${result.getProperty("java")}")
            }
            if (problems.isEmpty()) null else "${anchor.minecraft}: ${problems.joinToString("; ")}"
        }
        if (failures.isNotEmpty()) throw GradleException("Boot verification failed:\n" + failures.joinToString("\n") { "  $it" })
        results.forEach { (anchor, _) -> logger.lifecycle("Booted Paper ${anchor.minecraft} on Java ${anchor.java}: ok") }
    }
}
