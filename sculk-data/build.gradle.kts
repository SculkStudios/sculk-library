plugins {
    id("sculk.module")
    alias(libs.plugins.kotlin.serialization)
}

description = "Sculk Studio — suspend repositories, query DSL, schema migration and caching"

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
    }
}

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-config"))
    api(libs.serialization.core)
    api(libs.serialization.json)
    implementation(libs.hikari)
    implementation(libs.caffeine)
    // SQLite is the default backend, so it ships bundled. The remote drivers are referenced only
    // by class name, so they stay opt-in and the shaded jar stays small.
    implementation(libs.sqlite.jdbc)
    compileOnly(libs.mariadb.jdbc)
    compileOnly(libs.postgresql)
    compileOnly(libs.lettuce)

    testImplementation(testFixtures(project(":sculk-common")))
    // Speaks both the MySQL and the Postgres dialect, so the generated SQL is exercised by a real
    // engine rather than asserted as a string.
    testImplementation(libs.h2)
    testRuntimeOnly(libs.postgresql)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
}
