plugins {
    id("sculk.library")
    id("sculk.publish")
}

description =
    "Sculk Studio permissions: descriptors with defaults and children, grouped per plugin, and context-aware checks on every platform."

dependencies {
    api(project(":sculk-core"))
    testImplementation(project(":sculk-testkit"))
}
