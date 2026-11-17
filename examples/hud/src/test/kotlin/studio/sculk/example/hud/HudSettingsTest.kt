package studio.sculk.example.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration

class HudSettingsTest {
    private val ranks = HudSettings().ranks

    @Test
    fun `a player has the highest rank they hold a permission for, and the last rank otherwise`() {
        assertEquals("vip", rankOf(ranks) { it == "hud.rank.vip" || it == "hud.rank.member" }?.name)
        assertEquals("admin", rankOf(ranks) { true }?.name)
        assertEquals("member", rankOf(ranks) { false }?.name)
        assertNull(rankOf(emptyList()) { true })
    }

    @Test
    fun `the countdown shows minutes and seconds and says when the event has ended`() {
        assertEquals("29:05", countdown(Duration.ofMinutes(29).plusSeconds(5)))
        assertEquals("0:09", countdown(Duration.ofSeconds(9)))
        assertEquals("ended", countdown(Duration.ZERO))
        assertEquals("ended", countdown(Duration.ofSeconds(-3)))
    }
}
