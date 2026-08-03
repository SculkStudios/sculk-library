package studio.sculk.discord.jda

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.IntegrationType
import net.dv8tion.jda.api.interactions.InteractionContextType
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import studio.sculk.discord.command.CommandOption
import studio.sculk.discord.command.DiscordCommandSpec
import studio.sculk.discord.command.DiscordPermission
import studio.sculk.discord.command.OptionType as SculkOptionType

/**
 * Translates a command spec into JDA's builders.
 *
 * The only place in the codebase that knows what a `SlashCommandData` is. Everything upstream reasons
 * about the spec, which is why the shape of a command tree is unit-tested without a connection.
 */
internal fun DiscordCommandSpec.toJda(): SlashCommandData {
    val data = Commands.slash(name, description)

    defaultPermission?.let { data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(it.toJda())) }
    if (guildOnly) {
        data.setContexts(InteractionContextType.GUILD)
        data.setIntegrationTypes(IntegrationType.GUILD_INSTALL)
    }

    // A child with children of its own is a group; a child without is a plain subcommand. The spec
    // already refused a fourth level, so there is no deeper case to handle here.
    val groups = children.filter { it.children.isNotEmpty() }
    val subcommands = children.filter { it.children.isEmpty() }

    if (subcommands.isNotEmpty()) data.addSubcommands(subcommands.map { it.toSubcommand() })
    if (groups.isNotEmpty()) {
        data.addSubcommandGroups(
            groups.map { group ->
                SubcommandGroupData(group.name, group.description)
                    .addSubcommands(group.children.map { it.toSubcommand() })
            },
        )
    }
    if (children.isEmpty()) data.addOptions(options.map { it.toJda() })

    return data
}

private fun DiscordCommandSpec.toSubcommand(): SubcommandData = SubcommandData(name, description).addOptions(options.map { it.toJda() })

private fun CommandOption.toJda(): OptionData {
    val data = OptionData(type.toJda(), name, description, required, autocomplete != null)
    if (choices.isNotEmpty()) {
        choices.forEach { data.addChoice(it.name, it.value) }
    }
    // Only meaningful on the numeric types, and JDA throws on the others rather than ignoring it.
    // The long and double overloads are not interchangeable either: passing a double bound to an
    // INTEGER option throws, which is why the two branches exist rather than one.
    when (type) {
        SculkOptionType.Integer -> {
            minValue?.let { data.setMinValue(it.toLong()) }
            maxValue?.let { data.setMaxValue(it.toLong()) }
        }

        SculkOptionType.Number -> {
            minValue?.let { data.setMinValue(it) }
            maxValue?.let { data.setMaxValue(it) }
        }

        else -> Unit
    }
    return data
}

private fun SculkOptionType.toJda(): OptionType = when (this) {
    SculkOptionType.String -> OptionType.STRING
    SculkOptionType.Integer -> OptionType.INTEGER
    SculkOptionType.Number -> OptionType.NUMBER
    SculkOptionType.Boolean -> OptionType.BOOLEAN
    SculkOptionType.User -> OptionType.USER
    SculkOptionType.Channel -> OptionType.CHANNEL
    SculkOptionType.Role -> OptionType.ROLE
    SculkOptionType.Mentionable -> OptionType.MENTIONABLE
    SculkOptionType.Attachment -> OptionType.ATTACHMENT
}

private fun DiscordPermission.toJda(): Permission = when (this) {
    DiscordPermission.ManageMessages -> Permission.MESSAGE_MANAGE
    DiscordPermission.KickMembers -> Permission.KICK_MEMBERS
    DiscordPermission.BanMembers -> Permission.BAN_MEMBERS
    DiscordPermission.ModerateMembers -> Permission.MODERATE_MEMBERS
    DiscordPermission.ManageGuild -> Permission.MANAGE_SERVER
    DiscordPermission.Administrator -> Permission.ADMINISTRATOR
}
