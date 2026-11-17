// The API reference on the docs site: every published module in one Dokka site, copied into the
// Astro site by syncApiReference before it builds.
plugins {
    id("org.jetbrains.dokka")
}

dependencies {
    subprojects.filter { it.name.startsWith("sculk-") }.forEach { "dokka"(project(it.path)) }
}

dokka {
    moduleName = "Sculk Studio"
    pluginsConfiguration.html {
        customStyleSheets.from(layout.projectDirectory.file("docs/dokka/sculk.css"))
        homepageLink = "https://docs.sculk.studio/"
        footerMessage = "Sculk Studio is MIT licensed. Not an official Minecraft product."
    }
}

tasks.register<Sync>("syncApiReference") {
    group = "documentation"
    description = "Generates the API reference and copies it into the docs site at docs/public/api."
    from(tasks.named("dokkaGeneratePublicationHtml"))
    into(layout.projectDirectory.dir("docs/public/api"))
}
