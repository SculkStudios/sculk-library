plugins {
    id("sculk.module")
    alias(libs.plugins.kotlin.serialization)
}

description = "Sculk Studio — backend-neutral Discord API: messages as data, typed component ids, webhooks"

dependencies {
    api(project(":sculk-common"))

    // Only for SculkTheme/ThemeStyle, which carry no Adventure imports. Reaching for SculkMessages
    // here would put Adventure on the runtime classpath of a standalone bot that has no Minecraft
    // anywhere in it.
    api(project(":sculk-text"))

    // Webhook payloads. Both consumers hand-rolled a JSON string escaper for this and neither
    // covered the same control characters.
    implementation(libs.serialization.json)

    testImplementation(libs.coroutines.test)
}

/**
 * Fails if anything in this module references Discord's client library or Bukkit.
 *
 * Both bans are load-bearing and neither survives as a convention. A JDA import here would make the
 * backend unswappable, which is the whole reason the module is split — in 4.x the hologram service
 * reached past an identical boundary straight into PacketEvents, and the result was that ProtocolLib
 * could not serve holograms at all. A Bukkit import would mean a standalone bot needs a Minecraft
 * server on its classpath to start.
 *
 * The compiled classes are scanned rather than the compile classpath, because the convention plugin
 * puts Paper on every module's classpath as `compileOnly` whether or not a line of it is used. What
 * matters is whether the emitted code actually names one.
 */
val forbiddenReferences by tasks.registering {
    dependsOn(tasks.named("compileKotlin"))

    val classes = layout.buildDirectory.dir("classes/kotlin/main")
    val marker = layout.buildDirectory.file("reports/forbidden-references.txt")
    inputs.dir(classes).withPropertyName("classes")
    // A real output rather than `upToDateWhen { true }`: with no output declared, the first run makes
    // the task permanently up to date and it never checks again.
    outputs.file(marker)

    doLast {
        val banned = mapOf(
            "org/bukkit" to "Bukkit — sculk-discord must start without a Minecraft server",
            "io/papermc" to "Paper — sculk-discord must start without a Minecraft server",
            "net/dv8tion" to "JDA — the backend belongs in sculk-discord-jda",
        )
        val offences = classes.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .flatMap { file ->
                val text = file.readBytes().toString(Charsets.ISO_8859_1)
                banned.entries.filter { text.contains(it.key) }.map { "${file.name} references ${it.value}" }
            }.toList()

        if (offences.isNotEmpty()) {
            throw GradleException("sculk-discord broke its dependency ban:\n" + offences.joinToString("\n") { "  - $it" })
        }
        marker.get().asFile.apply {
            parentFile.mkdirs()
            writeText("clean\n")
        }
    }
}

tasks.named("check") { dependsOn(forbiddenReferences) }
