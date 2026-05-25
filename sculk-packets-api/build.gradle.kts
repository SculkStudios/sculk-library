plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio - lightweight packet abstraction and high-level packet UX contracts"

dependencies {
    api(project(":sculk-core"))
    testImplementation(libs.junit.jupiter)
}
