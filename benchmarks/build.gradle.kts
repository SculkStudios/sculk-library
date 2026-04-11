plugins {
    id("sculk.kotlin-library")
    alias(libs.plugins.jmh)
}

description = "Sculk Studio — JMH microbenchmarks"

dependencies {
    implementation(project(":sculk-platform"))
    jmh(libs.jmh.core)
    jmhAnnotationProcessor(libs.jmh.generator)
}

jmh {
    warmupIterations = 3
    iterations = 5
    fork = 1
    resultFormat = "JSON"
    resultsFile = project.file("results/benchmark-results.json")
}
