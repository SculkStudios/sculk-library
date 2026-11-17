import studio.sculk.gradle.ArchitectureCheck
import studio.sculk.gradle.serverMatrix

// ADR-0028: code that needs Paper API newer than the floor lives in the `adapters` source set,
// compiled against the newest anchor and packed into the module's own jar. Main code compiles
// against the floor and reaches an adapter only by name, after a startup probe. The adapter
// interfaces are public and @SculkInternal: associating the compilations would make the adapters
// depend on the jar that contains them.

plugins {
    id("sculk.library")
}

val main = sourceSets["main"]
val adapters = sourceSets.create("adapters") {
    compileClasspath += main.output
    runtimeClasspath += main.output
}

configurations.named("adaptersImplementation") { extendsFrom(configurations["implementation"], configurations["api"]) }
configurations.named("adaptersCompileOnly") { extendsFrom(configurations["compileOnly"]) }

dependencies {
    // Conflict resolution picks this over main's floor paper-api, so adapters see the newest API.
    "adaptersCompileOnly"("io.papermc.paper:paper-api:${serverMatrix().paperCurrent.apiVersion}")
}

// The newest anchor's API is published for Java 25. Adapter bytecode stays at 21 like every other
// class, and an adapter runs only where a probe found its types.
configurations.named("adaptersCompileClasspath") {
    attributes { attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}

tasks.named<Jar>("jar") { from(adapters.output) }

tasks.named<ArchitectureCheck>("checkArchitecture") { classes.from(adapters.output.classesDirs) }
