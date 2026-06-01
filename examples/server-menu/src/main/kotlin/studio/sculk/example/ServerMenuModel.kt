package studio.sculk.example

import kotlin.math.ceil

public object ServerMenuModel {
    public val mainSlots: Map<String, Int> =
        mapOf(
            "warps" to 10,
            "profile" to 12,
            "players" to 14,
            "settings" to 16,
        )

    public fun pageCount(entries: Int, pageSize: Int): Int =
        ceil(entries.coerceAtLeast(0).toDouble() / pageSize.coerceAtLeast(1).toDouble()).toInt().coerceAtLeast(1)

    public fun hasPlayers(count: Int): Boolean = count > 0
}
