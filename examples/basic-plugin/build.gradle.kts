plugins {
    id("sculk.example")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    // A @Serializable config class needs this. Without it kotlinx-serialization cannot find the
    // generated serializer and the plugin fails to enable -- which is exactly what happened the
    // first time this example was booted on a real server.
    alias(libs.plugins.kotlin.serialization)
}

description = "Sculk Studio — basic-plugin example"

dependencies {
    implementation(project(":sculk-platform"))
}

tasks.jar { enabled = false }

tasks.shadowJar {
    archiveClassifier = ""
    archiveFileName = "sculk-example-basic-plugin-${project.version}.jar"
}

// `./gradlew :examples:basic-plugin:runServer` boots a real Paper server with the example on it.
// Automated tests stop at the client boundary, so this is the only thing that proves the bootstrap
// path -- platform start-up, command registration, config generation, clean shutdown -- actually
// works against a server rather than against MockBukkit.
tasks.runServer {
    minecraftVersion("1.21.11")
    runDirectory = layout.projectDirectory.dir("run").asFile
}
