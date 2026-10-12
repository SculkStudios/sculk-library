package studio.sculk.placeholder

import net.kyori.adventure.text.Component
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.execution.SculkScope
import studio.sculk.getOrNull
import studio.sculk.testkit.FakeClock
import studio.sculk.testkit.FakeExecutor
import studio.sculk.text.Text
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/** The examples in docs/src/content/docs/guides/placeholders.mdx, compiled and run. */
class PlaceholderGuideTest {
    private val executor = FakeExecutor()
    private val clock = FakeClock()
    private val scope = SculkScope.root("shop", executor) {}
    private val placeholders = PlaceholderRegistry(scope, clock)
    private val text = Text()
    private val balances = ConcurrentHashMap<UUID, Double>()
    private val steve = Viewer("Steve")
    private val alex = Viewer("Alex")

    @Test
    fun `a balance renders per player and changes after a payment`() {
        balances[steve.uniqueId] = 120.0
        val balance = placeholders.audience(
            "shop:balance",
            Refresh.UntilInvalidated,
            format = { Component.text(String.format(Locale.ROOT, "%,.2f", it)) },
        ) { uniqueId?.let(balances::get) }

        val template = "<primary>Balance: <shop_balance>"
        assertEquals("Balance: 120.00", text.render(template, placeholders.resolver(steve)).plain())

        balances[steve.uniqueId] = 1_095.5
        balance.invalidate(steve)
        assertEquals("Balance: 1,095.50", text.render(template, placeholders.resolver(steve)).plain())
    }

    @Test
    fun `a nametag shows the target's rank and how the viewer relates to them`() {
        val teams = mapOf(steve.uniqueId to "red", alex.uniqueId to "blue")
        placeholders.audience("shop:rank") { if (uniqueId == steve.uniqueId) "Owner" else "Member" }
        placeholders.relational("teams:relation") { if (teams[viewerId] == teams[targetId]) "ally" else "enemy" }

        val nametag = text.render("[<shop_rank>] <teams_relation>", placeholders.resolver(viewer = alex, target = steve))
        assertEquals("[Owner] enemy", nametag.plain())
    }

    @Test
    fun `a statistic is chosen by the tag's argument`() {
        val stat = PlaceholderArgument.choice("stat", mapOf("kills" to 12, "deaths" to 3))
        placeholders.audience("stats:value", arguments = listOf(stat)) { this[stat] }

        val line = text.render("K <stats_value:kills> / D <stats_value:deaths> / <stats_value:assists>", placeholders.resolver(steve))
        assertEquals("K 12 / D 3 / <stats_value:assists>", line.plain())
    }

    @Test
    fun `a value loaded from a database never makes a render wait`() {
        var stored = 7
        placeholders.audienceAsync("shop:top", Refresh.Every(30.seconds), initial = "…") {
            stored.toString() // stands in for a suspending repository call
        }
        val resolver = placeholders.resolver(steve)

        assertEquals("Top …", text.render("Top <shop_top>", resolver).plain())
        executor.tick() // the load runs in the async context
        assertEquals("Top 7", text.render("Top <shop_top>", resolver).plain())

        stored = 3
        clock.advance(Duration.ofSeconds(30))
        assertEquals("Top 7", text.render("Top <shop_top>", resolver).plain()) // stale while it reloads
        executor.tick()
        assertEquals("Top 3", text.render("Top <shop_top>", resolver).plain())
    }

    @Test
    fun `a sidebar learns which players' lines changed`() {
        val dirty = ConcurrentHashMap.newKeySet<UUID>()
        placeholders.onChange { change ->
            when (change) {
                is PlaceholderChange.Audience -> dirty += change.uniqueId
                is PlaceholderChange.Relation -> dirty += change.viewer
                is PlaceholderChange.Everyone -> dirty += listOf(steve.uniqueId, alex.uniqueId)
            }
        }
        val balance = placeholders.audience("shop:balance", Refresh.UntilInvalidated) { uniqueId?.let(balances::get) ?: 0.0 }
        val wealth = placeholders.audience("shop:wealth", Refresh.UntilInvalidated) {
            if ((balance.value(audience).getOrNull() ?: 0.0) > 1_000) "rich" else "modest"
        }
        wealth.invalidateWith(balance)

        balances[steve.uniqueId] = 5_000.0
        balance.invalidate(steve)
        assertEquals(setOf(steve.uniqueId), dirty)
        assertEquals("rich", wealth.value(steve).getOrNull())
    }
}
