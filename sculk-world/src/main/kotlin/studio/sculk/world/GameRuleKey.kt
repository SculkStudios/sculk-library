package studio.sculk.world

import net.kyori.adventure.key.Key
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental

/** The value type of a game rule: [BOOLEAN] or [INTEGER], the only two Minecraft has. */
@SculkExperimental
public class GameRuleType<T : Any> private constructor(
    public val name: String,
    internal val javaType: Class<T>,
    private val reader: (String) -> T?,
) {
    /**
     * [text] as `/gamerule` reads it (`true`, `false`, or a whole number), or
     * [SculkFailure.InvalidInput] naming this type. Whether the server accepts the value is checked
     * only when it is set.
     */
    public fun parse(text: String): SculkResult<T> = reader(text.trim())?.let { SculkResult.success(it) }
        ?: SculkFailure.InvalidInput("game rule value", "a $name", "'$text'")

    override fun toString(): String = name

    @SculkExperimental
    public companion object {
        public val BOOLEAN: GameRuleType<Boolean> = GameRuleType("boolean", Boolean::class.javaObjectType) { it.toBooleanStrictOrNull() }
        public val INTEGER: GameRuleType<Int> = GameRuleType("integer", Int::class.javaObjectType) { it.toIntOrNull() }
    }
}

/**
 * A game rule named by its registry key, the name it has from Minecraft 1.21.11 on, with the type of
 * its value. The same key works on every supported version: on 1.21.4, where rules had other names
 * (`doDaylightCycle` for [ADVANCE_TIME]), [WorldRules] translates it, inverted rules and the fire
 * radius included.
 *
 * Two keys are equal when their key and type are. Immutable and safe to share.
 */
@SculkExperimental
public class GameRuleKey<T : Any> private constructor(public val key: Key, public val type: GameRuleType<T>) {
    override fun equals(other: Any?): Boolean = other is GameRuleKey<*> && other.key == key && other.type === type

    override fun hashCode(): Int = 31 * key.hashCode() + type.hashCode()

    override fun toString(): String = key.asString()

