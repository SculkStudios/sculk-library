import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.SourcesJar

plugins {
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
    id("com.github.jk1.dependency-license-report")
}

// What a published artifact puts on a consumer's runtime classpath must carry an allowed licence;
// compileOnly platform APIs never reach consumers through Sculk and are not checked here.
licenseReport {
    configurations = arrayOf("runtimeClasspath")
    allowedLicensesFile = rootProject.file("gradle/allowed-licenses.json")
    filters = arrayOf(LicenseBundleNormalizer())
}

tasks.named("check") {
    dependsOn("checkLicense")
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"), sourcesJar = SourcesJar.Sources()))
    publishToMavenCentral(automaticRelease = true)
    // Signing keys exist only in the release environment, so snapshots publish unsigned. A release
    // without a key fails here rather than uploading files Central would reject.
    val hasSigningKey = providers.gradleProperty("signingInMemoryKey").isPresent
    if (hasSigningKey) {
        signAllPublications()
    } else if (!project.version.toString().endsWith("-SNAPSHOT")) {
        tasks.matching { "MavenCentral" in it.name }.configureEach {
            doFirst { throw GradleException("Release ${project.version} must be signed; set signingInMemoryKey.") }
        }
    }
    pom {
        name = project.name
        description = provider { project.description ?: "Sculk Studio module ${project.name}" }
        url = "https://github.com/SculkStudios/sculk-library"
        inceptionYear = "2026"
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
                url = "https://github.com/SculkStudios"
            }
        }
        scm {
            url = "https://github.com/SculkStudios/sculk-library"
            connection = "scm:git:https://github.com/SculkStudios/sculk-library.git"
            developerConnection = "scm:git:ssh://git@github.com/SculkStudios/sculk-library.git"
        }
    }
}
