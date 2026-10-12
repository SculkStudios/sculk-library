plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio runtime contract, shared by the Paper and Velocity runtimes and by every feature module."

dependencies {
    api(project(":sculk-core"))
    api(project(":sculk-coroutines"))
}
