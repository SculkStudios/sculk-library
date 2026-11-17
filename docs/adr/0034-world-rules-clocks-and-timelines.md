# ADR-0034: Game rules, world clocks and timelines in sculk-world

**Status**: Proposed · **Date**: 2026-11-17 · **Supersedes**: —

## Context

`sculk-world` is planned in `MODULES.md` for batches, chunk tickets, world keys, borders, ray
tracing and world lifecycle. Its first slice is the world state plugins most often read and set:
game rules and time. The jar scans (`paper-api.json`) and `javap` on every anchor show three API
generations:

- **1.21.4**: `org.bukkit.GameRule` is a final class of 53 constants named in camelCase
  (`doDaylightCycle`), looked up by `GameRule.getByName`.
- **1.21.11 to 26.3**: game rules are a registry (`RegistryKey.GAME_RULE`, `org.bukkit.GameRules`,
  `GameRuleKeys`), 59 rules keyed in snake case (`minecraft:advance_time`). The rule set is identical
  across these anchors. `GameRule` became an abstract `Keyed` class and its old constants are bridges:
  three are inverted (`disableRaids` against `raids`) and `doFireTick` maps onto the integer
  `fire_spread_radius_around_player`. `getByName` now parses a namespaced key and returns null for an
  old name. `spawnChunkRadius` is gone; seven rules are new.
- **26.3**: world clocks and timelines (`WorldClock`, `Timeline`, their registries, and `World`
  methods for a clock's ticks, pause and rate). 26.1.2 and 26.2 have `ClockTimeSkipEvent` but no clock
  type. Timeline time markers are visible only to a registry builder at bootstrap.

Every version shares the typed `World.getGameRuleValue`, `setGameRule`, `getGameRuleDefault` and
`isGameRule`, and `WorldGameRuleChangeEvent`, which Paper fires for `/gamerule` and for `setGameRule`
alike (read from `CraftWorld` and `CraftEventFactory` of the 1.21.4 and 26.3 servers). On Folia 26.3
`setGameRule` and the legacy time setters require the global region thread; the clock setters do not
check, though clocks tick with the global region. Minecraft itself reads game rules and day time from
every region thread. A rule whose feature flag is off (`max_minecart_speed` without the minecart
experiment) throws on read on 1.21.4 and 26.3 alike.

## Decision

- **One key type, named by registry key.** `GameRuleKey<T>` carries a registry key and a
  `GameRuleType` (`BOOLEAN` or `INTEGER`); 59 constants cover 26.3. `parse` accepts a key with or
  without namespace and every 1.21.4 name, so configuration written for either era resolves.
- **A name table, as `sculk-registry` does for renamed keys.** `gamerules.txt` in the jar pairs each
  key with its 1.21.4 name, marks `inverted` and `fire-radius` rows, and lists the rules each side
  lacks; lookups read it both ways. The pairs are those Paper's own legacy constants bridge. The
  registry alias data does not fit: 1.21.4 has no game rule registry to alias in. A unit test fails
  when a vanilla key has no row.
- **Main code only, on the floor API.** Naming is chosen once by probing for `org.bukkit.GameRules`;
  every call then goes through the typed `World` methods every anchor has. On 1.21.4 an inverted rule
  is negated and the fire radius reads -1 (everywhere) or 0 (nowhere); any other radius there fails
  with `UnsupportedCapability`, as does a rule 1.21.4 lacks or a rule switched off by its flag.
- **Clocks behind an adapter.** `WorldTime` reaches clocks and timelines through a `ClockAdapter`
  compiled against the newest anchor (ADR-0028), instantiated by name after probing for
  `io.papermc.paper.world.WorldClock`. Elsewhere every call returns `UnsupportedCapability`. Clocks
  are `ClockKey`, `ClockState`, `TimelineKey`, `Timeline` and `TimelinePosition`: small immutable
  values with public constructors, so plugin tests can build them.
- **Writes on `Global`, reads anywhere.** `set` and `update` suspend and hop to the global region
  through `withGlobal`, running inline when already there; `setNow` and `updateNow` require the
  caller to be on it and throw otherwise, Sculk enforcing what Folia does not. Reads run on any
  thread, as the game's own do.
- **Failures are results.** An unknown key or a value the server rejects is `InvalidInput`; a rule,
  value or clock this server cannot have is `UnsupportedCapability`; a listener's veto is
  `ChangeCancelled`. Clock writes return the state read back, because `ClockTimeSkipEvent` listeners
  may change the jump.
- **Changes are observed through Paper's event.** `WorldRules.onChange` listens at `MONITOR` to
  `WorldGameRuleChangeEvent` and hands over a typed `GameRuleChange`; it runs before the world stores
  the value, so the value comes from the change.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Expose `org.bukkit.GameRule` and let callers pick legacy or registry constants | Every caller writes the version check the capability model removes, and the inverted rules invert silently. |
| Rely on Paper's legacy bridge constants everywhere | They exist only for the 1.21.4 names, miss the seven newer rules, and map `doFireTick` true to a radius of 128. |
| Put the game rule names in `sculk-registry`'s `aliases.txt` | That table aliases keys within one registry; 1.21.4 names are not registry keys, and the conversions have no place there. |
| An adapter source set for game rules | The typed `World` methods and `getByName` link on every anchor; only the names differ, which data handles. |
| Emulate clocks on 26.2 and below with `World.setTime` | One day counter is not a set of clocks with rates, pauses and timelines; pretending would be `Degraded` at best with no caller asking for it. |
| A fake in `sculk-testkit` behind a `WorldRules` interface | The API is concrete over Bukkit `World`, like `Registries`; its types are constructible values, and the server suite covers what a fake could not model faithfully. Revisit when `sculk-testkit-paper` gains a fake world. |

## Consequences

Plugins name rules once and run on every anchor. The table must gain a row whenever a Minecraft
release renames or adds a rule; the unit test catches a missing vanilla constant only, so a new
anchor in `gradle/servers.json` also needs its rules compared, which the `world` suite does by
reading every vanilla rule on every server. `integration-tests/world` proves reads, writes, the
1.21.4 translations against Paper's own constants, change events, thread hops and clocks on every
Paper anchor, Folia and Canvas, and `UnsupportedCapability` where clocks are absent. Revisit when
Paper exposes timeline markers at runtime, when a rule gains a third value type, or when the floor
moves past 1.21.4, which retires the table.
