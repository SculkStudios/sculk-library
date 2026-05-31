plugins {
    id("sculk.paper-plugin")
}

description = "Sculk Studio — chest & container GUI menus with animations, pagination, and click routing"

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=studio.sculk.annotation.SculkInternal")
    }
}

dependencies {
    api(project(":sculk-common"))
    api(project(":sculk-adventure"))
    api(project(":sculk-items"))
    testImplementation(libs.paper.api)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.kotlin)
}
