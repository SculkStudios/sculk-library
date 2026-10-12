// Modules whose public types are @Serializable. Applied beside sculk.library.

plugins {
    kotlin("plugin.serialization")
}

val libs = versionCatalogs.named("libs")

dependencies {
    "api"(libs.findLibrary("serialization-core").get())
}
