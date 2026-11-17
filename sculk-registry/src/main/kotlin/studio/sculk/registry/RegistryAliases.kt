package studio.sculk.registry

import net.kyori.adventure.key.Key
import studio.sculk.annotation.SculkStable

/**
 * A registry entry that was renamed or removed within the supported Minecraft range: [from] became
 * [to], several keys when it was split, or nothing when it was removed. Lookups through [Registries]
 * follow these both ways, so a configuration written for one supported version keeps working on
 * the others.
 */
@SculkStable
public class RegistryAlias internal constructor(public val registry: Key, public val from: Key, to: List<Key>) {
    public val to: List<Key> = to.toList()

    override fun toString(): String = "${registry.asString()} ${from.asString()} -> ${to.ifEmpty { listOf("-") }.joinToString(" ")}"
}

/**
 * The alias table shipped in the jar, `aliases.txt` beside this class. The boot suite reads every
 * supported server's registries and fails when a key disappears between versions without a line
 * here, so the table is complete for the supported range by construction.
 */
internal object RegistryAliases {
    val all: List<RegistryAlias> by lazy {
        val text = checkNotNull(RegistryAliases::class.java.getResourceAsStream("aliases.txt")) { "aliases.txt is missing from the jar" }
            .bufferedReader().use { it.readText() }
        parse(text)
    }

    /**
     * Parses `<registry> <old> -> <new>[ <new>...]` lines, `-` for a removal; `#` starts a comment and
     * keys without a namespace are `minecraft:`. Throws [IllegalArgumentException] naming the line.
     */
    fun parse(text: String): List<RegistryAlias> = text.lineSequence().mapIndexedNotNull { index, raw ->
        val line = raw.substringBefore('#').trim()
        if (line.isEmpty()) return@mapIndexedNotNull null
        val parts = line.split(Regex("\\s+"))
        require(parts.size >= 4 && parts[2] == "->") {
            "aliases.txt line ${index + 1}: expected '<registry> <old> -> <new>...', found '$raw'"
        }
        val targets = parts.drop(3)
        RegistryAlias(key(parts[0]), key(parts[1]), if (targets == listOf("-")) emptyList() else targets.map(::key))
    }.toList()

    /** The keys [key] stands for on another supported version: its new names, or the old name it replaced. */
    fun candidates(registry: Key, key: Key, aliases: List<RegistryAlias> = all): List<Key> {
        val forward = aliases.filter { it.registry == registry && it.from == key }.flatMap { it.to }
        val backward = aliases.filter { it.registry == registry && key in it.to }.map { it.from }
        return (forward + backward).distinct()
    }

    fun removed(registry: Key, key: Key, aliases: List<RegistryAlias> = all): Boolean =
        aliases.any { it.registry == registry && it.from == key && it.to.isEmpty() }

    private fun key(text: String): Key = Key.key(if (':' in text) text else "minecraft:$text")
}
