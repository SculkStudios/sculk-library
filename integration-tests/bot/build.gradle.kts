// A headless Minecraft client the server suites drive from inside a test plugin (ADR-0029). It runs
// in its own JVM, so its Netty and Adventure never meet the server's. Never published.

plugins {
    id("sculk.jvm")
    id("com.gradleup.shadow")
    application
}

dependencies {
    implementation(libs.mcprotocollib)
    implementation(libs.adventure.latest.plain)
}

application {
    mainClass = "studio.sculk.itest.bot.BotMainKt"
}

// The runnable jar is the shadow jar; the plain one gets a classifier so the two never share a file.
tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    mergeServiceFiles()
}

// The client speaks 26.3's protocol and is built with the Java that version requires.
configurations.named("compileClasspath") {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}
configurations.named("runtimeClasspath") {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}
