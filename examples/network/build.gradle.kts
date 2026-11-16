plugins {
    id("sculk.proxy-example")
}

dependencies {
    compileOnly(libs.velocity.api)
    implementation(project(":sculk-velocity"))
    implementation(project(":sculk-config"))
    implementation(project(":sculk-commands-velocity"))
    testImplementation(project(":sculk-testkit"))
}
