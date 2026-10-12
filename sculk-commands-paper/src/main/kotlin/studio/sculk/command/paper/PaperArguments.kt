package studio.sculk.command.paper

import com.destroystokyo.paper.profile.PlayerProfile
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.math.BlockPosition
import io.papermc.paper.math.FinePosition
import io.papermc.paper.registry.RegistryKey
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.block.BlockState
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.command.ArgumentHandle
import studio.sculk.command.ArgumentShape
import studio.sculk.command.ArgumentType
import studio.sculk.command.CommandBuilder
import studio.sculk.command.CommandSource
import com.mojang.brigadier.arguments.ArgumentType as BrigadierType

/**
 * An argument read by one of Paper's native argument types: the client parses, validates and
 * suggests it, and [resolve] turns the parsed value into what the executor reads, on the thread
 * the command runs on. Selectors such as `@p` resolve against the sender there.
 *
 * Outside a server, [parse] cannot run: these types need the game's registries and players.
 */
@SculkStable
public class PaperArgument<T : Any, N : Any> internal constructor(
    internal val native: BrigadierType<N>,
    override val expected: String,
    override val shape: ArgumentShape,
    private val resolve: (N, CommandSourceStack) -> T,
) : ArgumentType<T> {
    internal fun resolveRaw(raw: Any, stack: CommandSourceStack): T {
        @Suppress("UNCHECKED_CAST")
        return resolve(raw as N, stack)
    }

    override fun parse(text: String, source: CommandSource): SculkResult<T> {
        val stack = source.platformSender as? CommandSourceStack
            ?: return SculkFailure.InvalidInput("argument", "$expected, read by a Paper server", "'$text'")
        return try {
            val reader = StringReader(text)
            val parsed = native.parse(reader, stack)
            if (reader.canRead()) {
                SculkFailure.InvalidInput(
                    "argument",
                    expected,
                    "'$text'",
                )
            } else {
                SculkResult.success(resolve(parsed, stack))
            }
        } catch (invalid: CommandSyntaxException) {
            SculkFailure.InvalidInput("argument", expected, "'$text'", invalid.rawMessage.string)
        }
    }
}

private fun <T : Any, N : Any> paper(
    native: BrigadierType<N>,
    expected: String,
    shape: ArgumentShape = ArgumentShape.WORD,
    resolve: (N, CommandSourceStack) -> T,
) = PaperArgument(native, expected, shape, resolve)

/** One online player, by name or a single-player selector such as `@p`. */
@SculkStable
public fun CommandBuilder.onlinePlayer(name: String): ArgumentHandle<Player> =
    argument(name, paper(ArgumentTypes.player(), "an online player") { resolver, stack -> resolver.resolve(stack).first() })

/** Any number of online players, by name or selector such as `@a`. */
@SculkStable
public fun CommandBuilder.onlinePlayers(name: String): ArgumentHandle<List<Player>> =
    argument(name, paper(ArgumentTypes.players(), "online players") { resolver, stack -> resolver.resolve(stack) })

/** Players by name, online or not, or by selector; resolves to their profiles. */
@SculkStable
public fun CommandBuilder.playerProfiles(name: String): ArgumentHandle<Collection<PlayerProfile>> =
    argument(name, paper(ArgumentTypes.playerProfiles(), "player names") { resolver, stack -> resolver.resolve(stack) })

/** One entity, by UUID or a single-entity selector. */
@SculkStable
public fun CommandBuilder.entity(name: String): ArgumentHandle<Entity> =
    argument(name, paper(ArgumentTypes.entity(), "an entity") { resolver, stack -> resolver.resolve(stack).first() })

@SculkStable
public fun CommandBuilder.entities(name: String): ArgumentHandle<List<Entity>> =
    argument(name, paper(ArgumentTypes.entities(), "entities") { resolver, stack -> resolver.resolve(stack) })

/** Block coordinates, absolute or relative to the sender (`~ ~1 ~`). */
@SculkStable
public fun CommandBuilder.blockPosition(name: String): ArgumentHandle<BlockPosition> =
    argument(name, paper(ArgumentTypes.blockPosition(), "block coordinates") { resolver, stack -> resolver.resolve(stack) })

/** Exact coordinates, absolute or relative to the sender. */
@SculkStable
public fun CommandBuilder.position(name: String): ArgumentHandle<FinePosition> =
    argument(name, paper(ArgumentTypes.finePosition(), "coordinates") { resolver, stack -> resolver.resolve(stack) })

@SculkStable
public fun CommandBuilder.world(name: String): ArgumentHandle<World> =
    argument(name, paper(ArgumentTypes.world(), "a world") { world, _ -> world })

@SculkStable
public fun CommandBuilder.gameMode(name: String): ArgumentHandle<GameMode> =
    argument(name, paper(ArgumentTypes.gameMode(), "a game mode") { mode, _ -> mode })

/** An item with optional components, as `/give` reads it. */
@SculkStable
public fun CommandBuilder.itemStack(name: String): ArgumentHandle<ItemStack> =
    argument(name, paper(ArgumentTypes.itemStack(), "an item") { item, _ -> item })

/** A block with optional state, as `/setblock` reads it. */
@SculkStable
public fun CommandBuilder.blockState(name: String): ArgumentHandle<BlockState> =
    argument(name, paper(ArgumentTypes.blockState(), "a block") { state, _ -> state })

@SculkStable
public fun CommandBuilder.namedColor(name: String): ArgumentHandle<NamedTextColor> =
    argument(name, paper(ArgumentTypes.namedColor(), "a colour name") { colour, _ -> colour })

/** A text component written as JSON, as `/tellraw` reads it. */
@SculkStable
public fun CommandBuilder.textComponent(name: String): ArgumentHandle<Component> =
    argument(name, paper(ArgumentTypes.component(), "a JSON text component", ArgumentShape.GREEDY) { component, _ -> component })

/** A game duration in ticks, written as `20`, `5s` or `1d`. */
@SculkStable
public fun CommandBuilder.ticks(name: String): ArgumentHandle<Int> =
    argument(name, paper(ArgumentTypes.time(), "a time such as 20t, 5s or 1d") { ticks, _ -> ticks })

/** An entry of a registry, such as an enchantment or a biome, by key. */
@SculkStable
public fun <T : Any> CommandBuilder.registryEntry(name: String, registry: RegistryKey<T>): ArgumentHandle<T> =
    argument(name, paper(ArgumentTypes.resource(registry), "a ${registry.key().value().replace('_', ' ')}") { entry, _ -> entry })
