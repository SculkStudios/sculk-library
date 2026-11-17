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

// Dokka reads a module's summary from a Markdown file whose first heading names the module, so the
// Gradle description is written into one rather than kept twice.
val dokkaModulePage = tasks.register("writeDokkaModulePage") {
    val page = layout.buildDirectory.file("dokka-includes/module.md")
    val module = project.name
    val summary = provider { project.description.orEmpty() }
    inputs.property("summary", summary)
    outputs.file(page)
    doLast { page.get().asFile.writeText("# Module $module\n\n${summary.get()}\n") }
}

dokka {
    moduleName = project.name
    dokkaSourceSets.configureEach {
        includes.from(dokkaModulePage)
        sourceLink {
            localDirectory = layout.projectDirectory.dir("src/main/kotlin")
            remoteUrl("https://github.com/SculkStudios/sculk-library/tree/dev/${project.name}/src/main/kotlin")
            remoteLineSuffix = "#L"
        }
    }
    pluginsConfiguration.html {
        customStyleSheets.from(isolated.rootProject.projectDirectory.file("docs/dokka/sculk.css"))
        homepageLink = "https://docs.sculk.studio/"
        footerMessage = "Sculk Studio is MIT licensed. Not an official Minecraft product."
    }
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
