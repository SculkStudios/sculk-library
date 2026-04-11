package gg.sculk.series

import gg.sculk.core.annotation.SculkStable
import gg.sculk.series.registry.MappingResolver
import gg.sculk.series.registry.SculkRegistry
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.EntityType

/**
 * Entry point for all Sculk Studio cross-version registry lookups.
 *
 * All lookups are registry-based (never hardcoded) and cached after first access.
 * Keys are case-insensitive. Lookups return null for unknown keys — never throw.
 *
 * Example:
 * ```kotlin
 * val material = SculkSeries.material("diamond_sword") ?: Material.AIR
 * val sound    = SculkSeries.sound("entity.player.levelup")
 * val particle = SculkSeries.particle("flame")
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
                            ?: EntityType.values().firstOrNull {
                                it.name.equals(key, ignoreCase = true)
                            }

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

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Resolves a [Material] by [key]. Keys are case-insensitive.
     * Returns null for unknown keys.
     */
    @SculkStable
    public fun material(key: String): Material? = materialRegistry.resolve(key)

    /**
     * Resolves a [Sound] by [key].
     * Accepts both `ENTITY_PLAYER_LEVELUP` and `entity.player.levelup` forms.
     */
    @SculkStable
    public fun sound(key: String): Sound? = soundRegistry.resolve(key)

    /**
     * Resolves a [Particle] by [key].
     */
    @SculkStable
    public fun particle(key: String): Particle? = particleRegistry.resolve(key)

    /**
     * Resolves an [EntityType] by [key].
     */
    @SculkStable
    public fun entityType(key: String): EntityType? = entityTypeRegistry.resolve(key)

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
}
