plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — async database abstraction, caching layer, ORM support"

dependencies {
    api(project(":sculk-effects"))
    implementation(libs.hikari)
    implementation(libs.caffeine)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.h2)
}
