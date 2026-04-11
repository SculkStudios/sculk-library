plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — core framework: commands, GUI, Adventure wrapper, scheduler"

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
