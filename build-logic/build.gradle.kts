plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kotlin.serialization)
    implementation(libs.gradle.ktlint)
    implementation(libs.gradle.dokka)
    implementation(libs.gradle.maven.publish)
    implementation(libs.archunit)
    implementation(libs.kotlin.metadata)
    implementation(libs.asm)
    implementation(libs.gradle.shadow)
    implementation(libs.gradle.run.task)
    implementation(libs.gradle.license.report)
    implementation(libs.gradle.kotlinx.benchmark)
    implementation(libs.gradle.kotlin.allopen)
}
