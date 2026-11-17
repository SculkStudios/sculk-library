plugins {
    id("sculk.library")
    id("sculk.publish")
}

description =
    "Sculk Studio scenes: holograms, labels and simple models over display entities on Paper, Folia and Canvas (ADR-0032)."

dependencies {
    api(project(":sculk-paper"))
    api(project(":sculk-placeholders"))
    api(project(":sculk-items"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
    testImplementation(project(":sculk-testkit"))
}

// The whole module is experimental (API_POLICY.md); its own code uses its own API without per-file opt-ins.
kotlin {
    compilerOptions {
        optIn.add("studio.sculk.annotation.SculkExperimental")
    }
}
