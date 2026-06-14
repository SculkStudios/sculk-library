plugins {
    `java-platform`
    `maven-publish`
}

description = "Sculk Studio — Bill of Materials (BOM) for à-la-carte module version alignment"

dependencies {
    constraints {
        api(project(":sculk-common"))
        api(project(":sculk-adventure"))
        api(project(":sculk-commands"))
        api(project(":sculk-gui"))
        api(project(":sculk-events"))
        api(project(":sculk-config"))
        api(project(":sculk-series"))
        api(project(":sculk-items"))
        api(project(":sculk-effects"))
        api(project(":sculk-data"))
        api(project(":sculk-text"))
        api(project(":sculk-tasks"))
        api(project(":sculk-integrations"))
        api(project(":sculk-packets-api"))
        api(project(":sculk-packets-packetevents"))
        api(project(":sculk-packets-protocollib"))
        api(project(":sculk-holograms"))
        api(project(":sculk-content"))
        api(project(":sculk-platform"))
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
