package studio.sculk.platform

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import studio.sculk.annotation.SculkInternal
import java.nio.charset.Charset

/**
 * The block of text a plugin prints when it starts.
 *
 * Facts sit beside the art rather than under it, because the two questions every support
 * conversation opens with — which storage backend is live, did the packet backend load — are
 * answerable from a screenshot of the console if the answers are on screen at start-up.
 *
 * Printed through Paper's [ComponentLogger] so the colours are real components. A plain logger
 * would put raw section signs into log files and into whatever the owner pastes into a ticket.
 */
@SculkInternal
public class SculkBanner(private val logger: ComponentLogger) {
    public fun show(name: String, version: String, facts: List<Pair<String, String>>) {
        val art = artFor(consoleEncoding())
        val width = facts.maxOfOrNull { it.first.length } ?: 0

        logger.info(Component.empty())
        for ((index, line) in art.withIndex()) {
            val fact = facts.getOrNull(index)
            val row = Component.text(line, NamedTextColor.AQUA)
            logger.info(
                if (fact == null) {
                    row
                } else {
                    row
                        .append(Component.text("  ${fact.first.padEnd(width)}  ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(fact.second, NamedTextColor.WHITE))
                },
            )
        }
        logger.info(Component.text("  $name $version", NamedTextColor.GRAY))
        logger.info(Component.empty())
    }

    public fun goodbye(name: String) {
        logger.info(Component.text("$name disabled.", NamedTextColor.GRAY))
    }

    /**
     * Whether the console can actually draw the block characters.
     *
     * Asked of the output encoder rather than guessed from the OS name: a Windows console running
     * UTF-8 renders them fine, and a Linux one under a stripped locale does not. Guessing produces
     * a banner made of question marks on exactly the setups least able to investigate it.
     */
    private fun consoleEncoding(): String = System.getProperty("stdout.encoding")
        ?: System.getProperty("sun.stdout.encoding")
        ?: Charset.defaultCharset().name()

    internal companion object {
        /**
         * The art the console can actually draw.
         *
         * Separate from [show] so the choice is reachable without a logger and a real console —
         * the fallback only matters on setups nobody developing this has in front of them.
         */
        internal fun artFor(encoding: String): List<String> {
            val renderable = runCatching {
                Charset.forName(encoding).newEncoder().canEncode(BLOCKS.joinToString(""))
            }.getOrDefault(false)
            return if (renderable) BLOCKS else ASCII
        }

        val BLOCKS = listOf(
            "  ▄▄▄▄  ",
            " ██▀▀▀▀ ",
            " ▀▀▀▀██ ",
            "  ▀▀▀▀  ",
        )
        val ASCII = listOf(
            "  ____  ",
            " / ___| ",
            " \\___ \\ ",
            " |____/ ",
        )
    }
}
