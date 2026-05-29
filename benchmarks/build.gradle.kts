plugins {
    id("sculk.kotlin-library")
    alias(libs.plugins.jmh)
}

description = "Sculk Studio — JMH microbenchmarks"

dependencies {
    implementation(project(":sculk-platform"))
    // Paper API must be explicit here — compileOnly deps are not transitive
    compileOnly(libs.paper.api)
    jmh(libs.paper.api)
    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator)
    // Runtime deps for data-layer benchmarks (not transitive from sculk-data's implementation scope)
    jmh(libs.hikari)
    jmh(libs.caffeine)
    jmh(libs.h2)
    // Used only in benchmark @Setup to build a stub command source.
    jmh(libs.mockito.kotlin)
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    resultFormat = "JSON"
    resultsFile = project.file("results/benchmark-results.json")
}
