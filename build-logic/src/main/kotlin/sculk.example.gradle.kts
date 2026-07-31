import org.gradle.api.artifacts.VersionCatalogsExtension

// Showcase plugins under examples/. They are compile gates, not products: nothing here is
// published, and explicitApi is off because example code should read the way a consumer would
// actually write it.

plugins {
    kotlin("jvm")
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing catalog entry: $alias") }

fun version(alias: String) = libs.findVersion(alias).orElseThrow { IllegalStateException("missing catalog version: $alias") }.requiredVersion

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

dependencies {
    "compileOnly"(lib("paper-api"))
    "testImplementation"(lib("paper-api"))
    "testImplementation"(lib("junit-jupiter"))
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version = version("ktlint")
    android = false
}
