plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio text: themes, safe MiniMessage rendering, translations and glyph measurement."

dependencies {
    api(project(":sculk-core"))
    // The server provides Adventure. Compiling against the floor version keeps the module linkable on
    // every anchor; testAdventureLatest proves the same bytecode behaves on Adventure 5.
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
}

// CLDR's plural rules, vendored from cldr-json and turned into Kotlin at build time (ADR-0024).
val cldrPlurals = layout.projectDirectory.file("src/cldr/plurals.json")
val generatePluralRules = tasks.register<studio.sculk.gradle.GeneratePluralRules>("generatePluralRules") {
    plurals = cldrPlurals
    packageName = "studio.sculk.text"
    output = layout.buildDirectory.dir("generated/cldr/kotlin")
}

kotlin.sourceSets.named("main") { kotlin.srcDir(generatePluralRules) }

ktlint {
    filter { exclude { it.file.path.contains("generated") } }
}

// Unicode-3.0 asks for its notice to travel with the data, and the rules are derived from it.
tasks.processResources {
    from("src/cldr/LICENSE") {
        into("META-INF")
        rename { "LICENSE-unicode-cldr.txt" }
    }
}

tasks.withType<Test>().configureEach {
    inputs.file(cldrPlurals).withPathSensitivity(PathSensitivity.NONE)
    systemProperty("sculk.cldr.plurals", cldrPlurals.asFile.absolutePath)
}

val adventureLatest: Configuration = configurations.create("adventureLatest") {
    isCanBeConsumed = false
}

dependencies {
    adventureLatest(libs.adventure.latest.api)
    adventureLatest(libs.adventure.latest.minimessage)
    adventureLatest(libs.adventure.latest.plain)
}

val testAdventureLatest = tasks.register<Test>("testAdventureLatest") {
    description = "Runs the tests against Adventure ${libs.versions.adventure.latest.get()}, as Paper 26.2+ and Velocity 4 ship it."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val test = sourceSets["test"]
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath.filter { !it.name.startsWith("adventure-") && !it.name.startsWith("examination-") } + adventureLatest
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    val expected = "adventure-api-${libs.versions.adventure.latest.get()}.jar"
    doFirst {
        val apis = classpath.filter { it.name.startsWith("adventure-api-") }.map { it.name }
        check(apis == listOf(expected)) { "Expected only $expected on the test classpath, found $apis." }
    }
}

tasks.named("check") { dependsOn(testAdventureLatest) }
