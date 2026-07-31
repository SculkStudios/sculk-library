package studio.sculk.command

import studio.sculk.annotation.SculkStable

/**
 * A `/help` built from the same specs Brigadier was given.
 *
 * [specs] is a supplier, not a list, so a command registered after this one was constructed still
 * appears — the platform builds help last but plugins register in whatever order they like.
 *
 * ```kotlin
 * +HelpCommand({ declaredCommands }).spec()
 * ```
 */
@SculkStable
public class HelpCommand(
    private val specs: () -> List<CommandSpec>,
    private val help: CommandHelp = CommandHelp(),
    private val name: String = "help",
) {
    @SculkStable
    public fun spec(): CommandSpec = command(name) {
        description = "Lists the commands you can run."
        int("page", optional = true, min = 1)
        executes {
            val entries = visibleTo { sender.hasPermission(it) }
            help.page(entries, argumentOrNull<Int>("page") ?: 1).forEach { line ->
                reply(line.template, *line.values.toTypedArray())
            }
        }
    }

    /** The rows a holder of [hasPermission] would see. Exposed so it can be tested with no server. */
    @SculkStable
    public fun visibleTo(hasPermission: (String) -> Boolean): List<CommandHelp.Entry> = help.entries(specs(), hasPermission)
}
