// Root build — version declaration only.
// All module configuration lives in build-logic convention plugins.

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
}

group = "studio.sculk"
version = "3.0.0"

subprojects {
    group = rootProject.group
    version = rootProject.version
}
