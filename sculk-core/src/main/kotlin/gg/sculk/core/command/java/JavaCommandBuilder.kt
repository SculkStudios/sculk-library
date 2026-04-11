package gg.sculk.core.command.java

import gg.sculk.core.annotation.SculkStable
import gg.sculk.core.command.CommandBuilder
import gg.sculk.core.command.CommandContext
import java.util.function.Consumer

/**
 * Fluent Java-compatible builder for Sculk Studio commands.
 *
 * Kotlin users should use the [gg.sculk.core.command.command] DSL instead.
 *
 * Java example:
 * ```java
 * Command.builder("sculk")
 *     .permission("sculk.admin")
 *     .sub("ping", sub -> sub.executes(ctx -> ctx.reply("Pong!")))
 *     .build();
 * ```
 */
@SculkStable
public class JavaCommandBuilder private constructor(
    private val builder: CommandBuilder,
) {
    public companion object {
        /** Creates a new [JavaCommandBuilder] for a command with [name]. */
        @JvmStatic
        public fun create(name: String): JavaCommandBuilder = JavaCommandBuilder(CommandBuilder(name))
    }

    /** Sets the permission required to run this command. */
    public fun permission(permission: String): JavaCommandBuilder {
        builder.permission = permission
        return this
    }

    /** Sets the short description shown in auto-generated help. */
    public fun description(description: String): JavaCommandBuilder {
        builder.description = description
        return this
    }

    /** Executes [handler] for any sender type. */
    public fun executes(handler: Consumer<CommandContext>): JavaCommandBuilder {
        builder.executes { handler.accept(this) }
        return this
    }

    /** Executes [handler] for player senders only. Console is rejected automatically. */
    public fun player(handler: Consumer<CommandContext>): JavaCommandBuilder {
        builder.player { handler.accept(this) }
        return this
    }

    /** Executes [handler] for console senders only. Players are rejected automatically. */
    public fun console(handler: Consumer<CommandContext>): JavaCommandBuilder {
        builder.console { handler.accept(this) }
        return this
    }

    /** Registers a subcommand with [name], configured via [configurator]. */
    public fun sub(
        name: String,
        configurator: Consumer<JavaCommandBuilder>,
    ): JavaCommandBuilder {
        val subJava = JavaCommandBuilder(CommandBuilder(name))
        configurator.accept(subJava)
        builder.node.subcommands += subJava.build().node
        return this
    }

    /** Registers a required string argument. */
    public fun string(name: String): JavaCommandBuilder {
        builder.string(name)
        return this
    }

    /** Registers an integer argument. */
    public fun int(name: String): JavaCommandBuilder {
        builder.int(name)
        return this
    }

    /** Registers a double argument. */
    public fun double(name: String): JavaCommandBuilder {
        builder.double(name)
        return this
    }

    /** Registers a boolean argument. */
    public fun boolean(name: String): JavaCommandBuilder {
        builder.boolean(name)
        return this
    }

    /** Registers an online-player argument. */
    public fun player(name: String): JavaCommandBuilder {
        builder.player(name)
        return this
    }

    /** Registers a fixed-choice argument. */
    public fun choice(
        name: String,
        vararg choices: String,
    ): JavaCommandBuilder {
        builder.choice(name, *choices)
        return this
    }

    /** Builds and returns the [CommandBuilder]. */
    public fun build(): CommandBuilder = builder
}
