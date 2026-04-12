package gg.sculk.series

import gg.sculk.core.annotation.SculkStable
import gg.sculk.series.registry.MappingResolver
import gg.sculk.series.registry.SculkRegistry
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.block.Biome
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.EntityType
import org.bukkit.potion.PotionEffectType
import java.util.concurrent.ConcurrentHashMap

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
    private val materialRegistry: SculkRegistry<Material> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<Material> {
                    override fun resolve(key: String): Material? =
                        runCatching { Material.matchMaterial(key.uppercase()) }.getOrNull()
                            ?: runCatching { Material.matchMaterial(key) }.getOrNull()

                    override fun keys(): Set<String> = Material.values().map { it.name.lowercase() }.toSet()
                },
        )
    }

    private val soundRegistry: SculkRegistry<Sound> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<Sound> {
                    override fun resolve(key: String): Sound? =
                        runCatching { Sound.valueOf(key.uppercase()) }.getOrNull()
                            ?: Sound.values().firstOrNull {
                                it.name().equals(key, ignoreCase = true) ||
                                    it.name().lowercase().replace('_', '.') == key.lowercase()
                            }

                    override fun keys(): Set<String> = Sound.values().map { it.name().lowercase() }.toSet()
                },
        )
    }

    private val particleRegistry: SculkRegistry<Particle> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<Particle> {
                    override fun resolve(key: String): Particle? = runCatching { Particle.valueOf(key.uppercase()) }.getOrNull()

                    override fun keys(): Set<String> = Particle.values().map { it.name.lowercase() }.toSet()
                },
        )
    }

    private val entityTypeRegistry: SculkRegistry<EntityType> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<EntityType> {
                    override fun resolve(key: String): EntityType? =
                        runCatching { EntityType.valueOf(key.uppercase()) }.getOrNull()
                            ?: EntityType.values().firstOrNull { it.name.equals(key, ignoreCase = true) }

                    override fun keys(): Set<String> = EntityType.values().map { it.name.lowercase() }.toSet()
                },
        )
    }

    private val gameModeRegistry: SculkRegistry<GameMode> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<GameMode> {
                    override fun resolve(key: String): GameMode? = runCatching { GameMode.valueOf(key.uppercase()) }.getOrNull()

                    override fun keys(): Set<String> = GameMode.values().map { it.name.lowercase() }.toSet()
                },
        )
    }

    private val difficultyRegistry: SculkRegistry<Difficulty> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<Difficulty> {
                    override fun resolve(key: String): Difficulty? = runCatching { Difficulty.valueOf(key.uppercase()) }.getOrNull()

                    override fun keys(): Set<String> = Difficulty.values().map { it.name.lowercase() }.toSet()
                },
        )
    }

    private val enchantmentRegistry: SculkRegistry<Enchantment> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<Enchantment> {
                    override fun resolve(key: String): Enchantment? {
                        val nsKey = NamespacedKey.minecraft(key.lowercase())
                        return Registry.ENCHANTMENT.get(nsKey)
                            ?: runCatching { Enchantment.getByKey(nsKey) }.getOrNull()
                    }

                    override fun keys(): Set<String> = Registry.ENCHANTMENT.map { it.key.key }.toSet()
                },
        )
    }

    private val potionEffectRegistry: SculkRegistry<PotionEffectType> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<PotionEffectType> {
                    override fun resolve(key: String): PotionEffectType? {
                        val nsKey = NamespacedKey.minecraft(key.lowercase())
                        return Registry.EFFECT.get(nsKey)
                            ?: PotionEffectType.values().firstOrNull {
                                it.key.key.equals(key, ignoreCase = true)
                            }
                    }

                    override fun keys(): Set<String> = Registry.EFFECT.map { it.key.key }.toSet()
                },
        )
    }

    private val biomeRegistry: SculkRegistry<Biome> by lazy {
        SculkRegistry(
            resolver =
                object : MappingResolver<Biome> {
                    override fun resolve(key: String): Biome? = Registry.BIOME.get(NamespacedKey.minecraft(key.lowercase()))

                    override fun keys(): Set<String> = Registry.BIOME.map { it.key.key }.toSet()
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
    @SculkStable
    public fun <T : Any> register(
        type: Class<T>,
        resolver: (String) -> T?,
    ) {
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
    @SculkStable
    @Suppress("UNCHECKED_CAST")
    public fun <T : Any> lookup(
        type: Class<T>,
        key: String,
    ): T? = (customRegistries[type] as? SculkRegistry<T>)?.resolve(key)

    /** Kotlin convenience inline overload for [lookup]. */
    @SculkStable
    public inline fun <reified T : Any> lookup(key: String): T? = lookup(T::class.java, key)

    // ---------------------------------------------------------------------------
    // Public API — built-in registries
    // ---------------------------------------------------------------------------

    /** Resolves a [Material] by [key]. Keys are case-insensitive. Returns null for unknown keys. */
    @SculkStable
    public fun material(key: String): Material? = materialRegistry.resolve(key)

    /**
     * Resolves a [Sound] by [key].
     * Accepts both `ENTITY_PLAYER_LEVELUP` and `entity.player.levelup` forms.
     */
    @SculkStable
    public fun sound(key: String): Sound? = soundRegistry.resolve(key)

    /** Resolves a [Particle] by [key]. */
    @SculkStable
    public fun particle(key: String): Particle? = particleRegistry.resolve(key)

    /** Resolves an [EntityType] by [key]. */
    @SculkStable
    public fun entityType(key: String): EntityType? = entityTypeRegistry.resolve(key)

    /**
     * Resolves an [Enchantment] by [key].
     * Accepts keys like `sharpness`, `fire_aspect`, `protection`.
     */
    @SculkStable
    public fun enchantment(key: String): Enchantment? = enchantmentRegistry.resolve(key)

    /**
     * Resolves a [PotionEffectType] by [key].
     * Accepts keys like `speed`, `strength`, `night_vision`.
     */
    @SculkStable
    public fun potionEffect(key: String): PotionEffectType? = potionEffectRegistry.resolve(key)

    /**
     * Resolves a [Biome] by [key].
     * Accepts keys like `plains`, `desert`, `ocean`, `the_nether`.
     */
    @SculkStable
    public fun biome(key: String): Biome? = biomeRegistry.resolve(key)

    /**
     * Resolves a [GameMode] by [key].
     * Accepts `survival`, `creative`, `adventure`, `spectator`.
     */
    @SculkStable
    public fun gameMode(key: String): GameMode? = gameModeRegistry.resolve(key)

    /**
     * Resolves a [Difficulty] by [key].
     * Accepts `peaceful`, `easy`, `normal`, `hard`.
     */
    @SculkStable
    public fun difficulty(key: String): Difficulty? = difficultyRegistry.resolve(key)

    /** Returns all known [Material] key names. */
    @SculkStable
    public fun materialKeys(): Set<String> = materialRegistry.keys()

    /** Returns all known [Sound] key names. */
    @SculkStable
    public fun soundKeys(): Set<String> = soundRegistry.keys()

    /** Returns all known [Enchantment] key names. */
    @SculkStable
    public fun enchantmentKeys(): Set<String> = enchantmentRegistry.keys()

    /** Returns all known [PotionEffectType] key names. */
    @SculkStable
    public fun potionEffectKeys(): Set<String> = potionEffectRegistry.keys()
}
