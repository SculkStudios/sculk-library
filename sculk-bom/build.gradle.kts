plugins {
    `java-platform`
    `maven-publish`
}

description = "Sculk Studio — version alignment for picking modules à la carte"

dependencies {
    constraints {
        // Generated from the modules that actually apply sculk.module, so the BOM cannot drift
        // from the build the way a hand-maintained list does — the previous one still listed
        // sculk-content months after it was relevant.
        rootProject.subprojects
            .filter { it.name != "sculk-bom" && it.name.startsWith("sculk-") }
            .sortedBy { it.name }
            .forEach { api(project(it.path)) }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            pom {
                name = "Sculk Studio BOM"
                description = "Version alignment for picking individual Sculk modules à la carte."
                url = "https://github.com/SculkStudios/sculk-library"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
            }
        }
    }
}
