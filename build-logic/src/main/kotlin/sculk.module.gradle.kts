import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The convention every published sculk-* module applies. Examples use sculk.example instead,
// which deliberately omits explicitApi and maven-publish.

plugins {
    kotlin("jvm")
    `java-library`
    `java-test-fixtures`
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun lib(alias: String) = libs.findLibrary(alias).orElseThrow { IllegalStateException("missing catalog entry: $alias") }

fun version(alias: String) = libs.findVersion(alias).orElseThrow { IllegalStateException("missing catalog version: $alias") }.requiredVersion

kotlin {
    jvmToolchain(21)
    explicitApi()
}

java {
    withSourcesJar()
    withJavadocJar()
}

// Framework internals reach for @SculkInternal APIs constantly; opting in once here keeps the
// annotation meaningful at the consumer boundary without an @OptIn at every internal call site.
// This was previously copy-pasted into eleven module build files, and five of them had drifted.
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
        // Enabled only once the rebuild was finished. Turning it on earlier would have made every
        // phase a fight with warnings from modules that had not been touched yet.
        allWarningsAsErrors.set(name.contains("Test").not())
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Mockito's inline mock maker attaches an agent to the running JVM, which the JDK now warns
    // about by default and will eventually refuse outright.
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

dependencies {
    // Coroutines are part of the API surface, not an implementation detail: suspend functions
    // appear in public signatures across data, tasks and commands.
    "api"(lib("coroutines-core"))

    // Paper is never bundled. Test and fixture classpaths need it restated because compileOnly
    // does not propagate to them.
    "compileOnly"(lib("paper-api"))
    "testImplementation"(lib("paper-api"))
    "testFixturesCompileOnly"(lib("paper-api"))

    "testImplementation"(lib("junit-jupiter"))
    // Gradle 9 requires the launcher on the test runtime classpath explicitly.
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version = version("ktlint")
    android = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                url = "https://github.com/SculkStudios/sculk-library"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "sculkstudios"
                        name = "Sculk Studios"
                        url = "https://sculk.studio"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/SculkStudios/sculk-library.git"
                    developerConnection = "scm:git:ssh://github.com/SculkStudios/sculk-library.git"
                    url = "https://github.com/SculkStudios/sculk-library"
                }
            }
        }
    }
}
