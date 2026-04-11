plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — Paper platform integration: command registration, event DSL, GUI lifecycle"

dependencies {
    api(project(":sculk-data"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
