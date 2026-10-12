// JMH benchmarks through kotlinx-benchmark. Never published; run with ./gradlew :benchmarks:benchmark.

plugins {
    id("sculk.jvm")
    kotlin("plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
}

val libs = versionCatalogs.named("libs")

// JMH subclasses @State classes to generate its harness, and Kotlin classes are final by default.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

dependencies {
    "implementation"(libs.findLibrary("benchmark-runtime").get())
}

benchmark {
    targets {
        register("main")
    }
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            outputTimeUnit = "us"
            mode = "avgt"
        }
    }
}

// Compiling the JMH harness on every build keeps the suite from rotting between runs.
tasks.named("check") {
    dependsOn("assembleBenchmarks")
}
