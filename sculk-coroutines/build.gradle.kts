plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio execution ownership: contexts, dispatchers and tasks for Paper, Folia, Canvas and Velocity."

dependencies {
    api(project(":sculk-core"))
    api(libs.coroutines.core)
    testImplementation(libs.coroutines.test)
}
