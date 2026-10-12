plugins {
    id("sculk.example")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-config"))
    implementation(project(":sculk-commands-paper"))
    implementation(project(":sculk-placeholders"))
    testImplementation(project(":sculk-testkit"))
    testImplementation(libs.paper.api.floor)
}
