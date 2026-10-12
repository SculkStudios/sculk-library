import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import studio.sculk.gradle.ArchitectureCheck

plugins {
    id("sculk.jvm")
    `java-library`
    id("org.jlleitschuh.gradle.ktlint")
}

val libs = versionCatalogs.named("libs")

kotlin {
    explicitApi()
    compilerOptions {
        optIn.add("studio.sculk.annotation.SculkInternal")
    }
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation {
        filters {
            exclude {
                annotatedWith.add("studio.sculk.annotation.SculkInternal")
            }
        }
    }
}

ktlint {
    version = libs.findVersion("ktlint").get().requiredVersion
}

tasks.named("checkKotlinAbi") {
    mustRunAfter("updateKotlinAbi")
}

val checkArchitecture = tasks.register<ArchitectureCheck>("checkArchitecture") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    classes.from(sourceSets["main"].output.classesDirs)
    moduleName = project.name
    report = layout.buildDirectory.file("reports/architecture.txt")
}

tasks.named("check") {
    dependsOn("checkKotlinAbi", checkArchitecture)
}
