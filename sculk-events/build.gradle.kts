plugins {
    id("sculk.library")
    id("sculk.publish")
}

description =
    "Sculk Studio events: immediate, observing and awaitable handlers with one contract on every platform, and a bus for events plugins define."

dependencies {
    api(project(":sculk-coroutines"))
    testImplementation(project(":sculk-testkit"))
    testImplementation(libs.coroutines.test)
}
