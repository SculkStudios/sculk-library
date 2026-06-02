plugins {
    id("sculk.paper-plugin")
}

description = "High-level client-side content helpers built on Sculk packet services"

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-packets-api"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
}
