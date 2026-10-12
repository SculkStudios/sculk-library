package studio.sculk.command

import studio.sculk.annotation.SculkStable
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionCheck
import kotlin.time.Duration

/** Marks the command DSL so an inner block cannot reach an outer builder by accident. */
@SculkStable
@DslMarker
public annotation class CommandDsl

/**
 * Declares a command. Each block stands for one word of the command:
 *
 * - arguments declared in a block form a chain after that word, each following the last, and
 *   [CommandBuilder.executes] runs when input ends at the end of the chain;
 * - [CommandBuilder.literal] branches from the block's own word, whatever was declared before it;
 * - [CommandBuilder.after] branches from an argument, for forms such as `/eco <player> give`.
 *
 * ```kotlin
 * val pay = command("pay") {
 *     permission = "economy.pay"
 *     val target = word("player")
 *     val amount = double("amount", min = 0.01)
 *     player {
 *         reply("<success>Sent <amount> to <target>.", "amount" to this[amount], "target" to this[target])
 *     }
 * }
 * ```
 *
 * The tree is checked as it is built: duplicate words, two arguments at one position, a greedy
 * argument with anything after it, or a required argument after an optional one each throw, since
 * each is a wiring bug.
 */
@SculkStable
public fun command(name: String, vararg aliases: String, block: CommandBuilder.() -> Unit): CommandSpec {
    val builder = CommandBuilder(Draft.literal(name), parent = null).apply(block)
    val root = builder.build()
    aliases.forEach(::validWord)
    return CommandSpec(root as CommandNode.Literal, aliases.toList(), builder.description).also { builder.built = it }
}

@SculkStable
@CommandDsl
public class CommandBuilder internal constructor(private val scope: Draft, private val parent: CommandBuilder?) {
    private var tail: Draft = scope
    private val middleware = mutableListOf<CommandMiddleware>()
    private val branches = mutableListOf<Pair<Draft, CommandBuilder>>()

    /** Shown in help. */
    public var description: String? = null

    /**
     * The permission node needed to see and use this command or branch, checked as the platform
     * checks any string. Declare a [Permission] with [permission] instead so its default applies.
     */
    public var permission: String?
        get() = permissionCheck?.node
        set(value) {
            permissionCheck = value?.let(PermissionCheck::node)
        }

    private var permissionCheck: PermissionCheck? = null

    private var condition: ((CommandSource) -> Boolean)? = null

    /**
     * The permission needed to see and use this command or branch: a [Permission] descriptor,
     * whose default applies where nothing sets its node, or a contextual check such as
     * `Fly.use.where(PermissionContext.WORLD, "minecraft:the_nether")`. Replaces any earlier one.
     */
    public fun permission(check: PermissionCheck) {
        permissionCheck = check
    }

    internal var built: CommandSpec? = null

    /** An extra condition for this command or branch, such as a game mode. Senders who fail it never see it. */
    public fun requires(condition: (CommandSource) -> Boolean) {
        check(this.condition == null) { "A branch has one requires block; combine the conditions." }
        this.condition = condition
    }

    /** A fixed word branching from this block's word; [aliases] lead to the same branch. */
    public fun literal(name: String, vararg aliases: String, block: CommandBuilder.() -> Unit) {
        val node = Draft.literal(name, aliases.toList())
        attach(scope, node)
        branches += node to CommandBuilder(node, this).apply(block)
    }

    /**
     * Continues the command after [argument], which must be declared in this block: words and
     * arguments in [block] follow it, as in `/eco <player> give <amount>`.
     */
    public fun after(argument: ArgumentHandle<*>, block: CommandBuilder.() -> Unit) {
        val node = scope.find(argument.declaration)
            ?: throw IllegalStateException("$argument is not declared in this block; after() continues an argument of the same block.")
        check(node.argument?.type?.shape != ArgumentShape.GREEDY) { "Nothing can follow the greedy argument <${node.name}>." }
        branches += node to CommandBuilder(node, this).apply(block)
    }

    /** An argument of any [type], appended to the chain. */
    public fun <T : Any> argument(name: String, type: ArgumentType<T>): ArgumentHandle<T> {
        checkName(name)
        check(tail.argument?.type !is FlagsArgument) { "<$name> follows the flags; declare arguments before flags." }
        check(tail.argument?.type?.shape != ArgumentShape.GREEDY) { "Nothing can follow the greedy argument <${tail.name}>." }
        val declaration = ArgumentDeclaration(name, type)
        val node = Draft.argument(declaration)
        attach(tail, node)
        tail = node
        return ArgumentHandle(name, declaration)
    }

    public fun boolean(name: String): ArgumentHandle<Boolean> = argument(name, BuiltinArgument.BooleanArgument())

