plugins {
    id("sculk.example")
    application
}

description = "Sculk Studio — standalone Discord bot example"

dependencies {
    implementation(project(":sculk-discord"))
    implementation(project(":sculk-discord-jda"))

    // A standalone bot brings JDA itself. The adapter declares it compileOnly precisely so the
    // decision of how it arrives — here a normal runtime dependency, in a Paper plugin the
    // library loader — belongs to whoever ships the thing.
    runtimeOnly(libs.jda)

    testImplementation(testFixtures(project(":sculk-discord")))
    testImplementation(libs.coroutines.test)
}

application {
    mainClass = "studio.sculk.example.bot.MainKt"
}

// `DISCORD_TOKEN=… ./gradlew :examples:discord-bot:run`
//
// Passed through rather than baked in: a token in a build file is a token in git history, and it
// cannot be rotated out of one.
tasks.named<JavaExec>("run") {
    environment("DISCORD_TOKEN", System.getenv("DISCORD_TOKEN") ?: "")
    environment("DISCORD_GUILD", System.getenv("DISCORD_GUILD") ?: "")
    standardInput = System.`in`
}
