plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio runtime for Velocity: lifecycle, proxy execution and coroutine scope."

dependencies {
    api(project(":sculk-platform-api"))
    api(project(":sculk-events"))
    compileOnly(libs.velocity.api)
}

// Velocity 4 publishes its API as a Java 25 library and runs only on Java 25, so compiling against
// it is safe; this module's own bytecode stays at 21 like every other.
configurations.compileClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}