    public fun integer(name: String, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): ArgumentHandle<Int> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.IntegerArgument(min, max))
    }

    public fun long(name: String, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE): ArgumentHandle<Long> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.LongArgument(min, max))
    }

    public fun double(name: String, min: Double = -Double.MAX_VALUE, max: Double = Double.MAX_VALUE): ArgumentHandle<Double> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.DoubleArgument(min, max))
    }

    /** One word, up to the next space. */
    public fun word(name: String, maxLength: Int = Int.MAX_VALUE, pattern: Regex? = null): ArgumentHandle<String> =
        argument(name, BuiltinArgument.StringArgument(ArgumentShape.WORD, maxLength, pattern))

    /** A word, or quoted text with spaces. */
    public fun string(name: String, maxLength: Int = Int.MAX_VALUE, pattern: Regex? = null): ArgumentHandle<String> =
        argument(name, BuiltinArgument.StringArgument(ArgumentShape.QUOTABLE, maxLength, pattern))

    /** The rest of the input. Must be the last argument. */
    public fun greedy(name: String, maxLength: Int = Int.MAX_VALUE): ArgumentHandle<String> =
        argument(name, BuiltinArgument.StringArgument(ArgumentShape.GREEDY, maxLength, null))

    /** One of [choices], by key; matching ignores case. */
    public fun <T : Any> choice(name: String, choices: Map<String, T>): ArgumentHandle<T> =
        argument(name, BuiltinArgument.ChoiceArgument(choices))

    public fun uuid(name: String): ArgumentHandle<java.util.UUID> = argument(name, BuiltinArgument.UuidArgument())

    public fun key(name: String): ArgumentHandle<net.kyori.adventure.key.Key> = argument(name, BuiltinArgument.KeyArgument())

    public fun duration(name: String, min: Duration = Duration.ZERO, max: Duration = Duration.INFINITE): ArgumentHandle<Duration> {
        require(min <= max) { "<$name> has min $min above max $max." }
        return argument(name, BuiltinArgument.DurationArgument(min, max))
    }

    /**
     * A presence flag after the arguments, `--name` or `-short`; it reads as true when written.
     * Flags come after every argument of the chain and may be written in any order.
     */
    public fun flag(name: String, short: Char? = null): ArgumentHandle<Boolean> = declareFlag(name, short, null, default = false)

    /** A value flag after the arguments, `--name value`; it reads as null when left out. [type] reads one word. */
    public fun <T : Any> flag(name: String, type: ArgumentType<T>, short: Char? = null): ArgumentHandle<T?> {
        require(type.shape == ArgumentShape.WORD) { "--$name takes one word, so its type must read a word." }
        return declareFlag(name, short, type, default = null)
    }

    private fun <T> declareFlag(name: String, short: Char?, type: ArgumentType<*>?, default: Any?): ArgumentHandle<T> {
        require(ARGUMENT_NAME.matches(name)) { "Flag name '$name' must be lower-case letters, digits, '-' or '_'." }
        require(short == null || short.isLetter()) { "A short flag is one letter, but was '$short'." }
        val group = tail.argument?.takeIf { it.type is FlagsArgument } ?: run {
            check(tail.argument?.type?.shape != ArgumentShape.GREEDY) { "Flags cannot follow the greedy argument <${tail.name}>." }
            check(tail.executor == null) { "--$name is declared after executes; declare flags before the executor so it sees them." }
            val declaration = ArgumentDeclaration("flags", FlagsArgument()).apply {
                optional = true
                this.default = FlagValues.NONE
            }
            Draft.argument(declaration).also { node ->
                attach(tail, node)
                tail = node
            }.argument
        }
        (group?.type as FlagsArgument).add(FlagDefinition(name, short, type))
        val declaration = ArgumentDeclaration(name, type ?: BuiltinArgument.BooleanArgument()).apply {
            this.default = default
            flags = group
        }
        return ArgumentHandle(name, declaration)
    }

    /** Makes this argument optional; it reads as null when left out. Only the last arguments of a chain can be optional. */
    public fun <T : Any> ArgumentHandle<T>.optional(): ArgumentHandle<T?> {
        declaration.optional = true
        @Suppress("UNCHECKED_CAST")
        return this as ArgumentHandle<T?>
    }

    /** Makes this argument optional; it reads as [default] when left out. */
    public fun <T : Any> ArgumentHandle<T>.optional(default: T): ArgumentHandle<T> {
        declaration.optional = true
        declaration.default = default
        return this
    }

    /** Replaces this argument's completions, for values that depend on the server or the sender. */
    public fun <T> ArgumentHandle<T>.suggests(provider: SuggestionProvider): ArgumentHandle<T> {
        declaration.suggestions = provider
        return this
    }

    /** Wraps every executor in this block and the blocks inside it, outermost first. */
    public fun use(middleware: CommandMiddleware) {
        this.middleware += middleware
    }

    /** Lets each sender run the executors in this block once per [duration]. */
    public fun cooldown(duration: Duration) {
        use(Cooldown(duration))
    }

    /** Asks for the command to be run twice within [window] before it proceeds. */
    public fun confirm(window: Duration) {
        use(Confirmation(window))
    }

    /**
     * Adds a `help` word that lists every form of the command the sender may use, generated from
     * the tree, under [header]. Only at the top level of a command.
     */
    public fun help(header: String = "<primary>/<command><muted> can be used as:") {
        check(parent == null) { "help() belongs at the top level of a command." }
        val root = this
        literal("help") {
            executes {
                val spec = checkNotNull(root.built) { "The command is still being declared." }
                reply(header, "command" to spec.name)
                spec.usage(source).forEach { line -> reply("<secondary><line>", "line" to line) }
            }
        }
    }

    /** Runs [body] when input ends at the end of the chain. */
    public fun executes(body: suspend CommandContext.() -> Unit) {
        setExecutor(requiresPlayer = false, body)
    }

    /** Like [executes], for players only; anyone else gets the players-only message. */
    public fun player(body: suspend CommandContext.() -> Unit) {
        setExecutor(requiresPlayer = true, body)
    }

    private fun setExecutor(requiresPlayer: Boolean, body: suspend CommandContext.() -> Unit) {
        check(tail.executor == null) { "<${tail.name}> already has an executor." }
        tail.executor = PendingExecutor(this, requiresPlayer, body)
    }

    private fun attach(parent: Draft, node: Draft) {
        val clash = parent.children.firstOrNull { it.clashesWith(node) }
        check(clash == null) {
            if (node.argument != null) {
                "<${node.name}> and <${clash?.name}> would both read the same position after ${parent.name}."
            } else {
                "'${node.name}' is declared twice after ${parent.name}."
            }
        }
        parent.children += node
        node.parent = parent
    }

    private fun checkName(name: String) {
        require(ARGUMENT_NAME.matches(name)) { "Argument name '$name' must be lower-case letters, digits, '-' or '_'." }
        val taken = generateSequence(tail) { it.parent }.any { it.argument?.name == name }
        check(!taken) { "Two arguments on one path are both named <$name>." }
    }

    internal fun middlewareChain(): List<CommandMiddleware> = parent?.middlewareChain().orEmpty() + middleware

    internal fun build(): CommandNode {
        applyRequirements()
        return scope.freeze()
    }

    private fun applyRequirements() {
        scope.requirement = Requirement(permissionCheck, condition)
        branches.forEach { (_, builder) -> builder.applyRequirements() }
    }

    private companion object {
        val ARGUMENT_NAME = Regex("[a-z][a-z0-9_-]*")
    }
}

