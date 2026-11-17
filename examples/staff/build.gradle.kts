plugins {
    id("sculk.example")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-config-admin"))
    implementation(project(":sculk-commands-paper"))
    testImplementation(libs.paper.api.floor)
}
