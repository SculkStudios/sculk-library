plugins {
    id("sculk.module")
    alias(libs.plugins.kotlin.serialization)
}

description = "Sculk Studio — data-component item builders, persistent data, skulls and descriptors"

dependencies {
    api(project(":sculk-common"))
    // Item names and lore are messages: they go through the same theme and the same unparsed
    // placeholder boundary as chat, rather than parsing MiniMessage on their own.
    api(project(":sculk-text"))
    // Material lookup by key belongs in one place; doing it here as well is what let item() and
    // ItemBuilder.material() disagree about how an unknown key fails.
    api(project(":sculk-series"))
    // ItemDescriptor's generated serializer is public API: a consumer embedding one in its own
    // @Serializable settings class resolves it at compile time, so this cannot be implementation.
    api(libs.serialization.core)
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.mini)

    testImplementation(libs.mockbukkit)
    // The descriptor is decoded by the same engine sculk-config uses, so the round-trip is
    // asserted against kaml rather than against kotlinx-serialization's JSON.
    testImplementation(libs.kaml)
}
