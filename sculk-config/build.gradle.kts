plugins {
    id("sculk.library")
    id("sculk.serialization")
    id("sculk.publish")
}

description = "Sculk Studio configuration: typed YAML that keeps comments and unknown keys, explains errors and reloads transactionally."

dependencies {
    api(project(":sculk-core"))
    implementation(libs.snakeyaml.engine)
}

kotlin {
    compilerOptions {
        optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    }
}
