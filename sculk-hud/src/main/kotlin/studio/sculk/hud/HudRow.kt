package studio.sculk.hud

import studio.sculk.annotation.SculkStable

/**
 * A HUD line: a trusted template plus the untrusted values it references.
 *
 * The two are kept apart all the way to the renderer rather than being substituted into a string
 * on the way. Folding them together first is the bug `SculkMessages` exists to prevent — a player
 * name or an item name reaching a template as text gets parsed as markup — and a sidebar is a
 * particularly good place for it to happen, because half of what a sidebar shows comes from
 * somewhere a player can influence.
 *
 * Keeping them apart is also what makes change detection correct: the signature covers the
 * *resolved values*, so a row of `Balance: <coins>` is redrawn when the balance changes, where
 * comparing templates alone would draw it once and freeze the number for the session.
 */
@SculkStable
public data class HudRow(public val template: String, public val values: List<Pair<String, String>> = emptyList()) {
    internal val signature: String get() = sidebarSignature(template, values.map { it.second })

    internal val centred: Boolean get() = template.startsWith("<center>")

    internal val body: String get() = template.removePrefix("<center>")
}
