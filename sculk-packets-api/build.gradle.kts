plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio - lightweight packet abstraction and high-level packet UX contracts"

dependencies {
    api(project(":sculk-common"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
