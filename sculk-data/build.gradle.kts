plugins {
    id("sculk.paper-plugin")
    alias(libs.plugins.kotlin.serialization)
}

description = "Sculk Studio — async database abstraction, caching layer, ORM support"

// Allow framework internals within this module.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.core.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-effects"))
    implementation(kotlin("reflect"))
    implementation(libs.hikari)
    implementation(libs.caffeine)
    // SQLite is the default backend, so it ships bundled. The MySQL/MariaDB driver is referenced
    // only by class name (see ConnectionPool), so it stays opt-in — plugins that use MySQL add
    // `org.mariadb.jdbc:mariadb-java-client` themselves, keeping the shaded jar small.
    implementation(libs.sqlite.jdbc)
    compileOnly(libs.mariadb.jdbc)
    // Distributed cache: serialization is part of the API (entities are @Serializable);
    // Lettuce is opt-in — plugins that use the Redis backend add it themselves.
    api(libs.serialization.json)
    compileOnly(libs.lettuce)
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.h2)
    testImplementation(libs.coroutines.test)
}
