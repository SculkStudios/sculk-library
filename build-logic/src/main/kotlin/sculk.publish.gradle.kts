import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = SourcesJar.Sources()))
    publishToMavenCentral(automaticRelease = true)
    // Signing keys exist only in the release environment; local and CI snapshot publishing stay unsigned.
    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }
    pom {
        name = project.name
        description = provider { project.description ?: "Sculk Studio module ${project.name}" }
        url = "https://github.com/SculkStudios/sculk-library"
        inceptionYear = "2024"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "sculkstudios"
                name = "Sculk Studios"
                url = "https://sculk.studio"
            }
        }
        scm {
            url = "https://github.com/SculkStudios/sculk-library"
            connection = "scm:git:https://github.com/SculkStudios/sculk-library.git"
            developerConnection = "scm:git:ssh://git@github.com/SculkStudios/sculk-library.git"
        }
    }
}
