plugins {
    id("sculk.library")
    id("sculk.publish")
    // A tag resolver is an Adventure interface Sculk implements, so the same bytecode is proven on Adventure 5 too.
    id("sculk.adventure-latest")
}

description =
    "Sculk Studio placeholders: typed global, audience and relational placeholders as MiniMessage tags, with refresh policies and change signals."

dependencies {
    api(project(":sculk-text"))
    api(project(":sculk-coroutines"))
    // The server provides Adventure; placeholders reach MiniMessage as tag resolvers.
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.minimessage)
    testImplementation(project(":sculk-testkit"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.adventure.api)
    testImplementation(libs.adventure.minimessage)
    testImplementation(libs.adventure.plain)
}
