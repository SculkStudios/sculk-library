import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import studio.sculk.gradle.VerifyBytecodeLevel

plugins {
    kotlin("jvm")
}

val buildJdk = 25
val targetJdk = 21

val libs = versionCatalogs.named("libs")

kotlin {
    jvmToolchain(buildJdk)
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        // jvmTarget alone still resolves against the JDK 25 class library; this is the fence.
        freeCompilerArgs.add("-Xjdk-release=$targetJdk")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = targetJdk
}

tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    compilerOptions.allWarningsAsErrors = true
}

dependencies {
    "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testRuntimeOnly"(libs.findLibrary("junit-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(targetJdk) }
}

val testJdk25 = tasks.register<Test>("testJdk25") {
    description = "Runs the unit tests on Java $buildJdk."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    val test = sourceSets["test"]
    testClassesDirs = test.output.classesDirs
    classpath = test.runtimeClasspath
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(buildJdk) }
}

val verifyBytecodeLevel = tasks.register<VerifyBytecodeLevel>("verifyBytecodeLevel") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    archives.from(tasks.named("jar"))
    maxJavaVersion = targetJdk
    report = layout.buildDirectory.file("reports/bytecode-level.txt")
}

tasks.named("check") {
    dependsOn(testJdk25, verifyBytecodeLevel)
}
