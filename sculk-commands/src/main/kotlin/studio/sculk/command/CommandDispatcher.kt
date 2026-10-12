package studio.sculk.command

import net.kyori.adventure.text.Component
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.text.Text
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs command specs directly, without a server: parses input the way the platform trees do,
 * checks requirements, runs middleware and the executor, and answers the sender. This is how
 * commands are unit-tested, and the reference the platform compilers are checked against.
 *
 * The executor runs in the calling coroutine; a platform dispatches to the sender's context first.
 */
@SculkStable
public class CommandDispatcher(public val text: Text = Text(), public val messages: CommandMessages = CommandMessages.DEFAULT) {
    private val byLabel = ConcurrentHashMap<String, CommandSpec>()

    public val commands: Collection<CommandSpec> get() = byLabel.values.toSet()

    /** Adds [spec] under its name and aliases. A label taken by another command is a wiring bug and throws. */
    public fun register(spec: CommandSpec) {
        (listOf(spec.name) + spec.aliases).forEach { label ->
            val previous = byLabel.putIfAbsent(label, spec)
            check(previous == null || previous === spec) { "/$label is already registered by /${previous?.name}." }
        }
    }

    /** Parses and runs [input] for [source], answering the sender as a server would, and returns the outcome. */
    public suspend fun execute(source: CommandSource, input: String): CommandResult {
        val result = run(source, input.removePrefix("/"))
        result.message(text, messages)?.let(source.audience::sendMessage)
        return result
    }

    private suspend fun run(source: CommandSource, input: String): CommandResult {
        val reader = CommandReader(input)
        val label = reader.readWord().lowercase()
        val spec = byLabel[label]?.takeIf { it.root.requirement.test(source) } ?: return CommandResult.UnknownCommand(input)
        return when (val match = match(spec.root, reader, source, emptyMap())) {
            is Match.Complete -> match.executor.run(CommandContext(source, input, label, match.values, text))
            is Match.Incomplete -> CommandResult.Incomplete(usage(spec, source).firstOrNull() ?: "/$label")
            is Match.Invalid -> CommandResult.InvalidArgument(match.argument, match.failure.expected, match.failure.received)
            is Match.Unknown -> CommandResult.UnknownCommand(input)
        }
    }

    /** Completions for the last word of [input], as the client would show them. */
    public fun suggest(source: CommandSource, input: String): List<String> {
        val text = input.removePrefix("/")
        if (' ' !in text) {
            return byLabel.filter { (label, spec) ->
                label.startsWith(text.lowercase()) && spec.root.requirement.test(source)
            }.keys.sorted()
        }
        val spec = byLabel[text.substringBefore(' ').lowercase()]?.takeIf { it.root.requirement.test(source) } ?: return emptyList()
        val reader = CommandReader(text, text.indexOf(' '))
        return completions(spec.root, reader, source).distinct().sorted()
    }

    private fun completions(node: CommandNode, reader: CommandReader, source: CommandSource): List<String> {
        if (!reader.skipSeparator()) return emptyList()
        val start = reader.cursor
        val partial = reader.remaining
        return node.children.filter { it.requirement.test(source) }.flatMap { child ->
            reader.reset(start)
            val token = read(child, reader) ?: return@flatMap emptyList()
            when {
                !reader.canRead -> offers(child, partial, source)

                child is CommandNode.Literal && token.equals(child.name, ignoreCase = true) -> completions(child, reader, source)

                child is CommandNode.Argument<*> && child.type.parse(
                    token,
                    source,
                ) is SculkResult.Success -> completions(child, reader, source)

                else -> emptyList()
            }
        }
    }

    private fun offers(node: CommandNode, partial: String, source: CommandSource): List<String> = when (node) {
        is CommandNode.Literal -> listOf(node.name).filter { it.startsWith(partial.lowercase()) }
        is CommandNode.Argument<*> -> (node.suggestions?.suggest(partial, source) ?: node.type.suggest(partial, source))
    }

    /** One line per runnable path that [source] may use; see [CommandSpec.usage]. */
    public fun usage(spec: CommandSpec, source: CommandSource): List<String> = spec.usage(source)

