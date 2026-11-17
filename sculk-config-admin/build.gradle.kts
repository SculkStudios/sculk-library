plugins {
    id("sculk.library")
    id("sculk.publish")
}

description = "Sculk Studio config admin: an in-game editor for a typed configuration, as a dialog, or a menu where dialogs do not exist."

dependencies {
    api(project(":sculk-config"))
    api(project(":sculk-dialogs"))
    api(project(":sculk-inventory"))
    compileOnly(libs.paper.api.floor)
    testImplementation(libs.paper.api.floor)
}
