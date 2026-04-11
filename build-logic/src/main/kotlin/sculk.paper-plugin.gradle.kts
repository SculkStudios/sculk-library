// Applied on top of sculk.kotlin-library for modules that need Paper API.
// Paper is compileOnly — never bundled into published artifacts.

plugins {
    id("sculk.kotlin-library")
}

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    compileOnly(libs.paper.api)
}
