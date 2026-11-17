package studio.sculk.placeholder

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.execution.SculkScope
import studio.sculk.getOrNull
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeExecutor
import studio.sculk.text.Text
import java.time.Duration
import java.util.Locale

class PlaceholderRenderingTest {
    private val clock = FakeClock()
    private val failures = mutableListOf<Throwable>()
    private val scope = SculkScope.root("plugin", FakeExecutor()) { failures += it }
    private val placeholders = PlaceholderRegistry(scope, clock)
    private val text = Text()
    private val steve = Viewer("Steve")
    private val alex = Viewer("Alex")

    @Test
    fun `a global placeholder renders as its tag in any template`() {
        placeholders.global("server:online") { 42 }
        assertEquals("Online: 42", text.render("Online: <server_online>", placeholders.resolver()).plain())
        assertEquals("Online: 42", text.render("Online: <server_online>", placeholders.resolver(steve)).plain())
    }

    @Test
    fun `an audience placeholder renders the value of the audience the template is rendered for`() {
        val balances = mapOf(steve.uniqueId to 120, alex.uniqueId to 7)
        placeholders.audience("shop:balance") { balances[uniqueId] }
        assertEquals("Balance: 120", text.render("Balance: <shop_balance>", placeholders.resolver(steve)).plain())
        assertEquals("Balance: 7", text.render("Balance: <shop_balance>", placeholders.resolver(alex)).plain())
    }

    @Test
    fun `an audience placeholder in a template rendered without an audience is left as written`() {
        placeholders.audience("shop:balance") { 120 }
        assertEquals("Balance: <shop_balance>", text.render("Balance: <shop_balance>", placeholders.resolver()).plain())
    }

    @Test
    fun `a relational placeholder sees the viewer and the target, and audience placeholders describe the target`() {
        placeholders.audience("player:name") { (audience as Viewer).name }
        placeholders.relational("teams:relation") { if (viewer == target) "you" else "seen by $viewer" }
        val nametag = text.render("<player_name> (<teams_relation>)", placeholders.resolver(viewer = steve, target = alex))
        assertEquals("Alex (seen by Steve)", nametag.plain())
        assertEquals("<teams_relation>", text.render("<teams_relation>", placeholders.resolver(steve)).plain())
    }

    @Test
    fun `markup in a value is shown as text, never parsed`() {
        val hostile = "<red>rich</red><click:run_command:'/op Steve'>click</click><reset>"
        placeholders.audience("player:nickname") { hostile }
        val line = text.render("<primary>Hi <player_nickname>!", placeholders.resolver(steve))
        assertEquals("Hi $hostile!", line.plain())
        assertTrue(line.descendants().none { it.clickEvent() != null }, "a value never becomes a click event")
    }

    @Test
    fun `markup in an argument reaches the placeholder as text`() {
        placeholders.global("debug:echo", arguments = listOf(ECHOED)) { this[ECHOED] }
        assertEquals("<red>loud", text.render("<debug_echo:'<red>loud'>", placeholders.resolver()).plain())
    }

    @Test
    fun `a component the placeholder builds is inserted as built`() {
        val badge = Component.text("VIP", NamedTextColor.GOLD).clickEvent(ClickEvent.runCommand("/vip"))
        placeholders.audience("shop:badge") { badge }
        val line = text.render("<shop_badge> Steve", placeholders.resolver(steve))
        assertEquals("VIP Steve", line.plain())
        assertTrue(line.descendants().any { it == badge })
    }

    @Test
    fun `a format shows a typed value while reads keep the type`() {
        val balance = placeholders.audience("shop:balance", format = { Component.text("$%.2f".format(Locale.ROOT, it)) }) { 12.5 }
        assertEquals("$12.50", text.render("<shop_balance>", placeholders.resolver(steve)).plain())
        assertEquals(12.5, balance.value(steve).getOrNull())
    }

    @Test
    fun `a null value shows as nothing`() {
        placeholders.audience<String>("shop:title") { null }
        assertEquals("[]", text.render("[<shop_title>]", placeholders.resolver(steve)).plain())
    }

    @Test
    fun `placeholders render inside theme styles`() {
        placeholders.global("server:online") { 3 }
        assertEquals("3 online", text.render("<primary><server_online> online</primary>", placeholders.resolver()).plain())
    }

    @Test
    fun `a resolver that throws leaves its tag as written, the rest of the line renders, and the failure is reported`() {
        placeholders.global("server:online") { 3 }
        placeholders.audience<Int>("shop:balance") { error("database down") }
        val line = text.render("<server_online> online, balance <shop_balance>", placeholders.resolver(steve))
        assertEquals("3 online, balance <shop_balance>", line.plain())
        val reported = failures.single()
        assertTrue(reported.message.orEmpty().contains("<shop_balance>"), "the report names the tag: ${reported.message}")
        assertEquals("database down", reported.cause?.message)
    }

    @Test
    fun `a format that throws leaves its tag as written too`() {
        placeholders.audience<Int>("shop:balance", format = { error("bad format") }) { 12 }
        assertEquals("[<shop_balance>]", text.render("[<shop_balance>]", placeholders.resolver(steve)).plain())
        assertEquals("bad format", failures.single().cause?.message)
    }

    @Test
    fun `a placeholder that keeps failing is reported once a minute with the count held back`() {
        placeholders.global<Int>("server:tps") { error("no tps") }
        repeat(20) { text.render("<server_tps>", placeholders.resolver()) }
        assertEquals(1, failures.size, "renders within a minute of the first report are held back")
        clock.advance(Duration.ofMinutes(1))
        text.render("<server_tps>", placeholders.resolver())
        assertEquals(2, failures.size)
        val second = failures.last().message.orEmpty()
        assertTrue(second.contains("held back"), "the second report counts the others: $second")
    }

    private fun Component.descendants(): Sequence<Component> = sequenceOf(this) + children().asSequence().flatMap { it.descendants() }

    private companion object {
        val ECHOED = PlaceholderArgument.text("value")
    }
}
