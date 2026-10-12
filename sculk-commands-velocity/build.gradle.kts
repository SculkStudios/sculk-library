plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio commands on Velocity: compiles command specs into BrigadierCommands with a proxy argument catalogue."

dependencies {
    api(project(":sculk-commands"))
    api(project(":sculk-velocity"))
    // Velocity brings Adventure and its own Brigadier fork; Sculk never declares either.
    compileOnly(libs.velocity.api)
}

// Velocity 4 publishes its API as a Java 25 library and runs only on Java 25, so compiling against
// it is safe; this module's own bytecode stays at 21 like every other.
configurations.compileClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}
