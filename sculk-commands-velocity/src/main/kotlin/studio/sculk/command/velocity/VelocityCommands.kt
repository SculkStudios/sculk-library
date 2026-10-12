package studio.sculk.command.velocity

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.audience.Audience
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.command.ArgumentHandle
import studio.sculk.command.ArgumentShape
import studio.sculk.command.ArgumentType
import studio.sculk.command.BuiltinArgument
import studio.sculk.command.CommandBuilder
import studio.sculk.command.CommandExecutor
import studio.sculk.command.CommandMessages
import studio.sculk.command.CommandNode
import studio.sculk.command.CommandResult
import studio.sculk.command.CommandSource
import studio.sculk.command.CommandSpec
import studio.sculk.command.SenderKind
import studio.sculk.execution.ExecutionContext
import studio.sculk.permission.Permission
import studio.sculk.permission.PermissionContext
import studio.sculk.service.find
import studio.sculk.service.register
import studio.sculk.text.Text
import studio.sculk.velocity.VelocityPermissionSubject
import studio.sculk.velocity.VelocityPermissions
import studio.sculk.velocity.VelocityRuntime
import java.util.UUID
import com.mojang.brigadier.arguments.ArgumentType as BrigadierType
import com.velocitypowered.api.command.CommandSource as VelocitySource
import studio.sculk.command.CommandContext as SculkContext

/**
 * Registers command specs with Velocity as [BrigadierCommand]s. Velocity forwards only Mojang's
 * primitive argument types to the client and supports no custom ones, so numbers, booleans and
 * strings travel natively and every other type travels as a string the proxy parses before the
 * executor runs; a parse failure reaches the sender as the usual invalid-argument message.
 */
@SculkStable
public class VelocityCommands internal constructor(
    private val runtime: VelocityRuntime,
    public val text: Text,
    public val messages: CommandMessages,
) {
    /** Registers [spec] at once; Velocity accepts commands at any time. A taken label is a wiring bug and throws. */
    public fun register(spec: CommandSpec) {
        val manager = runtime.proxy.commandManager
        (listOf(spec.name) + spec.aliases).forEach { label ->
            check(!manager.hasCommand(label)) { "/$label is already registered on this proxy." }
        }
        val command = BrigadierCommand(compile(spec))
        val plugin = runtime.container.instance.orElseThrow { IllegalStateException("${runtime.name} has no plugin instance yet.") }
        manager.register(manager.metaBuilder(command).aliases(*spec.aliases.toTypedArray()).plugin(plugin).build(), command)
        runtime.scope.resources.onClose { manager.unregister(spec.name) }
    }

    public fun register(vararg specs: CommandSpec) {
        specs.forEach(::register)
    }

    private fun sourceOf(source: VelocitySource): VelocityCommandSource = VelocityCommandSource(source, runtime.proxy, runtime.permissions)

    private fun compile(spec: CommandSpec): LiteralArgumentBuilder<VelocitySource> {
        @Suppress("UNCHECKED_CAST")
        return compileNode(spec.root, emptyList()) as LiteralArgumentBuilder<VelocitySource>
    }

    /** A literal's aliases become sibling literals with the same subtree, which is how Brigadier spells an alias. */
    private fun compileWithAliases(node: CommandNode, path: List<CommandNode.Argument<*>>): List<ArgumentBuilder<VelocitySource, *>> =
        if (node is CommandNode.Literal) {
            (listOf(node.name) + node.aliases).map { word -> compileNode(node, path, word) }
        } else {
            listOf(compileNode(node, path))
        }

    private fun compileNode(
        node: CommandNode,
        path: List<CommandNode.Argument<*>>,
        word: String = node.name,
    ): ArgumentBuilder<VelocitySource, *> {
        val here = if (node is CommandNode.Argument<*>) path + node else path
        val builder: ArgumentBuilder<VelocitySource, *> = when (node) {
            is CommandNode.Literal -> BrigadierCommand.literalArgumentBuilder(word)
            is CommandNode.Argument<*> -> argumentBuilder(node)
        }
        val requirement = node.requirement
        builder.requires { source -> requirement.test(sourceOf(source)) }
        node.executor?.let { executor -> builder.executes { context -> execute(executor, here, context) } }
        node.children.forEach { child -> compileWithAliases(child, here).forEach(builder::then) }
        return builder
    }

    private fun argumentBuilder(node: CommandNode.Argument<*>): RequiredArgumentBuilder<VelocitySource, *> {
        val builder = BrigadierCommand.requiredArgumentBuilder(node.name, nativeType(node.type))
        return builder.suggests { context, suggestions ->
            val source = sourceOf(context.source)
            val offered = node.suggestions?.suggest(suggestions.remaining, source) ?: node.type.suggest(suggestions.remaining, source)
            offered.forEach(suggestions::suggest)
            suggestions.buildFuture()
        }
    }

    private fun nativeType(type: ArgumentType<*>): BrigadierType<*> = when (type) {
        is BuiltinArgument.BooleanArgument -> BoolArgumentType.bool()

        is BuiltinArgument.IntegerArgument -> IntegerArgumentType.integer(type.min, type.max)

        is BuiltinArgument.LongArgument -> LongArgumentType.longArg(type.min, type.max)

        is BuiltinArgument.DoubleArgument -> DoubleArgumentType.doubleArg(type.min, type.max)

        else -> when (type.shape) {
            ArgumentShape.WORD -> StringArgumentType.word()
            ArgumentShape.QUOTABLE -> StringArgumentType.string()
            ArgumentShape.GREEDY -> StringArgumentType.greedyString()
        }
    }

    private fun execute(executor: CommandExecutor, path: List<CommandNode.Argument<*>>, context: CommandContext<VelocitySource>): Int {
        val source = sourceOf(context.source)
        val values = LinkedHashMap<ArgumentHandle<*>, Any?>()
        for (node in path) {
            val raw = context.getArgument(node.name, Any::class.java)
            values[node.handle] = when (val parsed = parse(node, raw, source)) {
                is SculkResult.Success -> parsed.value

                is SculkFailure.InvalidInput -> return reject(
                    source,
                    CommandResult.InvalidArgument(node.name, parsed.expected, parsed.received),
                )

                is SculkFailure -> return reject(source, CommandResult.InvalidArgument(node.name, node.type.expected, "'$raw'"))
            }
        }
        val sculkContext = SculkContext.of(source, context.input, context.input.substringBefore(' '), values, text)
        runtime.scope.launch {
            val result = executor.run(sculkContext)
            result.message(text, messages)?.let(source.audience::sendMessage)
            if (result is CommandResult.Error) runtime.log.error("/${context.input} failed for ${source.name}.", result.cause)
        }
        return Command.SINGLE_SUCCESS
    }

    /** Values of native types arrive parsed; everything else arrives as text and is parsed here. */
    private fun parse(node: CommandNode.Argument<*>, raw: Any, source: CommandSource): SculkResult<Any> =
        if (node.type.isNative) SculkResult.success(raw) else node.type.parse(raw as String, source)

    private val ArgumentType<*>.isNative: Boolean
        get() = this is BuiltinArgument.BooleanArgument ||
            this is BuiltinArgument.IntegerArgument ||
            this is BuiltinArgument.LongArgument ||
            this is BuiltinArgument.DoubleArgument

    private fun reject(source: CommandSource, result: CommandResult): Int {
        result.message(text, messages)?.let(source.audience::sendMessage)
        return 0
    }
}

