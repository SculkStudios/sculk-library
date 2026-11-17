@file:OptIn(SculkExperimental::class)

package studio.sculk.itest.world

import net.kyori.adventure.key.Key
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.command.ConsoleCommandSender
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.execution.delayTicks
import studio.sculk.execution.withAsync
import studio.sculk.getOrNull
import studio.sculk.getOrThrow
import studio.sculk.paper.SculkPaperPlugin
import studio.sculk.world.ClockKey
import studio.sculk.world.GameRuleChange
import studio.sculk.world.GameRuleKey
import studio.sculk.world.TimelineKey
import studio.sculk.world.WorldRules
import studio.sculk.world.WorldTime
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListMap
import kotlin.coroutines.cancellation.CancellationException

/**
 * Reads and sets game rules through their registry keys, checks each against Paper's own legacy
 * constants, and uses world clocks where the server has them. Writes `results.properties` and stops
 * the server.
 */
class WorldCheckPlugin : SculkPaperPlugin() {
    private val results = ConcurrentSkipListMap<String, String>()
    private val observed = ConcurrentLinkedQueue<GameRuleChange<Boolean>>()

    override suspend fun start() {
        WorldRules.onChange(sculk.events, GameRuleKey.KEEP_INVENTORY) { observed += it }
        sculk.scope.launch {
            delayTicks(20)
            try {
                check(server.worlds.first(), legacy = server.minecraftVersion == "1.21.4")
            } finally {
                dataFolder.mkdirs()
                val lines = results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" }
                File(dataFolder, "results.properties").writeText(lines)
                server.shutdown()
            }
        }
    }

