// Modules compiled against the floor Adventure: testAdventureLatest runs the same tests and bytecode
// against the newest Adventure, which Paper 26.2+ and Velocity 4 ship. Applied beside sculk.library.

plugins {
    id("sculk.jvm")
}

val libs = versionCatalogs.named("libs")
val latest = libs.findVersion("adventure-latest").get().requiredVersion

val adventureLatest: Configuration = configurations.create("adventureLatest") {
    isCanBeConsumed = false
}

dependencies {
    adventureLatest(libs.findLibrary("adventure-latest-api").get())
    adventureLatest(libs.findLibrary("adventure-latest-minimessage").get())
    adventureLatest(libs.findLibrary("adventure-latest-plain").get())
}

val testAdventureLatest = tasks.register<Test>("testAdventureLatest") {
    description = "Runs the tests against Adventure $latest, as Paper 26.2+ and Velocity 4 ship it."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val test = sourceSets["test"]
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath.filter { !it.name.startsWith("adventure-") && !it.name.startsWith("examination-") } + adventureLatest
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    val expected = "adventure-api-$latest.jar"
    doFirst {
        val apis = classpath.filter { it.name.startsWith("adventure-api-") }.map { it.name }
        check(apis == listOf(expected)) { "Expected only $expected on the test classpath, found $apis." }
    }
}

tasks.named("check") { dependsOn(testAdventureLatest) }
