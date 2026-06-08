package studio.sculk.series

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.Keyed
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.block.Biome
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.potion.PotionEffectType
import studio.sculk.annotation.SculkStable
import studio.sculk.series.registry.MappingResolver
import studio.sculk.series.registry.SculkRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

/**
 * Entry point for all Sculk Studio cross-version registry lookups.
 *
 * All lookups are registry-based (never hardcoded) and cached after first access.
 * Keys are case-insensitive. Lookups return null for unknown keys — never throw.
 *
 * Example:
 * ```kotlin
 * val material   = SculkSeries.material("diamond_sword") ?: Material.AIR
 * val sound      = SculkSeries.sound("entity.player.levelup")
 * val particle   = SculkSeries.particle("flame")
 * val ench       = SculkSeries.enchantment("sharpness")
 * val effect     = SculkSeries.potionEffect("speed")
 * val biome      = SculkSeries.biome("plains")
 * ```
 */
@SculkStable
public object SculkSeries {
    private val paperRegistries: RegistryAccess by lazy { RegistryAccess.registryAccess() }

    private val materialRegistry: SculkRegistry<Material> by lazy {
        SculkRegistry(
            resolver =
            object : MappingResolver<Material> {
                override fun resolve(key: String): Material? = materialCandidates(key)
                    .firstNotNullOfOrNull {
                        runCatching { Material.matchMaterial(it.uppercase()) }.getOrNull()
                            ?: runCatching { Material.matchMaterial(it) }.getOrNull()
                    }

                override fun keys(): Set<String> = Material.values().map { it.name.lowercase() }.toSet()
            },
        )
    }

    private val soundRegistry: SculkRegistry<Sound> by lazy {
        val registry = registry(RegistryKey.SOUND_EVENT)
        SculkRegistry(
            resolver =
            object : MappingResolver<Sound> {
                override fun resolve(key: String): Sound? = normalizedLookupKeys(key)
                    .firstNotNullOfOrNull { registry.get(it) }

                override fun keys(): Set<String> = registry
                    .keyStream()
                    .map { it.key }
                    .collect(Collectors.toSet())
            },
        )
    }

    private val particleRegistry: SculkRegistry<Particle> by lazy {
        val registry = registry(RegistryKey.PARTICLE_TYPE)
        SculkRegistry(
            resolver =
            object : MappingResolver<Particle> {
                override fun resolve(key: String): Particle? = normalizedLookupKeys(key)
                    .firstNotNullOfOrNull { registry.get(it) }

                override fun keys(): Set<String> = registry
                    .keyStream()
                    .map { it.key }
                    .collect(Collectors.toSet())
            },
        )
    }

    private val entityTypeRegistry: SculkRegistry<EntityType> by lazy {
        val registry = registry(RegistryKey.ENTITY_TYPE)
        SculkRegistry(
            resolver =
            object : MappingResolver<EntityType> {
                override fun resolve(key: String): EntityType? = normalizedLookupKeys(key)
                    .firstNotNullOfOrNull { registry.get(it) }

                override fun keys(): Set<String> = registry
                    .keyStream()
                    .map { it.key }
                    .collect(Collectors.toSet())
            },
        )
    }

