package studio.sculk.gradle

/**
 * Which packages a module may not reach, derived from its name. This is the executable form of the
 * rules table in MODULES.md; a module in neither the neutral set nor an owner list is a platform module.
 */
internal object ModuleRules {
    private val PLATFORM = listOf("org.bukkit..", "io.papermc..", "com.destroystokyo..", "com.velocitypowered..", "io.canvasmc..")
    private val INTERNALS = listOf("net.minecraft..", "com.mojang..", "org.bukkit.craftbukkit..")
    private val SQL_DRIVERS = listOf("org.sqlite..", "com.mysql..", "org.mariadb..", "org.postgresql..")
    private val REFLECTION = listOf("kotlin.reflect.full..", "kotlin.reflect.jvm..")

    private val NEUTRAL = setOf(
        "sculk-core", "sculk-coroutines", "sculk-platform-api", "sculk-testkit",
        "sculk-text", "sculk-placeholders", "sculk-config", "sculk-commands", "sculk-events", "sculk-permissions",
        "sculk-data", "sculk-data-sqlite", "sculk-data-mysql", "sculk-data-mariadb", "sculk-data-postgres",
        "sculk-cache", "sculk-cache-caffeine", "sculk-cache-valkey", "sculk-network",
        "sculk-discord", "sculk-discord-jda", "sculk-web", "sculk-web-ktor",
    )

    /** Third-party packages and the only modules allowed to reference them. */
    private val OWNERS: Map<List<String>, Set<String>> = mapOf(
        listOf("com.github.retrooper..", "io.github.retrooper..") to setOf("sculk-packets-packetevents"),
        listOf("com.comphenix..") to setOf("sculk-packets-protocollib"),
        listOf("net.dv8tion..") to setOf("sculk-discord-jda"),
        listOf("io.ktor..") to setOf("sculk-web-ktor", "sculk-resourcepack-ktor"),
    )

    fun forbiddenPackages(module: String): List<String> = buildList {
        if (module in NEUTRAL) addAll(PLATFORM)
        if (!module.startsWith("sculk-internals-")) addAll(INTERNALS)
        if (!module.startsWith("sculk-data-")) addAll(SQL_DRIVERS)
        OWNERS.forEach { (packages, owners) -> if (module !in owners) addAll(packages) }
        addAll(REFLECTION)
    }
}
