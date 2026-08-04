plugins {
    id("sculk.module")
}

description = "Sculk Studio — backend-neutral embedded HTTP server: routes as data, no server library"

dependencies {
    api(project(":sculk-common"))

    testImplementation(libs.coroutines.test)
}

/**
 * Fails if anything in this module references a server library or Bukkit.
 *
 * The same ban, for the same reason, as the one guarding sculk-discord. A Ktor import here would
 * make the backend unswappable, which is the entire point of splitting the module; a Bukkit import
 * would mean a standalone tool needs a Minecraft server on its classpath to serve a page.
 *
 * Compiled classes are scanned rather than the compile classpath, because the convention plugin
 * puts Paper on every module's classpath as `compileOnly` whether or not a line of it is used.
 */
val forbiddenReferences by tasks.registering {
    dependsOn(tasks.named("compileKotlin"))

    val classes = layout.buildDirectory.dir("classes/kotlin/main")
    val marker = layout.buildDirectory.file("reports/forbidden-references.txt")
    inputs.dir(classes).withPropertyName("classes")
    outputs.file(marker)

    doLast {
        val banned =
            mapOf(
                "org/bukkit" to "Bukkit — sculk-web must start without a Minecraft server",
                "io/papermc" to "Paper — sculk-web must start without a Minecraft server",
                "io/ktor" to "Ktor — the backend belongs in sculk-web-ktor",
                "io/javalin" to "Javalin — a backend belongs in its own module",
            )
        val offences =
            classes
                .get()
                .asFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "class" }
                .flatMap { file ->
                    val text = file.readBytes().toString(Charsets.ISO_8859_1)
                    banned.entries.filter { text.contains(it.key) }.map { "${file.name} references ${it.value}" }
                }.toList()

        if (offences.isNotEmpty()) {
            throw GradleException("sculk-web broke its dependency ban:\n" + offences.joinToString("\n") { "  - $it" })
        }
        marker.get().asFile.apply {
            parentFile.mkdirs()
            writeText("clean\n")
        }
    }
}

tasks.named("check") { dependsOn(forbiddenReferences) }
