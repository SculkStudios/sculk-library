plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — typed config, hot reload, message system"

dependencies {
    api(project(":sculk-core"))
    implementation(libs.snakeyaml)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
