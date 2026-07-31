plugins {
    id("sculk.module")
}

description = "Sculk Studio — optional PlaceholderAPI, Vault and LuckPerms adapters"

dependencies {
    api(project(":sculk-common"))
    testImplementation(libs.mockito.kotlin)
}
