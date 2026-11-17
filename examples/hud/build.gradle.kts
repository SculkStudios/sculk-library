plugins {
    id("sculk.example")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-config"))
    implementation(project(":sculk-commands-paper"))
    implementation(project(":sculk-presentation"))
    testImplementation(libs.paper.api.floor)
}
