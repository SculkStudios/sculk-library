package studio.sculk.platform

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.logger.slf4j.ComponentLogger
import studio.sculk.annotation.SculkInternal
import studio.sculk.annotation.SculkStable
import java.nio.charset.Charset

/**
 * The art drawn beside a plugin's start-up facts.
 *
 * Two sets rather than one, because a console that cannot encode block characters draws them as
 * question marks — and that is exactly the setup least able to work out why. [fallback] defaults to
 * [lines], which is correct for art that is already ASCII.
 *
 * ```kotlin
 * override fun bannerArt(): BannerArt = BannerArt(
 *     lines = listOf("  ▄▄▄  ", " ██▀██ ", " ▀▀▀▀▀ "),
 *     fallback = listOf("  ___  ", " |   | ", " |___| "),
 * )
 * ```
 */
@SculkStable
public class BannerArt(public val lines: List<String>, public val fallback: List<String> = lines) {
    init {
        require(lines.isNotEmpty()) { "Banner art needs at least one line." }
        // A shorter fallback shifts every fact up a row on the consoles least able to report it.
        require(lines.size == fallback.size) {
            "Banner art and its fallback must be the same height, got ${lines.size} and ${fallback.size}."
        }
    }

    @SculkStable
    public companion object {
        /** Sculk's own mark. The default, and what a plugin replaces to stop printing two banners. */
        @SculkStable
        public val SCULK: BannerArt = BannerArt(
            lines = listOf(
                "  ▄██████ ",
                " ██       ",
                "  ▀█████▄ ",
                "       ██ ",
                " ██████▀  ",
            ),
            fallback = listOf(
                "   ____  ",
                "  / ___| ",
                "  \\___ \\ ",
                "   ___) |",
                "  |____/ ",
            ),
        )
    }
}

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
public class SculkBanner(private val logger: ComponentLogger, private val art: BannerArt = BannerArt.SCULK) {
    public fun show(name: String, version: String, facts: List<Pair<String, String>>) {
        val width = facts.maxOfOrNull { it.first.length } ?: 0

        logger.info(Component.empty())
        for ((line, fact) in layout(artFor(consoleEncoding(), art), facts)) {
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
         * Pairs each fact with the art line it sits beside, blank-padding once the art runs out.
         *
         * Iterating the *art* is what this replaces: a plugin adding two facts of its own has more
         * facts than art, and every one past the last line was dropped — including the "Started in"
         * row the framework appends itself, so the loss was invisible from inside the plugin.
         *
         * Separate from [show] for the same reason [artFor] is: it is the part with a decision in
         * it, and it is reachable here without a logger or a console.
         */
        internal fun layout(art: List<String>, facts: List<Pair<String, String>>): List<Pair<String, Pair<String, String>?>> {
            val blank = " ".repeat(art.first().length)
            return (0 until maxOf(art.size, facts.size)).map { index ->
                (art.getOrNull(index) ?: blank) to facts.getOrNull(index)
            }
        }

        /**
         * The art the console can actually draw.
         *
         * Separate from [show] so the choice is reachable without a logger and a real console —
         * the fallback only matters on setups nobody developing this has in front of them.
         */
        internal fun artFor(encoding: String, art: BannerArt = BannerArt.SCULK): List<String> {
            val renderable = runCatching {
                Charset.forName(encoding).newEncoder().canEncode(art.lines.joinToString(""))
            }.getOrDefault(false)
            return if (renderable) art.lines else art.fallback
        }
    }
}
