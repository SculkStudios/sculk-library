plugins {
    id("sculk.module")
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
    compileOnly(libs.adventure.api)
    compileOnly(libs.adventure.mini)
}