    @SculkExperimental
    public companion object {
        private val known = ArrayList<GameRuleKey<*>>()

        public val ADVANCE_TIME: GameRuleKey<Boolean> = register("advance_time", GameRuleType.BOOLEAN)
        public val ADVANCE_WEATHER: GameRuleKey<Boolean> = register("advance_weather", GameRuleType.BOOLEAN)
        public val ALLOW_ENTERING_NETHER_USING_PORTALS: GameRuleKey<Boolean> =
            register("allow_entering_nether_using_portals", GameRuleType.BOOLEAN)
        public val BLOCK_DROPS: GameRuleKey<Boolean> = register("block_drops", GameRuleType.BOOLEAN)
        public val BLOCK_EXPLOSION_DROP_DECAY: GameRuleKey<Boolean> = register("block_explosion_drop_decay", GameRuleType.BOOLEAN)
        public val COMMAND_BLOCK_OUTPUT: GameRuleKey<Boolean> = register("command_block_output", GameRuleType.BOOLEAN)
        public val COMMAND_BLOCKS_WORK: GameRuleKey<Boolean> = register("command_blocks_work", GameRuleType.BOOLEAN)
        public val DROWNING_DAMAGE: GameRuleKey<Boolean> = register("drowning_damage", GameRuleType.BOOLEAN)
        public val ELYTRA_MOVEMENT_CHECK: GameRuleKey<Boolean> = register("elytra_movement_check", GameRuleType.BOOLEAN)
        public val ENDER_PEARLS_VANISH_ON_DEATH: GameRuleKey<Boolean> = register("ender_pearls_vanish_on_death", GameRuleType.BOOLEAN)
        public val ENTITY_DROPS: GameRuleKey<Boolean> = register("entity_drops", GameRuleType.BOOLEAN)
        public val FALL_DAMAGE: GameRuleKey<Boolean> = register("fall_damage", GameRuleType.BOOLEAN)
        public val FIRE_DAMAGE: GameRuleKey<Boolean> = register("fire_damage", GameRuleType.BOOLEAN)

        /** Blocks around a player within which fire spreads: 0 stops it, -1 lets it spread everywhere. */
        public val FIRE_SPREAD_RADIUS_AROUND_PLAYER: GameRuleKey<Int> = register("fire_spread_radius_around_player", GameRuleType.INTEGER)
        public val FORGIVE_DEAD_PLAYERS: GameRuleKey<Boolean> = register("forgive_dead_players", GameRuleType.BOOLEAN)
        public val FREEZE_DAMAGE: GameRuleKey<Boolean> = register("freeze_damage", GameRuleType.BOOLEAN)
        public val GLOBAL_SOUND_EVENTS: GameRuleKey<Boolean> = register("global_sound_events", GameRuleType.BOOLEAN)
        public val IMMEDIATE_RESPAWN: GameRuleKey<Boolean> = register("immediate_respawn", GameRuleType.BOOLEAN)
        public val KEEP_INVENTORY: GameRuleKey<Boolean> = register("keep_inventory", GameRuleType.BOOLEAN)
        public val LAVA_SOURCE_CONVERSION: GameRuleKey<Boolean> = register("lava_source_conversion", GameRuleType.BOOLEAN)
        public val LIMITED_CRAFTING: GameRuleKey<Boolean> = register("limited_crafting", GameRuleType.BOOLEAN)
        public val LOCATOR_BAR: GameRuleKey<Boolean> = register("locator_bar", GameRuleType.BOOLEAN)
        public val LOG_ADMIN_COMMANDS: GameRuleKey<Boolean> = register("log_admin_commands", GameRuleType.BOOLEAN)
        public val MAX_BLOCK_MODIFICATIONS: GameRuleKey<Int> = register("max_block_modifications", GameRuleType.INTEGER)
        public val MAX_COMMAND_FORKS: GameRuleKey<Int> = register("max_command_forks", GameRuleType.INTEGER)
        public val MAX_COMMAND_SEQUENCE_LENGTH: GameRuleKey<Int> = register("max_command_sequence_length", GameRuleType.INTEGER)
        public val MAX_ENTITY_CRAMMING: GameRuleKey<Int> = register("max_entity_cramming", GameRuleType.INTEGER)
        public val MAX_MINECART_SPEED: GameRuleKey<Int> = register("max_minecart_speed", GameRuleType.INTEGER)
        public val MAX_SNOW_ACCUMULATION_HEIGHT: GameRuleKey<Int> = register("max_snow_accumulation_height", GameRuleType.INTEGER)
        public val MOB_DROPS: GameRuleKey<Boolean> = register("mob_drops", GameRuleType.BOOLEAN)
        public val MOB_EXPLOSION_DROP_DECAY: GameRuleKey<Boolean> = register("mob_explosion_drop_decay", GameRuleType.BOOLEAN)
        public val MOB_GRIEFING: GameRuleKey<Boolean> = register("mob_griefing", GameRuleType.BOOLEAN)
        public val NATURAL_HEALTH_REGENERATION: GameRuleKey<Boolean> = register("natural_health_regeneration", GameRuleType.BOOLEAN)
        public val PLAYER_MOVEMENT_CHECK: GameRuleKey<Boolean> = register("player_movement_check", GameRuleType.BOOLEAN)
        public val PLAYERS_NETHER_PORTAL_CREATIVE_DELAY: GameRuleKey<Int> =
            register("players_nether_portal_creative_delay", GameRuleType.INTEGER)
        public val PLAYERS_NETHER_PORTAL_DEFAULT_DELAY: GameRuleKey<Int> =
            register("players_nether_portal_default_delay", GameRuleType.INTEGER)
        public val PLAYERS_SLEEPING_PERCENTAGE: GameRuleKey<Int> = register("players_sleeping_percentage", GameRuleType.INTEGER)
        public val PROJECTILES_CAN_BREAK_BLOCKS: GameRuleKey<Boolean> = register("projectiles_can_break_blocks", GameRuleType.BOOLEAN)
        public val PVP: GameRuleKey<Boolean> = register("pvp", GameRuleType.BOOLEAN)
        public val RAIDS: GameRuleKey<Boolean> = register("raids", GameRuleType.BOOLEAN)
        public val RANDOM_TICK_SPEED: GameRuleKey<Int> = register("random_tick_speed", GameRuleType.INTEGER)
        public val REDUCED_DEBUG_INFO: GameRuleKey<Boolean> = register("reduced_debug_info", GameRuleType.BOOLEAN)
        public val RESPAWN_RADIUS: GameRuleKey<Int> = register("respawn_radius", GameRuleType.INTEGER)
        public val SEND_COMMAND_FEEDBACK: GameRuleKey<Boolean> = register("send_command_feedback", GameRuleType.BOOLEAN)
        public val SHOW_ADVANCEMENT_MESSAGES: GameRuleKey<Boolean> = register("show_advancement_messages", GameRuleType.BOOLEAN)
        public val SHOW_DEATH_MESSAGES: GameRuleKey<Boolean> = register("show_death_messages", GameRuleType.BOOLEAN)
        public val SPAWN_MOBS: GameRuleKey<Boolean> = register("spawn_mobs", GameRuleType.BOOLEAN)
        public val SPAWN_MONSTERS: GameRuleKey<Boolean> = register("spawn_monsters", GameRuleType.BOOLEAN)
        public val SPAWN_PATROLS: GameRuleKey<Boolean> = register("spawn_patrols", GameRuleType.BOOLEAN)
        public val SPAWN_PHANTOMS: GameRuleKey<Boolean> = register("spawn_phantoms", GameRuleType.BOOLEAN)
        public val SPAWN_WANDERING_TRADERS: GameRuleKey<Boolean> = register("spawn_wandering_traders", GameRuleType.BOOLEAN)
        public val SPAWN_WARDENS: GameRuleKey<Boolean> = register("spawn_wardens", GameRuleType.BOOLEAN)
        public val SPAWNER_BLOCKS_WORK: GameRuleKey<Boolean> = register("spawner_blocks_work", GameRuleType.BOOLEAN)
        public val SPECTATORS_GENERATE_CHUNKS: GameRuleKey<Boolean> = register("spectators_generate_chunks", GameRuleType.BOOLEAN)
        public val SPREAD_VINES: GameRuleKey<Boolean> = register("spread_vines", GameRuleType.BOOLEAN)
        public val TNT_EXPLODES: GameRuleKey<Boolean> = register("tnt_explodes", GameRuleType.BOOLEAN)
        public val TNT_EXPLOSION_DROP_DECAY: GameRuleKey<Boolean> = register("tnt_explosion_drop_decay", GameRuleType.BOOLEAN)
        public val UNIVERSAL_ANGER: GameRuleKey<Boolean> = register("universal_anger", GameRuleType.BOOLEAN)
        public val WATER_SOURCE_CONVERSION: GameRuleKey<Boolean> = register("water_source_conversion", GameRuleType.BOOLEAN)

        /**
         * Every rule Minecraft 26.3 has, in key order. Some are missing on older servers: reading or
         * setting one there returns [SculkFailure.UnsupportedCapability].
         */
        public val vanilla: List<GameRuleKey<*>> = known.toList()

        /** A boolean rule this list does not name, such as one added by a newer Minecraft; checked against the server when used. */
        public fun boolean(key: Key): GameRuleKey<Boolean> = GameRuleKey(key, GameRuleType.BOOLEAN)

        /** An integer rule this list does not name; checked against the server when used. */
        public fun integer(key: Key): GameRuleKey<Int> = GameRuleKey(key, GameRuleType.INTEGER)

        /**
         * The vanilla rule [text] names, as operators write it in configuration: its key with or
         * without the `minecraft:` namespace (`keep_inventory`), or its Paper 1.21.4 name
         * (`keepInventory`), on any server. Anything else is [SculkFailure.InvalidInput], with a
         * hint for a 1.21.4 rule that no longer exists. Needs no server; [WorldRules.resolve] also
         * finds rules newer than this list.
         */
        public fun parse(text: String): SculkResult<GameRuleKey<*>> {
            val name = text.trim()
            GameRuleNames.forLegacyName(name)?.let { row ->
                val key = row.key ?: return SculkFailure.InvalidInput(
                    "game rule",
                    "a game rule",
                    "'$name'",
                    hint = "$name existed only up to Minecraft 1.21.4 and has no successor.",
                )
                return byKey(key)?.let { SculkResult.success(it) } ?: unknown(name)
            }
            val key = keyOrNull(name) ?: return unknown(name)
            return byKey(key)?.let { SculkResult.success(it) } ?: unknown(name)
        }

        internal fun byKey(key: Key): GameRuleKey<*>? = vanilla.firstOrNull { it.key == key }

        private fun unknown(name: String): SculkFailure =
            SculkFailure.InvalidInput("game rule", "a game rule such as keep_inventory or keepInventory", "'$name'")

        private fun <T : Any> register(name: String, type: GameRuleType<T>): GameRuleKey<T> =
            GameRuleKey(Key.key(Key.MINECRAFT_NAMESPACE, name), type).also { known += it }
    }
}
