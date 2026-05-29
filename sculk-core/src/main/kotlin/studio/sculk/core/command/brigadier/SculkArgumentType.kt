package studio.sculk.core.command.brigadier

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.MessageComponentSerializer
import io.papermc.paper.command.brigadier.argument.CustomArgumentType
import net.kyori.adventure.text.Component
import studio.sculk.core.annotation.SculkInternal
import studio.sculk.core.command.argument.ArgumentParser
import java.util.concurrent.CompletableFuture

/**
 * Bridges a Sculk [ArgumentParser] into Paper's Brigadier command tree.
 *
 * Every Sculk argument becomes a Brigadier argument backed by a string native type, so the client
 * gets native completion, error squiggles, and prior-argument-aware suggestions for free. Parsing
 * and tab-completion delegate to the wrapped parser.
 *
 * The native type is [StringArgumentType.word] normally, or [StringArgumentType.greedyString] for
 * greedy parsers that should consume the remainder of the line.
 */
@SculkInternal
public class SculkArgumentType<T : Any>(
    private val parser: ArgumentParser<T>,
    private val greedy: Boolean = false,
) : CustomArgumentType.Converted<T, String> {
    @Throws(CommandSyntaxException::class)
    override fun convert(nativeType: String): T =
        parser.parse(nativeType)
            ?: throw INVALID.create("'$nativeType' is not a valid ${parser.typeName}.")

    override fun getNativeType(): ArgumentType<String> = if (greedy) StringArgumentType.greedyString() else StringArgumentType.word()

    override fun <S : Any> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining
        parser
            .suggest(remaining)
            .filter { it.startsWith(remaining, ignoreCase = true) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    private companion object {
        val INVALID =
            DynamicCommandExceptionType { message ->
                MessageComponentSerializer.message().serialize(Component.text(message.toString()))
            }
    }
}
