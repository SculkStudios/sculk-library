package studio.sculk.example.bot

import studio.sculk.SculkHandle
import studio.sculk.discord.ComponentId
import studio.sculk.discord.DiscordGateway
import studio.sculk.discord.command.DiscordPermission
import studio.sculk.discord.command.OptionChoice
import studio.sculk.discord.command.discordCommand
import studio.sculk.discord.interaction.InteractionRouter
import studio.sculk.discord.interaction.modal
import studio.sculk.discord.message.ButtonStyle
import studio.sculk.discord.message.message
import studio.sculk.fold
import studio.sculk.getOrElse
import studio.sculk.text.ThemeStyle
import kotlin.time.Duration.Companion.seconds

/** The palette. Defined once, so Discord and a Minecraft server can share it. */
private val danger = ThemeStyle.Solid("#e57373")
private val ok = ThemeStyle.Solid("#81c784")

/** Stands in for whatever a real bot reads its choices from — a config, a database, a live registry. */
private val kits = mutableListOf("starter", "veteran", "seasonal")

private const val NAMESPACE = "example"

fun registerCommands(router: InteractionRouter, gateway: DiscordGateway): SculkHandle {
    val handles = listOf(
        router.register(ping()),
        router.register(kit()),
        router.register(confirm(gateway)),
        router.register(punish()),
        router.onComponent(NAMESPACE) { interaction ->
            // Every button lands here, and what it means is read back out of its id rather than
            // remembered anywhere. Re-resolve everything it names: the id has been outside the
            // process, so it is input.
            when (interaction.componentId.part(0)) {
                "kit" -> interaction.reply(message { text("You picked **${interaction.componentId.part(1)}**.") })
                else -> interaction.reply(message { text("That button is no longer wired to anything.") })
            }
        },
        router.onModal(NAMESPACE) { submission ->
            val reason = submission.field("reason").orEmpty().ifBlank { "no reason given" }
            submission.reply(
                message {
                    container(danger) { text("Recorded: $reason") }
                    ephemeral = true
                },
            )
        },
    )
    return SculkHandle.all(handles)
}

/** The smallest possible command. */
private fun ping() = discordCommand("ping") {
    description = "Check the bot is alive"
    executes {
        reply(message { text("Pong.") })
    }
}

/**
 * Options, autocomplete, and a reply that offers buttons.
 *
 * The autocomplete lambda reads `kits` every keystroke. A list captured when the command was
 * registered would stop matching the moment anything changed it, and stay wrong until a restart.
 */
private fun kit() = discordCommand("kit") {
    description = "Kits"

    sub("list") {
        description = "Show every kit"
        ephemeral = false
        executes {
            reply(
                message {
                    container(ok) {
                        text("**Kits**")
                        divider()
                        text(kits.joinToString("\n") { "· $it" })
                        row {
                            kits.take(3).forEach { name ->
                                ComponentId.of(NAMESPACE, "kit", name).getOrNull()?.let { id ->
                                    button(name, id)
                                }
                            }
                        }
                    }
                },
            )
        }
    }

    sub("give") {
        description = "Give a kit to someone"
        user("target", "Who gets it", required = true)
        string("kit", "Which kit") { typed -> kits.filter { it.startsWith(typed) }.map { OptionChoice(it, it) } }
        executes {
            val target = option("target").asUser
            val chosen = optionOrNull("kit")?.asString ?: kits.first()
            reply(message { text("Gave **$chosen** to <@${target.raw}>.") })
        }
    }
}

/**
 * Post something, then wait for the answer.
 *
 * The collector is the part with no JDA equivalent — without it this is a listener, a map keyed by
 * message id, and a scheduled task to clean up the ones nobody ever clicked.
 */
private fun confirm(gateway: DiscordGateway) = discordCommand("confirm") {
    description = "Ask a question and wait fifteen seconds for the answer"
    ephemeral = false

    executes {
        val yes = ComponentId.of(NAMESPACE, "confirm", "yes").getOrThrow()
        val no = ComponentId.of(NAMESPACE, "confirm", "no").getOrThrow()

        reply(
            message {
                container(danger) {
                    text("**Wipe the test database?**")
                    row {
                        button("Do it", yes, ButtonStyle.Danger)
                        button("Cancel", no)
                    }
                }
            },
        )

        // Posted through the channel rather than the interaction so there is a message id to watch.
        val posted = gateway.sendText(channel, "-# waiting for a decision…").getOrNull() ?: return@executes
        val click = gateway.awaitComponent(posted, within = 15.seconds, from = actor.id)

        click.fold(
            { interaction ->
                val decided = if (interaction.componentId.part(1) == "yes") "Wiped." else "Cancelled."
                gateway.edit(channel, posted, message { text(decided) })
            },
            { _, _ -> gateway.edit(channel, posted, message { text("Timed out — nothing was done.") }) },
        )
    }
}

/**
 * A modal, and a Discord-side permission.
 *
 * The permission is Discord's, not the game's: the person running this is not on the server, so
 * there is no live permissible to ask, and inheriting authority from a forgotten in-game rank is how
 * somebody ends up with powers nobody meant to grant.
 */
private fun punish() = discordCommand("punish") {
    description = "Record a punishment"
    defaultPermission = DiscordPermission.ModerateMembers
    user("target", "Who", required = true)

    executes {
        if (!actor.holds(DiscordPermission.ModerateMembers)) {
            reply(message { text("You need Moderate Members to use this.") })
            return@executes
        }
        val target = option("target").asUser
        val id = ComponentId.of(NAMESPACE, "punish", target.raw).getOrElse { reason, _ ->
            reply(message { text(reason) })
            return@executes
        }
        // A modal has to be the first response, which is why nothing above this acknowledges. After
        // a defer, replyModal is not even on the type.
        replyModal(
            modal(id, "Why?") {
                field("reason", "Reason", placeholder = "What did they do?", maxLength = 200)
            },
        )
    }
}
