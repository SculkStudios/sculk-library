plugins {
    id("sculk.library")
    id("sculk.publish")
}

description =
    "Sculk Studio placeholders: typed global, audience and relational placeholders as MiniMessage tags, with refresh policies and change signals."

dependencies {
    api(project(":sculk-text"))
    api(project(":sculk-coroutines"))
    // The server provides Adventure; placeholders reach MiniMessage as tag resolvers.
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    testImplementation(project(":sculk-testkit"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
}

val adventureLatest: Configuration by configurations.creating {
    isCanBeConsumed = false
}

dependencies {
    adventureLatest(libs.adventure.latest.api)
    adventureLatest(libs.adventure.latest.minimessage)
    adventureLatest(libs.adventure.latest.plain)
}

// A tag resolver is an Adventure interface Sculk implements, so the same bytecode is proven on Adventure 5 too.
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
