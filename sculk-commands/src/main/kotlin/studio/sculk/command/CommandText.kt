package studio.sculk.command

import studio.sculk.annotation.SculkStable

/**
 * The messages the command framework itself emits.
 *
 * A data class rather than constants so a server can retheme or translate them. Previously these
 * were hardcoded English inside the Brigadier compiler, which meant a fully localised plugin still
 * said "This command can only be used by players." in English.
 *
 * Placeholders are inserted unparsed, so `<usage>` and `<time>` are safe to feed anything.
 */
@SculkStable
public data class CommandText(
    public val playerOnly: String = "<danger>This command can only be used by players.",
    public val consoleOnly: String = "<danger>This command can only be used from the console.",
    public val noPermission: String = "<danger>You do not have permission to do that.",
    public val onCooldown: String = "<danger>Wait <value><time></value> before using that again.",
    public val badUsage: String = "<danger>Usage: <value><usage></value>",
    public val unknownValue: String = "<danger>Unknown <value><type></value>: <value><input></value>",
    public val failed: String = "<danger>Something went wrong running that command.",
) {
    public companion object {
        @SculkStable
        public val DEFAULT: CommandText = CommandText()
    }
}

/**
 * The `/help` wording, kept separate because a server is far more likely to restyle its help output
 * than its error messages.
 */
@SculkStable
public data class HelpText(
    public val header: String = "<value>Commands</value> <dim>(page <page>/<pages>)</dim>",
    public val entry: String = "<value><usage></value> <dim>-</dim> <description>",
    public val entryNoDescription: String = "<value><usage></value>",
    public val empty: String = "<dim>There is nothing here you can run.</dim>",
    public val footer: String = "",
) {
    public companion object {
        @SculkStable
        public val DEFAULT: HelpText = HelpText()
    }
}
