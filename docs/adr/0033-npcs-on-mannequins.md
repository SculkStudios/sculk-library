# ADR-0033: NPCs on Paper's mannequins

**Status**: Proposed · **Date**: 2026-11-17 · **Supersedes**: —

## Context

Plugins want player-shaped NPCs: a skin, a name, something to click. Until Minecraft 1.21.9 the
only ways were a fake `ServerPlayer` (internals, ADR-0007) or packets spoofing a player-info entry
and a player entity (ADR-0023's backends), and both break with every protocol change. 1.21.9 added
the mannequin, a real entity with a player model, and Paper exposes it as `org.bukkit.entity.Mannequin`:
profile, skin parts, main hand, the line under the name, an immovable flag and equipment. The jar
scans (`paper-api.json`) and `javap` of each anchor show the same `Mannequin` surface on 1.21.11,
26.1.2, 26.2 and 26.3 and none on 1.21.4; `ResolvableProfile` is `@ApiStatus.Experimental` until 26.2.
`CraftMannequin` behaves alike on all four (Folia adds its usual ownership check), and Paper's
`setDescription(null)` hides the line. `PaperCapabilities.MANNEQUINS` already probes the class.

Two constraints decide the design. An NPC must not outlive the plugin that made it, so nothing may
reach the world save. And on Folia and Canvas an entity belongs to a region: spawning, turning its
head and handling a click must happen on the owning thread, without a scan of all players.

## Decision

`sculk-npcs` spawns an immutable `NpcSpec` as a mannequin through an adapter compiled against the
newest anchor (ADR-0028), gated on `PaperCapabilities.MANNEQUINS`; where it is unavailable `spawn`
returns `UnsupportedCapability` and nothing is registered. Each mannequin is **not persistent**, so
it is never written to a chunk, and its chunk holds a plugin ticket (counted per chunk) while the NPC
lives, so it is not discarded by an unload. An NPC belongs to a `ResourceScope` and is removed when
that scope or the plugin's closes: inline when the closing thread owns it, otherwise on its next
tick. At a shutdown where no task can run, the entity is left to the server, which does not save it,
and Paper drops the plugin's tickets on disable. Spawning loads the chunk with `getChunkAtAsync` and
runs on the region that owns it (`withRegion`); head turning is a repeating task on the NPC's own
entity context that reads only the players tracking it and owned by the same thread; clicks arrive
through `PlayerInteractEntityEvent` and `PrePlayerAttackEntityEvent`, both cancelled, and the
handler runs on the player's thread. Names and descriptions are trusted templates rendered once
through `sculk-text` with unparsed values, the same for every viewer. A skin given by UUID is looked
up once per `Npcs` off every game thread and applied as a static profile; textures given directly
are shown from the first tick.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| A fake `ServerPlayer` added to the level | `net.minecraft` outside an internals module (ADR-0007), breaks per release, and leaks into player lists, sleeping and advancement checks. |
| Packets: player-info entry plus a spawned player entity | Needs `sculk-packets-api` (M5) and a backend; every version change moves the packets; Folia needs per-viewer state outside any region. Remains the route for 1.21.4 and for per-viewer names, in a later packet module. |
| Persistent mannequins found again on chunk load | The world keeps NPCs after the plugin is removed; matching saved entities back to specs needs stable ids and migrations. |
| No chunk ticket; respawn on chunk load | Needs per-chunk bookkeeping and leaves windows where the NPC is gone; a ticket per occupied chunk is simpler, and most NPCs stand where players already are. |
| One mannequin per viewer, hidden from others, for per-viewer names | Entity count grows with players times NPCs; per-viewer text is a packet concern. |
| Scanning all online players for look-at | A global scan touches players owned by other regions on Folia; the tracked-player set is already per entity. |

## Consequences

NPCs work on Paper 1.21.11 to 26.3, Folia and Canvas with Paper API only, and 1.21.4 reports
`UnsupportedCapability`. Every chunk with an NPC stays loaded and entity-ticking. A name is the same
for every viewer. A player skin needs a profile lookup, which an offline-mode server without a
proxy may not answer. Moving an NPC means despawning and spawning it again; touching
`Npc.entity` directly is an escape hatch that can strand its chunk ticket. `integration-tests/npcs`
proves spawning, clicks on the player's thread, head turning and removal on every anchor, Folia and
Canvas, and the failure on 1.21.4. Revisit when `sculk-packets-api` lands (per-viewer names, 1.21.4),
or if Paper adds per-viewer entity metadata.
