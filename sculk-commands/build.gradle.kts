plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio commands: immutable command specs, a typed DSL, middleware, help and a server-free dispatcher."

dependencies {
    api(project(":sculk-coroutines"))
    api(project(":sculk-text"))
    // The server provides Adventure; senders expose it as their audience.
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    testImplementation(project(":sculk-testkit"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
}
