plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — async database abstraction, caching layer, ORM support"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-effects"))
    implementation(kotlin("reflect"))
    implementation(libs.hikari)
    implementation(libs.caffeine)
    implementation(libs.sqlite.jdbc)
    implementation(libs.mariadb.jdbc)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.h2)
}