    private fun match(node: CommandNode, reader: CommandReader, source: CommandSource, values: Map<ArgumentDeclaration, Any?>): Match {
        if (!reader.canRead) {
            val executor = node.executor ?: return Match.Incomplete(reader.cursor)
            return Match.Complete(executor, values)
        }
        if (!reader.skipSeparator()) return Match.Unknown(reader.cursor)
        val start = reader.cursor
        var best: Match = Match.Unknown(start)
        for (child in node.children.sortedBy { it is CommandNode.Argument<*> }) {
            if (!child.requirement.test(source)) continue
            reader.reset(start)
            val token = read(child, reader) ?: continue
            val next = when (child) {
                is CommandNode.Literal -> if (token.equals(
                        child.name,
                        ignoreCase = true,
                    )
                ) {
                    match(child, reader, source, values)
                } else {
                    continue
                }

                is CommandNode.Argument<*> -> when (val parsed = child.type.parse(token, source)) {
                    is SculkResult.Success -> match(child, reader, source, values + (child.handle.declaration to parsed.value))

                    is SculkFailure.InvalidInput -> Match.Invalid(start, child.name, parsed)

                    is SculkFailure -> Match.Invalid(
                        start,
                        child.name,
                        SculkFailure.InvalidInput("argument", child.type.expected, "'$token'"),
                    )
                }
            }
            if (next is Match.Complete) return next
            if (next.position >= best.position) best = next
        }
        return best
    }

    /** The text [node] would consume at the cursor, or null when it cannot (an unclosed quote). */
    private fun read(node: CommandNode, reader: CommandReader): String? = when (node) {
        is CommandNode.Literal -> reader.readWord()

        is CommandNode.Argument<*> -> when (node.type.shape) {
            ArgumentShape.WORD -> reader.readWord()
            ArgumentShape.QUOTABLE -> reader.readQuotable()
            ArgumentShape.GREEDY -> reader.readRest()
        }
    }

    private sealed class Match(val position: Int) {
        class Complete(val executor: CommandExecutor, val values: Map<ArgumentDeclaration, Any?>) : Match(Int.MAX_VALUE)

        class Incomplete(position: Int) : Match(position)

        class Invalid(position: Int, val argument: String, val failure: SculkFailure.InvalidInput) : Match(position)

        class Unknown(position: Int) : Match(position)
    }
}

/** What happened when a command ran. Every outcome except [Success] has a message for the sender. */
@SculkStable
public sealed class CommandResult {
    /** What the sender is told, rendered with [text] from [messages]; null when there is nothing to say. */
    public abstract fun message(text: Text, messages: CommandMessages): Component?

    @SculkStable
    public data object Success : CommandResult() {
        override fun message(text: Text, messages: CommandMessages): Component? = null
    }

    /** The executor stopped with [CommandContext.fail]. */
    @SculkStable
    public class Failed(public val reason: Component) : CommandResult() {
        override fun message(text: Text, messages: CommandMessages): Component = reason
    }

    @SculkStable
    public class UnknownCommand(public val input: String) : CommandResult() {
        override fun message(text: Text, messages: CommandMessages): Component = text.render(messages.unknownCommand)
    }

    /** Input ended where nothing runs; [usage] is the first runnable form. */
    @SculkStable
    public class Incomplete(public val usage: String) : CommandResult() {
        override fun message(text: Text, messages: CommandMessages): Component = text.render(messages.incompleteCommand, "usage" to usage)
    }

    @SculkStable
    public class InvalidArgument(public val argument: String, public val expected: String, public val found: String) : CommandResult() {
        override fun message(text: Text, messages: CommandMessages): Component = text.render(
            messages.invalidArgument,
            "argument" to "<$argument>",
            "expected" to expected,
            "found" to found,
        )
    }

    @SculkStable
    public data object PlayersOnly : CommandResult() {
        override fun message(text: Text, messages: CommandMessages): Component = text.render(messages.playersOnly)
    }

    /** The executor threw; the sender saw a generic message, and [cause] belongs in the log. */
    @SculkStable
    public class Error(public val cause: Exception) : CommandResult() {
        override fun message(text: Text, messages: CommandMessages): Component = text.render(messages.internalError)
    }
}
