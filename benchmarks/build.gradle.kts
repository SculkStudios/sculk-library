plugins {
    id("sculk.benchmark")
    id("sculk.serialization")
}

description = "JMH baselines for Sculk's hot paths; never published."

dependencies {
    implementation(project(":sculk-text"))
    implementation(project(":sculk-config"))
    implementation(project(":sculk-commands"))
    implementation(project(":sculk-testkit"))
    implementation(libs.coroutines.core)
}
