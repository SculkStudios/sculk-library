plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio support for Canvas: regionized data, extra ownership checks, async world unload and the region tick manager."

dependencies {
    api(project(":sculk-paper"))
    compileOnly(libs.canvas.api)
}

// Canvas publishes its API as a Java 25 library. This module only ever runs on Canvas, which requires
// Java 25, so compiling against it is safe; the module's own bytecode stays at 21 like every other.
configurations.compileClasspath {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}
