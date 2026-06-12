// Applied on top of sculk.kotlin-library for modules that need Paper API.
// Paper is compileOnly — never bundled into published artifacts.
// Keep Paper version in sync with gradle/libs.versions.toml.

plugins {
    id("sculk.kotlin-library")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}