internal class PendingExecutor(val builder: CommandBuilder, val requiresPlayer: Boolean, val body: suspend CommandContext.() -> Unit)

/** A mutable node while the DSL runs; frozen into [CommandNode] once the whole command is declared. */
internal class Draft private constructor(val name: String, val aliases: List<String>, val argument: ArgumentDeclaration?) {
    val children = mutableListOf<Draft>()
    var parent: Draft? = null
    var executor: PendingExecutor? = null
    var requirement: Requirement = Requirement.NONE

    fun clashesWith(other: Draft): Boolean = when {
        argument != null && other.argument != null -> true

        argument == null && other.argument == null -> (aliases + name).any { word ->
            (other.aliases + other.name).any { it.equals(word, true) }
        }

        else -> false
    }

    fun find(declaration: ArgumentDeclaration): Draft? =
        if (argument === declaration) this else children.firstNotNullOfOrNull { if (it.argument != null) it.find(declaration) else null }

    fun freeze(): CommandNode {
        checkOptionalChain()
        val frozenChildren = children.map { it.freeze() }
        val runs = executor ?: inheritedFromOptionalChild()
        val compiled = runs?.let { CommandExecutor(it.builder.middlewareChain(), it.requiresPlayer, it.body) }
        return if (argument == null) {
            CommandNode.Literal(name, aliases, frozenChildren, compiled, requirement)
        } else {
            CommandNode.Argument(
                ArgumentHandle<Any?>(argument.name, argument),
                argument.type,
                argument.suggestions,
                frozenChildren,
                compiled,
                requirement,
            )
        }
    }

    /** A node runs the executor of an optional argument after it, as if the sender stopped here. */
    private fun inheritedFromOptionalChild(): PendingExecutor? {
        val next = children.singleOrNull { it.argument != null } ?: return null
        if (next.argument?.optional != true) return null
        return next.executor ?: next.inheritedFromOptionalChild()
    }

    private fun checkOptionalChain() {
        if (argument?.optional != true) return
        val required = children.firstOrNull { it.argument != null && !it.argument.optional }
        check(required == null) {
            "<${required?.name}> is required but follows the optional <$name>; only the last arguments can be optional."
        }
        check(children.none { it.argument == null }) { "A word cannot follow the optional <$name>." }
    }

    companion object {
        fun literal(name: String, aliases: List<String> = emptyList()): Draft {
            validWord(name)
            aliases.forEach(::validWord)
            return Draft(name, aliases, null)
        }

        fun argument(declaration: ArgumentDeclaration): Draft = Draft(declaration.name, emptyList(), declaration)
    }
}

internal fun validWord(word: String) {
    require(word.isNotEmpty() && word.none { it.isWhitespace() } && word == word.lowercase()) {
        "Command word '$word' must be lower case with no spaces."
    }
}
