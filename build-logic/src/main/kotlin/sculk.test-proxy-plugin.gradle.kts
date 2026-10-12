import studio.sculk.gradle.VerifyBytecodeLevel

// A Velocity plugin that exists only to run Sculk on a real proxy in CI. Never published.

plugins {
    id("sculk.jvm")
    id("com.gradleup.shadow")
    id("xyz.jpenilla.run-velocity")
}

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    val prefix = "studio.sculk.itest.${project.name.replace('-', '_')}.libs"
    relocate("kotlinx", "$prefix.kotlinx")
    relocate("kotlin", "$prefix.kotlin")
}

tasks.named<VerifyBytecodeLevel>("verifyBytecodeLevel") {
    archives.setFrom(tasks.shadowJar)
}

// Velocity 4 publishes its API for Java 25 and runs only on Java 25.
configurations.named("compileClasspath") {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}
