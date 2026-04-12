plugins {
    id("sculk.kotlin-library")
    alias(libs.plugins.jmh)
}

description = "Sculk Studio — JMH microbenchmarks"

dependencies {
    implementation(project(":sculk-platform"))
    // Paper API must be explicit here — compileOnly deps are not transitive
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    jmh("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator)
    // Runtime deps for data-layer benchmarks (not transitive from sculk-data's implementation scope)
    jmh(libs.hikari)
    jmh(libs.caffeine)
    jmh(libs.h2)
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    resultFormat = "JSON"
    resultsFile = project.file("results/benchmark-results.json")
}
