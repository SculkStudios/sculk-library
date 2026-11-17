plugins {
    id("sculk.paper-adapters")
    id("sculk.publish")
}

description =
    "Sculk Studio NPCs: player-shaped NPCs on Paper's Mannequin entity, from Minecraft 1.21.9, with no fake players or packets (ADR-0033)."

kotlin {
    compilerOptions {
        // The whole module is experimental (API_POLICY.md), so its own uses of its API need no opt-in at each site.
        optIn.add("studio.sculk.annotation.SculkExperimental")
    }
}

dependencies {
    api(project(":sculk-paper"))
    api(project(":sculk-items"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
    testImplementation(project(":sculk-testkit"))
}