/**
 * A Velocity command sender. Every command on a proxy starts on the plugin's executor, so the
 * context is always [ExecutionContext.Proxy]. Permission checks apply the descriptors registered in
 * [permissions] where Velocity's provider leaves a node undefined.
 */
@SculkStable
public class VelocityCommandSource(
    public val source: VelocitySource,
    public val proxy: ProxyServer,
    permissions: VelocityPermissions? = null,
) : CommandSource {
    private val subject = VelocityPermissionSubject(source, permissions)

    override val name: String get() = (source as? Player)?.username ?: "Console"

    override val kind: SenderKind get() = if (source is Player) SenderKind.PLAYER else SenderKind.CONSOLE

    override val uniqueId: UUID? get() = (source as? Player)?.uniqueId

    override val audience: Audience get() = source

    override val executionContext: ExecutionContext get() = ExecutionContext.Proxy

    override val platformSender: Any get() = source

    override val permissionContext: PermissionContext get() = subject.permissionContext

    override fun hasPermission(permission: String): Boolean = subject.hasPermission(permission)

    override fun hasPermission(permission: Permission): Boolean = subject.hasPermission(permission)

    override fun toString(): String = "VelocityCommandSource($kind $name)"
}

/** An argument resolved against the proxy: its players and servers. */
@SculkStable
public class ProxyArgument<T : Any> internal constructor(
    override val expected: String,
    private val lookup: (ProxyServer, String) -> T?,
    private val names: (ProxyServer) -> Collection<String>,
) : ArgumentType<T> {
    override val shape: ArgumentShape get() = ArgumentShape.WORD

    override fun parse(text: String, source: CommandSource): SculkResult<T> {
        val proxy = (source as? VelocityCommandSource)?.proxy
            ?: return SculkFailure.InvalidInput("argument", "$expected, read on a Velocity proxy", "'$text'")
        return lookup(proxy, text)?.let { SculkResult.success(it) } ?: SculkFailure.InvalidInput("argument", expected, "'$text'")
    }

    override fun suggest(partial: String, source: CommandSource): List<String> {
        val proxy = (source as? VelocityCommandSource)?.proxy ?: return emptyList()
        return names(proxy).filter { it.startsWith(partial, ignoreCase = true) }
    }
}

/** A player connected to this proxy, by name. */
@SculkStable
public fun CommandBuilder.proxyPlayer(name: String): ArgumentHandle<Player> = argument(
    name,
    ProxyArgument("a player on this proxy", { proxy, text ->
        proxy.getPlayer(text).orElse(null)
    }, { proxy -> proxy.allPlayers.map(Player::getUsername) }),
)

/** A backend server registered with this proxy, by name. */
@SculkStable
public fun CommandBuilder.server(name: String): ArgumentHandle<RegisteredServer> = argument(
    name,
    ProxyArgument("a server on this proxy", { proxy, text ->
        proxy.getServer(text).orElse(null)
    }, { proxy -> proxy.allServers.map { it.serverInfo.name } }),
)

/** The proxy sender, for code that needs Velocity's own types. */
@SculkStable
public val SculkContext.velocitySource: VelocitySource get() = source.platformSender as VelocitySource

/** This runtime's command registry, created on first use. */
@SculkStable
public fun VelocityRuntime.commands(text: Text = Text(), messages: CommandMessages = CommandMessages.DEFAULT): VelocityCommands =
    synchronized(services) { services.find<VelocityCommands>() ?: services.register(VelocityCommands(this, text, messages)) }
