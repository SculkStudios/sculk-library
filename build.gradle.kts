// Root build — version and the public-API gate. Module configuration lives in build-logic.

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.binary.compat)
}

group = "studio.sculk"
version = "5.0.0"

subprojects {
    group = rootProject.group
    version = rootProject.version
}

// The committed .api files make the public surface reviewable in a diff. Adding a method becomes a
// visible line in a pull request rather than something noticed after it shipped, which is the only
// way a stability marker means anything.
apiValidation {
    ignoredProjects += listOf("sculk-bom")
    nonPublicMarkers += "studio.sculk.annotation.SculkInternal"
}
