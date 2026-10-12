# ADR-0023: Packet backends and scoreboard surfaces on Folia

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: ADR-0012

## Context

ADR-0012 chose PacketEvents as the primary packet backend, ProtocolLib as best effort, both
`compileOnly`, and promised that holograms, client blocks and HUD work with no packet plugin
installed. On Folia that promise cannot hold for scoreboard surfaces: `registerNewObjective`,
`registerNewTeam`, `clearSlot`, `ScoreboardManager#getNewScoreboard` and `setPlayerBoard` throw
`UnsupportedOperationException`; only reads of the main board work
(`docs/research/c-folia-canvas.md` §1.1, §1.5). THREADING.md rule 12 said presentation surfaces
on Folia are therefore packet-backed, but `sculk-presentation` has no packet dependency, and PacketEvents is
GPL-3.0, so Sculk cannot bundle it to close the gap. Canvas restores scoreboards (writes on
`Global`, membership on the entity), and Paper allows them anywhere.

PacketEvents releases are published to CodeMC
(`https://repo.codemc.io/repository/maven-releases/`, 2.14.0 on 2026-10-12), not to Maven Central:
`com.github.retrooper` has no artifacts there. ProtocolLib 5.4.0 is on Maven Central as
`net.dmulloy2:ProtocolLib`; its 26.x support exists only as development builds.

## Decision

The packet strategy of ADR-0012 stands: a small backend-neutral `sculk-packets-api`, PacketEvents
primary, ProtocolLib best effort while its tests pass, one guard for every backend's handlers,
backends `compileOnly` and never shaded. Packets are used only where the platform API cannot do
the job.

Scoreboard surfaces are split by what each platform can do without packets:

| Surface | Paper, Canvas | Folia without a packet backend | Folia with `sculk-presentation-packets` |
| --- | --- | --- | --- |
| Action bar, boss bar, titles, tab header and footer, tab-list names | `Native` | `Native` | `Native` |
| Sidebar, below-name, nametag teams | `Native` (scoreboard API, leased) | `Unavailable` | `Emulated` (per-viewer objective and team packets) |

`sculk-presentation-packets` depends on `sculk-presentation` and `sculk-packets-api`; the bytes
come from whichever backend module the operator's server satisfies. It is never a dependency of
`sculk-presentation`, so a plugin that does not opt in carries no packet code. Callers branch on the
capability or receive `UnsupportedCapability`; Sculk never silently drops a surface.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Packet-backed surfaces inside `sculk-presentation` | Forces a GPL packet plugin on every Folia server for every HUD user; breaks module boundaries. |
| Bundle PacketEvents | GPL-3.0; Sculk artifacts stay MIT (`LICENSING.md`). |
| Emulate the sidebar with boss bars or action bars on Folia | Different surface, different semantics; a capability must not pretend. |
| Our own protocol layer | Rebuilds PacketEvents' per-release maintenance. |

## Consequences

THREADING.md rule 12 and `MODULES.md` change accordingly. Folia integration tests cover both rows
of the table, with and without a backend installed. PacketEvents' open Folia crash (#1458) means
the emulated row is declared only after it passes on Folia. Build repositories for
`sculk-packets-packetevents` use CodeMC through `exclusiveContent`, never a wildcard.
