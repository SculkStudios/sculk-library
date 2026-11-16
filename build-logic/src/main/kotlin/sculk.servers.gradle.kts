import studio.sculk.gradle.CheckServerMatrix
import studio.sculk.gradle.ServerMatrix
import studio.sculk.gradle.UpdateServerMatrix

// The server pins in gradle/servers.json: checked on every build, moved by updateServerMatrix.

plugins {
    base
}

val checkServerMatrix = tasks.register<CheckServerMatrix>("checkServerMatrix") {
    group = "verification"
    description = "Checks that the version catalog and COMPATIBILITY.md agree with gradle/servers.json."
    servers = layout.projectDirectory.file(ServerMatrix.FILE)
    catalog = layout.projectDirectory.file("gradle/libs.versions.toml")
    compatibility = layout.projectDirectory.file("COMPATIBILITY.md")
    report = layout.buildDirectory.file("reports/server-matrix.txt")
}

tasks.check { dependsOn(checkServerMatrix) }

tasks.register<UpdateServerMatrix>("updateServerMatrix") {
    group = "sculk compatibility"
    description = "Moves gradle/servers.json and the version catalog to the newest stable server builds (--report-only changes nothing)."
    servers = layout.projectDirectory.file(ServerMatrix.FILE)
    catalog = layout.projectDirectory.file("gradle/libs.versions.toml")
    scratch = layout.buildDirectory.file("tmp/updateServerMatrix/canvas.jar")
}
