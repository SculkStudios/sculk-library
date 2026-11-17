plugins {
    id("sculk.example")
}

dependencies {
    compileOnly(libs.paper.api.floor)
    implementation(project(":sculk-paper"))
    implementation(project(":sculk-config"))
    implementation(project(":sculk-dialogs"))
    implementation(project(":sculk-inventory"))
    implementation(project(":sculk-commands-paper"))
    testImplementation(libs.paper.api.floor)
}
