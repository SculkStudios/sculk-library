plugins {
    id("sculk.module")
}

description = "Sculk Studio — shared base: result/handle types, coroutines, scheduler, tasks, version, annotations"

dependencies {
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.coroutines.test)
}