    private suspend fun check(world: World, legacy: Boolean) {
        verdict("vanilla-rules-read-on-every-version") {
            val newer = setOf(
                GameRuleKey.ALLOW_ENTERING_NETHER_USING_PORTALS,
                GameRuleKey.COMMAND_BLOCKS_WORK,
                GameRuleKey.LOCATOR_BAR,
                GameRuleKey.PVP,
                GameRuleKey.SPAWN_MONSTERS,
                GameRuleKey.SPAWNER_BLOCKS_WORK,
                GameRuleKey.TNT_EXPLODES,
            )
            val wrong = GameRuleKey.vanilla.mapNotNull { rule ->
                val result = WorldRules.get(world, rule)
                val ok = when {
                    legacy && rule in newer -> result is SculkFailure.UnsupportedCapability
                    // Switched off unless the minecart experiment is on, on every version.
                    rule == GameRuleKey.MAX_MINECART_SPEED -> result is SculkResult.Success || result is SculkFailure.UnsupportedCapability
                    else -> result is SculkResult.Success
                }
                if (ok) null else "$rule gave $result"
            }
            (WorldRules.registryBacked == !legacy && wrong.isEmpty()) to "registry-backed ${WorldRules.registryBacked}, wrong: $wrong"
        }
        verdict("renamed-rules-reach-papers-own-constants") {
            val byOldName = GameRuleKey.parse("doDaylightCycle").getOrNull()
            val byKey = WorldRules.resolve("minecraft:advance_time").getOrNull()
            WorldRules.set(world, GameRuleKey.ADVANCE_TIME, false).getOrThrow()
            val off = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)
            WorldRules.set(world, GameRuleKey.ADVANCE_TIME, true).getOrThrow()
            val on = world.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE)
            val same = byOldName == GameRuleKey.ADVANCE_TIME && byKey == GameRuleKey.ADVANCE_TIME
            (same && off == false && on == true) to "doDaylightCycle finds $byOldName, the key finds $byKey, Paper read $off then $on"
        }
        verdict("inverted-rules-keep-their-meaning") {
            WorldRules.set(world, GameRuleKey.RAIDS, false).getOrThrow()
            val disabled = world.getGameRuleValue(GameRule.DISABLE_RAIDS)
            val read = WorldRules.get(world, GameRuleKey.RAIDS).getOrThrow()
            WorldRules.set(world, GameRuleKey.RAIDS, true).getOrThrow()
            val restored = world.getGameRuleValue(GameRule.DISABLE_RAIDS)
            (disabled == true && !read && restored == false) to "raids false read $read, disableRaids $disabled then $restored"
        }
        verdict("fire-radius-on-every-version") {
            val rule = GameRuleKey.FIRE_SPREAD_RADIUS_AROUND_PLAYER
            val default = WorldRules.default(world, rule).getOrThrow()
            val none = WorldRules.set(world, rule, 0).getOrThrow()
            val noFire = world.getGameRuleValue(GameRule.DO_FIRE_TICK)
            val everywhere = WorldRules.set(world, rule, -1).getOrThrow()
            val fire = world.getGameRuleValue(GameRule.DO_FIRE_TICK)
            val radius = WorldRules.set(world, rule, 128)
            val radiusOk = if (legacy) radius is SculkFailure.UnsupportedCapability else radius.getOrNull() == 128
            WorldRules.set(world, rule, default).getOrThrow()
            (none == 0 && noFire == false && everywhere == -1 && fire == true && radiusOk) to
                "0 gave $none (doFireTick $noFire), -1 gave $everywhere (doFireTick $fire), 128 gave $radius"
        }
        verdict("defaults-are-vanillas") {
            val keep = WorldRules.default(world, GameRuleKey.KEEP_INVENTORY).getOrThrow()
            val tick = WorldRules.default(world, GameRuleKey.RANDOM_TICK_SPEED).getOrThrow()
            val time = WorldRules.default(world, GameRuleKey.ADVANCE_TIME).getOrThrow()
            (!keep && tick == 3 && time) to "keep_inventory $keep, random_tick_speed $tick, advance_time $time"
        }
        verdict("out-of-range-values-are-refused") {
            val result = WorldRules.set(world, GameRuleKey.RANDOM_TICK_SPEED, -1)
            val read = WorldRules.get(world, GameRuleKey.RANDOM_TICK_SPEED).getOrNull()
            WorldRules.set(world, GameRuleKey.RANDOM_TICK_SPEED, 3).getOrThrow()
            // Rules gained ranges with the registry; 1.21.4 stores any integer.
            val ok = if (legacy) result.getOrNull() == -1 else result is SculkFailure.InvalidInput && read == 3
            ok to "-1 gave $result, then read $read"
        }
        verdict("changes-are-observed") {
            observed.clear()
            WorldRules.set(world, GameRuleKey.KEEP_INVENTORY, true).getOrThrow()
            server.dispatchCommand(server.consoleSender, if (legacy) "gamerule keepInventory false" else "gamerule keep_inventory false")
            val seen = observed.toList()
            val byPlugin = seen.getOrNull(0)
            val byCommand = seen.getOrNull(1)
            val ok = seen.size == 2 &&
                byPlugin != null &&
                byCommand != null &&
                byPlugin.value &&
                byPlugin.sender == null &&
                !byCommand.value &&
                byCommand.sender is ConsoleCommandSender &&
                WorldRules.get(world, GameRuleKey.KEEP_INVENTORY).getOrNull() == false
            ok to "saw $seen"
        }
        verdict("set-hops-to-the-global-region") {
            val (offGlobal, result) = withAsync { !server.isGlobalTickThread to WorldRules.set(world, GameRuleKey.KEEP_INVENTORY, true) }
            val read = WorldRules.get(world, GameRuleKey.KEEP_INVENTORY).getOrNull()
            WorldRules.set(world, GameRuleKey.KEEP_INVENTORY, false).getOrThrow()
            (offGlobal && result.getOrNull() == true && read == true) to "called off the global region $offGlobal, gave $result, read $read"
        }
        verdict("set-now-refuses-other-threads") {
            val refused = withAsync { runCatching { WorldRules.setNow(world, GameRuleKey.KEEP_INVENTORY, true) }.exceptionOrNull() }
            val read = WorldRules.get(world, GameRuleKey.KEEP_INVENTORY).getOrNull()
            (refused is IllegalStateException && read == false) to "threw $refused, keep_inventory $read"
        }
        verdict("available-lists-the-servers-rules") {
            val available = WorldRules.available().toSet()
            val ok = if (legacy) {
                available.size == 52 && GameRuleKey.PVP !in available && GameRuleKey.ADVANCE_TIME in available
            } else {
                available.containsAll(GameRuleKey.vanilla)
            }
            ok to "${available.size} rules: $available"
        }
        checkClocks(world)
    }

    private suspend fun checkClocks(world: World) {
        // Folia 26.3 and Canvas 26.3 forked Paper before clocks were added: the API decides, not the version.
        val expected = runCatching { Class.forName("io.papermc.paper.world.WorldClock") }.isSuccess
        verdict("clocks-or-unsupported-capability") {
            if (!WorldTime.isAvailable) {
                val read = WorldTime.clock(world, ClockKey.OVERWORLD)
                val write = WorldTime.updateNow(world, ClockKey.OVERWORLD, paused = true)
                val timelines = WorldTime.timelines()
                val ok = !expected &&
                    read is SculkFailure.UnsupportedCapability &&
                    write is SculkFailure.UnsupportedCapability &&
                    timelines is SculkFailure.UnsupportedCapability
                return@verdict ok to "read $read, write $write, timelines $timelines"
            }
            val clocks = WorldTime.clocks().getOrThrow()
            val default = WorldTime.defaultClock(world).getOrThrow()
            val state = WorldTime.clock(world, ClockKey.OVERWORLD).getOrThrow()
            val unknown = WorldTime.clock(world, ClockKey(Key.key("sculk-itest:nope")))
            val nether = server.worlds.firstOrNull { it.environment == World.Environment.NETHER }
            val netherClock = nether?.let { WorldTime.defaultClock(it).getOrThrow() }
            val ok = expected &&
                clocks.containsAll(listOf(ClockKey.OVERWORLD, ClockKey.THE_END)) &&
                default == ClockKey.OVERWORLD &&
                state.ticks >= 0 &&
                state.rate > 0f &&
                unknown is SculkFailure.InvalidInput &&
                netherClock == null
            ok to "clocks $clocks, default $default, overworld $state, unknown $unknown, nether follows $netherClock"
        }
        if (!WorldTime.isAvailable) {
            results["clock-writes-hop-and-read-back"] = "SKIP: this server's API has no world clocks"
            results["timelines-follow-their-clock"] = "SKIP: this server's API has no timelines"
            return
        }
        verdict("clock-writes-hop-and-read-back") {
            val before = WorldTime.clock(world, ClockKey.OVERWORLD).getOrThrow()
            val target = before.ticks + 1_000
            val changed = withAsync { WorldTime.update(world, ClockKey.OVERWORLD, ticks = target, paused = true, rate = 2f) }.getOrThrow()
            val refused = withAsync { runCatching { WorldTime.updateNow(world, ClockKey.OVERWORLD, paused = false) }.exceptionOrNull() }
            val negative = WorldTime.updateNow(world, ClockKey.OVERWORLD, ticks = -1)
            val zeroRate = WorldTime.updateNow(world, ClockKey.OVERWORLD, rate = 0f)
            val restored = WorldTime.update(world, ClockKey.OVERWORLD, paused = false, rate = 1f).getOrThrow()
            val ok = changed.ticks == target &&
                changed.paused &&
                changed.rate == 2f &&
                refused is IllegalStateException &&
                negative is SculkFailure.InvalidInput &&
                zeroRate is SculkFailure.InvalidInput &&
                !restored.paused &&
                restored.rate == 1f
            ok to "changed $changed, off-thread $refused, negative $negative, zero rate $zeroRate, restored $restored"
        }
        verdict("timelines-follow-their-clock") {
            val all = WorldTime.timelines().getOrThrow()
            val day = WorldTime.timeline(TimelineKey.DAY).getOrThrow()
            val moon = all.firstOrNull { it.key == TimelineKey.MOON }
            val earlyGame = all.firstOrNull { it.key == TimelineKey.EARLY_GAME }
            val position = WorldTime.position(world, TimelineKey.DAY).getOrThrow()
            val ok = day.clock == ClockKey.OVERWORLD &&
                day.periodTicks == 24_000 &&
                moon?.periodTicks == 192_000 &&
                earlyGame != null &&
                earlyGame.periodTicks == null &&
                position.tick == Math.floorMod(position.clockTicks, 24_000L) &&
                WorldTime.timeline(TimelineKey(Key.key("sculk-itest:nope"))) is SculkFailure.InvalidInput
            // World.getTime is recorded beside the day position: how the two relate on 26.3 is observed, not asserted.
            ok to "day $day, moon $moon, early game $earlyGame, position $position, World.getTime ${world.time}"
        }
    }

    private suspend fun verdict(name: String, block: suspend () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS ($detail)" else "FAIL: $detail"
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }
}
