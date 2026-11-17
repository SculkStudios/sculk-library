@file:OptIn(SculkExperimental::class)

package studio.sculk.world

import net.kyori.adventure.key.Key
import studio.sculk.annotation.SculkExperimental

/** How a rule's value differs between its registry form and the Paper 1.21.4 rule it maps to. */
internal enum class Conversion {
    NONE,
    INVERTED,
    FIRE_RADIUS,
    ;

    /** The Java type of the platform rule that holds a [type] value. */
    fun platformType(type: GameRuleType<*>): Class<*> = if (this == FIRE_RADIUS) Boolean::class.javaObjectType else type.javaType

    /** [value] as the platform rule stores it, or null when the platform rule cannot represent it. */
    fun toPlatform(value: Any): Any? = when (this) {
        NONE -> value

        INVERTED -> !(value as Boolean)

        // 1.21.4 has only doFireTick: fire spreads everywhere loaded or nowhere, never within a radius.
        FIRE_RADIUS -> when (value as Int) {
            0 -> false
            -1 -> true
            else -> null
        }
    }

    /** The platform rule's [value] as its registry form reads it. */
    fun fromPlatform(value: Any): Any = when (this) {
        NONE -> value
        INVERTED -> !(value as Boolean)
        FIRE_RADIUS -> if (value as Boolean) -1 else 0
    }
}

/** One line of `gamerules.txt`: a registry key, its 1.21.4 name, either missing when the other side has no such rule. */
internal class GameRuleRow(val key: Key?, val legacyName: String?, val conversion: Conversion) {
    override fun toString(): String = "${key?.asString() ?: "-"} ${legacyName ?: "-"} ${conversion.name.lowercase()}"
}

/** The name table shipped in the jar, `gamerules.txt` beside this class. */
internal object GameRuleNames {
    val rows: List<GameRuleRow> by lazy {
        val text = checkNotNull(GameRuleNames::class.java.getResourceAsStream("gamerules.txt")) { "gamerules.txt is missing from the jar" }
            .bufferedReader().use { it.readText() }
        parse(text)
    }

    /**
     * Parses `<key> <1.21.4 name> [inverted|fire-radius]` lines, `-` for a missing side; `#` starts a
     * comment. Throws [IllegalArgumentException] naming the line.
     */
    fun parse(text: String): List<GameRuleRow> = text.lineSequence().mapIndexedNotNull { index, raw ->
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return@mapIndexedNotNull null
        val parts = line.split(Regex("\\s+"))
        require(parts.size in 2..3 && (parts[0] != "-" || parts[1] != "-")) {
            "gamerules.txt line ${index + 1}: expected '<key> <1.21.4 name> [inverted|fire-radius]', found '$raw'"
        }
        val conversion = when (parts.getOrNull(2)) {
            null -> Conversion.NONE
            "inverted" -> Conversion.INVERTED
            "fire-radius" -> Conversion.FIRE_RADIUS
            else -> throw IllegalArgumentException("gamerules.txt line ${index + 1}: unknown conversion '${parts[2]}'")
        }
        val key = parts[0].takeUnless { it == "-" }?.let { Key.key(if (':' in it) it else "minecraft:$it") }
        GameRuleRow(key, parts[1].takeUnless { it == "-" }, conversion)
    }.toList()

    fun forKey(key: Key, rows: List<GameRuleRow> = this.rows): GameRuleRow? = rows.firstOrNull { it.key == key }

    fun forLegacyName(name: String, rows: List<GameRuleRow> = this.rows): GameRuleRow? = rows.firstOrNull { it.legacyName == name }
}

/** The name and value conversion of a rule on this server's platform. */
internal class PlatformRuleName(val name: String, val conversion: Conversion)

/**
 * Names rules the way this server does: by registry key from 1.21.11 ([registryBacked]), by the
 * 1.21.4 names of [rows] before that.
 */
internal class RuleNaming(val registryBacked: Boolean, private val rows: List<GameRuleRow> = GameRuleNames.rows) {
    /**
     * The platform name of [rule], or null when this server's version has no such vanilla rule. A key
     * the table does not know is tried under its own value, which the server then accepts or not.
     */
    fun platformName(rule: GameRuleKey<*>): PlatformRuleName? {
        if (registryBacked) return PlatformRuleName(rule.key.asString(), Conversion.NONE)
        val row = GameRuleNames.forKey(rule.key, rows) ?: return PlatformRuleName(rule.key.value(), Conversion.NONE)
        return row.legacyName?.let { PlatformRuleName(it, row.conversion) }
    }

    /**
     * The rule the platform calls [name], whose values have [platformType]; null for a 1.21.4 rule
     * with no successor or a type Sculk cannot read.
     */
    fun ruleFor(name: String, platformType: Class<*>): GameRuleKey<*>? {
        if (!registryBacked) {
            val key = GameRuleNames.forLegacyName(name, rows)?.key ?: return null
            return GameRuleKey.byKey(key)
        }
        val key = keyOrNull(name) ?: return null
        GameRuleKey.byKey(key)?.let { return it }
        return when (platformType) {
            Boolean::class.javaObjectType -> GameRuleKey.boolean(key)
            Int::class.javaObjectType -> GameRuleKey.integer(key)
            else -> null
        }
    }
}