    private val gameModeRegistry: SculkRegistry<GameMode> by lazy {
        SculkRegistry(
            resolver =
            object : MappingResolver<GameMode> {
                override fun resolve(key: String): GameMode? =
                    GameMode.entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) }

                override fun keys(): Set<String> = GameMode.entries.map { it.name.lowercase() }.toSet()
            },
        )
    }

    private val difficultyRegistry: SculkRegistry<Difficulty> by lazy {
        SculkRegistry(
            resolver =
            object : MappingResolver<Difficulty> {
                override fun resolve(key: String): Difficulty? =
                    Difficulty.entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) }

                override fun keys(): Set<String> = Difficulty.entries.map { it.name.lowercase() }.toSet()
            },
        )
    }

    private val enchantmentRegistry: SculkRegistry<Enchantment> by lazy {
        val registry = registry(RegistryKey.ENCHANTMENT)
        SculkRegistry(
            resolver =
            object : MappingResolver<Enchantment> {
                override fun resolve(key: String): Enchantment? = normalizedLookupKeys(key)
                    .firstNotNullOfOrNull { registry.get(it) }

                override fun keys(): Set<String> = registry
                    .keyStream()
                    .map { it.key }
                    .collect(Collectors.toSet())
            },
        )
    }

    private val potionEffectRegistry: SculkRegistry<PotionEffectType> by lazy {
        val registry = registry(RegistryKey.MOB_EFFECT)
        SculkRegistry(
            resolver =
            object : MappingResolver<PotionEffectType> {
                override fun resolve(key: String): PotionEffectType? = normalizedLookupKeys(key)
                    .firstNotNullOfOrNull { registry.get(it) }

                override fun keys(): Set<String> = registry
                    .keyStream()
                    .map { it.key }
                    .collect(Collectors.toSet())
            },
        )
    }

    private val biomeRegistry: SculkRegistry<Biome> by lazy {
        val registry = registry(RegistryKey.BIOME)
        SculkRegistry(
            resolver =
            object : MappingResolver<Biome> {
                override fun resolve(key: String): Biome? = normalizedLookupKeys(key)
                    .firstNotNullOfOrNull { registry.get(it) }

                override fun keys(): Set<String> = registry
                    .keyStream()
                    .map { it.key }
                    .collect(Collectors.toSet())
            },
        )
    }

    // ---------------------------------------------------------------------------
    // Custom registries
    // ---------------------------------------------------------------------------

    private val customRegistries: ConcurrentHashMap<Class<*>, SculkRegistry<*>> = ConcurrentHashMap()

    /**
     * Registers a custom lookup registry for [type].
     *
     * ```kotlin
     * SculkSeries.register<MyType> { key ->
     *     MyType.values().firstOrNull { it.key.equals(key, ignoreCase = true) }
     * }
     * val value = SculkSeries.lookup<MyType>("my_key")
     * ```
     */
    @JvmStatic
    @SculkStable
    public fun <T : Any> register(type: Class<T>, resolver: (String) -> T?) {
        customRegistries[type] =
            SculkRegistry(
                object : MappingResolver<T> {
                    override fun resolve(key: String): T? = resolver(key)

                    override fun keys(): Set<String> = emptySet()
                },
            )
    }

    /** Kotlin convenience inline overload for [register]. */
    @SculkStable
    public inline fun <reified T : Any> register(noinline resolver: (String) -> T?): Unit = register(T::class.java, resolver)

    /**
     * Looks up a value from a previously [register]ed custom registry.
     *
     * Returns null if no registry is registered for [type] or the key is unknown.
     */
    @JvmStatic
    @SculkStable
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> lookup(type: Class<T>, key: String): T? = (customRegistries[type] as? SculkRegistry<T>)?.resolve(key)

    /** Kotlin convenience inline overload for [lookup]. */
    @SculkStable
    public inline fun <reified T : Any> lookup(key: String): T? = lookup(T::class.java, key)

    // ---------------------------------------------------------------------------
    // Public API — built-in registries
    // ---------------------------------------------------------------------------

    /** Resolves a [Material] by [key]. Keys are case-insensitive. Returns null for unknown keys. */
    @JvmStatic
    @SculkStable
    public fun material(key: String): Material? = materialRegistry.resolve(key)

    /** Resolves a [Material] by [key] or throws with a clear error. */
    @JvmStatic
    @SculkStable
    public fun requireMaterial(key: String): Material = material(key) ?: throw unknown("material", key)

    /**
     * Resolves a [Sound] by [key].
     * Accepts both `ENTITY_PLAYER_LEVELUP` and `entity.player.levelup` forms.
     */
    @JvmStatic
    @SculkStable
    public fun sound(key: String): Sound? = soundRegistry.resolve(key)

    /** Resolves a [Sound] by [key] or throws with a clear error. */
    @JvmStatic
    @SculkStable
    public fun requireSound(key: String): Sound = sound(key) ?: throw unknown("sound", key)

    /** Resolves a [Particle] by [key]. */
    @JvmStatic
    @SculkStable
    public fun particle(key: String): Particle? = particleRegistry.resolve(key)

    /** Resolves a [Particle] by [key] or throws with a clear error. */
    @JvmStatic
    @SculkStable
    public fun requireParticle(key: String): Particle = particle(key) ?: throw unknown("particle", key)

    /** Resolves an [EntityType] by [key]. */
    @JvmStatic
    @SculkStable
    public fun entityType(key: String): EntityType? = entityTypeRegistry.resolve(key)

    /** Resolves an [EntityType] by [key] or throws with a clear error. */
    @JvmStatic
    @SculkStable
    public fun requireEntityType(key: String): EntityType = entityType(key) ?: throw unknown("entity type", key)

    /**
     * Resolves an [Enchantment] by [key].
     * Accepts keys like `sharpness`, `fire_aspect`, `protection`.
     */
    @JvmStatic
    @SculkStable
    public fun enchantment(key: String): Enchantment? = enchantmentRegistry.resolve(key)

    /** Resolves an [Enchantment] by [key] or throws with a clear error. */
    @JvmStatic
    @SculkStable
    public fun requireEnchantment(key: String): Enchantment = enchantment(key) ?: throw unknown("enchantment", key)

    /**
     * Resolves a [PotionEffectType] by [key].
     * Accepts keys like `speed`, `strength`, `night_vision`.
     */
    @JvmStatic
    @SculkStable
    public fun potionEffect(key: String): PotionEffectType? = potionEffectRegistry.resolve(key)

    /** Resolves a [PotionEffectType] by [key] or throws with a clear error. */
    @JvmStatic
    @SculkStable
    public fun requirePotionEffect(key: String): PotionEffectType = potionEffect(key) ?: throw unknown("potion effect", key)

    /**
     * Resolves a [Biome] by [key].
     * Accepts keys like `plains`, `desert`, `ocean`, `the_nether`.
     */
    @JvmStatic
    @SculkStable
    public fun biome(key: String): Biome? = biomeRegistry.resolve(key)

    /** Resolves a [Biome] by [key] or throws with a clear error. */
    @JvmStatic
    @SculkStable
    public fun requireBiome(key: String): Biome = biome(key) ?: throw unknown("biome", key)

    /**
     * Resolves a [GameMode] by [key].
     * Accepts `survival`, `creative`, `adventure`, `spectator`.
     */
    @JvmStatic
    @SculkStable
    public fun gameMode(key: String): GameMode? = gameModeRegistry.resolve(key)

    /**
     * Resolves a [Difficulty] by [key].
     * Accepts `peaceful`, `easy`, `normal`, `hard`.
     */
    @JvmStatic
    @SculkStable
    public fun difficulty(key: String): Difficulty? = difficultyRegistry.resolve(key)

    /** Returns all known [Material] key names. */
    @JvmStatic
    @SculkStable
    public fun materialKeys(): Set<String> = materialRegistry.keys()

    /** Returns all known [Sound] key names. */
    @JvmStatic
    @SculkStable
    public fun soundKeys(): Set<String> = soundRegistry.keys()

    /** Returns all known [Enchantment] key names. */
    @JvmStatic
    @SculkStable
    public fun enchantmentKeys(): Set<String> = enchantmentRegistry.keys()

    /** Returns all known [PotionEffectType] key names. */
    @JvmStatic
    @SculkStable
    public fun potionEffectKeys(): Set<String> = potionEffectRegistry.keys()

    /**
     * Validates config-driven registry keys during startup.
     */
    @JvmStatic
    @JvmOverloads
    @SculkStable
    public fun validateKeys(
        materials: Iterable<String> = emptyList(),
        sounds: Iterable<String> = emptyList(),
        particles: Iterable<String> = emptyList(),
        entityTypes: Iterable<String> = emptyList(),
        enchantments: Iterable<String> = emptyList(),
        potionEffects: Iterable<String> = emptyList(),
        biomes: Iterable<String> = emptyList(),
    ): SeriesValidationReport {
        val missing = mutableListOf<SeriesMissingKey>()
        materials.filter { material(it) == null }.forEach { missing += SeriesMissingKey("material", it) }
        sounds.filter { sound(it) == null }.forEach { missing += SeriesMissingKey("sound", it) }
        particles.filter { particle(it) == null }.forEach { missing += SeriesMissingKey("particle", it) }
        entityTypes.filter { entityType(it) == null }.forEach { missing += SeriesMissingKey("entityType", it) }
        enchantments.filter { enchantment(it) == null }.forEach { missing += SeriesMissingKey("enchantment", it) }
        potionEffects.filter { potionEffect(it) == null }.forEach { missing += SeriesMissingKey("potionEffect", it) }
        biomes.filter { biome(it) == null }.forEach { missing += SeriesMissingKey("biome", it) }
        return SeriesValidationReport(missing)
    }

    private fun materialCandidates(input: String): List<String> {
        val key = normalizedText(input)
        return listOf(
            key,
            key.replace(' ', '_'),
            key.replace('-', '_'),
            key.replace('.', '_'),
            commonMaterialAliases[key],
        ).filterNotNull().filter { it.isNotBlank() }.distinct()
    }

    private fun minecraftKey(input: String): NamespacedKey {
        val trimmed = input.trim().lowercase()
        val key =
            if (trimmed.contains(':')) {
                trimmed.substringAfter(':')
            } else {
                trimmed
            }
        return NamespacedKey.minecraft(key)
    }

    private fun normalizedLookupKeys(input: String): List<NamespacedKey> {
        val trimmed = normalizedText(input)
        val unqualified = trimmed.substringAfter(':')
        val alias = commonRegistryAliases[unqualified]
        val candidates =
            listOf(
                trimmed,
                unqualified,
                unqualified.replace('.', '_'),
                unqualified.replace('_', '.'),
                unqualified.replace(' ', '_'),
                unqualified.replace('-', '_'),
                alias,
            )
        return candidates
            .filterNotNull()
            .filter { it.isNotBlank() }
            .distinct()
            .map(::minecraftKey)
    }

    private fun <T : Keyed> registry(key: RegistryKey<T>): Registry<T> = paperRegistries.getRegistry(key)

    private fun normalizedText(input: String): String = input.trim().lowercase().substringAfter("minecraft:")

    private fun unknown(type: String, key: String): IllegalArgumentException = IllegalArgumentException("Unknown $type key '$key'.")
}

