plugins {
    kotlin("jvm")
    `maven-publish`
    id("org.jlleitschuh.gradle.ktlint")
}

kotlin {
    jvmToolchain(21)
    explicitApi()
}

java {
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<Test> {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

// Gradle 9 requires the JUnit Platform launcher on the test runtime classpath explicitly.
dependencies {
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
}

ktlint {
    version = "1.5.0"
    android = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                url = "https://github.com/SculkStudios/sculk-studio"
                licenses {
                    license {
                        name = "MIT License"
                        url = "https://opensource.org/licenses/MIT"
                    }
                }
                developers {
                    developer {
                        id = "sculkstudios"
                        name = "Sculk Studios"
                        url = "https://sculk.gg"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/SculkStudios/sculk-studio.git"
                    developerConnection = "scm:git:ssh://github.com/SculkStudios/sculk-studio.git"
                    url = "https://github.com/SculkStudios/sculk-studio"
                }
            }
        }
    }
}