/** A missing registry key discovered during validation. */
@SculkStable
public data class SeriesMissingKey(public val type: String, public val key: String)

/** Startup validation result for config-driven registry keys. */
@SculkStable
public data class SeriesValidationReport(public val missing: List<SeriesMissingKey>) {
    public val valid: Boolean get() = missing.isEmpty()
}

private val commonMaterialAliases: Map<String, String> =
    mapOf(
        "gold sword" to "golden_sword",
        "gold_sword" to "golden_sword",
        "gold helmet" to "golden_helmet",
        "gold_helmet" to "golden_helmet",
        "wood sword" to "wooden_sword",
        "wood_sword" to "wooden_sword",
        "grass" to "grass_block",
        "workbench" to "crafting_table",
        "log" to "oak_log",
        "leaves" to "oak_leaves",
    )

private val commonRegistryAliases: Map<String, String> =
    mapOf(
        "damage_all" to "sharpness",
        "arrow_fire" to "flame",
        "arrow_damage" to "power",
        "arrow_knockback" to "punch",
        "durability" to "unbreaking",
        "loot_bonus_blocks" to "fortune",
        "loot_bonus_mobs" to "looting",
        "entity.player.levelup" to "entity.player.level_up",
        "entity_player_levelup" to "entity.player.level_up",
    )
