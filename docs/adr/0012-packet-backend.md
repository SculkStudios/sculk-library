# ADR-0012: Packet backend strategy

**Status**: Superseded by ADR-0023 · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Some features have no Paper API: zero-tick cross-chunk fake entities, per-player nametag text
without scoreboard-team collisions, fake tab-list entries, chunk rewriting and inbound packet
interception. Others look like they need packets but do not: per-player display entities
(`setVisibleByDefault(false)` + `showEntity`), client block changes (`sendMultiBlockChange`), and
HUD through Adventure audiences.

PacketEvents 2.14.0 shipped 26.3 support eight days after the Minecraft release and has kept that
cadence for every drop since 1.21.9. ProtocolLib has had no stable release since 5.4.0 in August
2025; 26.x support exists only as a rolling development build. PacketEvents is GPL-3.0 and
ProtocolLib GPL-2.0, which are mutually incompatible. Sculk 5's packet guard claimed to cover
every backend and did not cover ProtocolLib. Evidence: `docs/research/e2-presentation-gui-config-data-packets.md` §E.

## Decision

Keep a small backend-neutral `sculk-packets-api`. PacketEvents is the primary backend. ProtocolLib
is best effort: maintained while its tests pass on the anchor matrix and dropped from a release
otherwise. Every feature that Paper can serve has a packet-free implementation on Paper first, so
holograms, client blocks and HUD work with no packet plugin installed; packets are used only where
the API cannot do the job. Every backend dispatches handlers through one guard. Backends are
`compileOnly`, never shaded, per `LICENSING.md`.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Packets first for visuals | Measures nothing, adds a GPL plugin dependency for features Paper serves. |
| Drop ProtocolLib | Many servers still run it; keeping it best effort costs little while tests pass. |
| Our own protocol layer or NMS | Rebuilds PacketEvents' maintenance burden for every Minecraft drop. |
| Depend on EntityLib | GPL-3.0, alpha, not on Maven Central. Copy its named-field metadata design instead. |

## Consequences

`VirtualEntityService` gains a Paper display-entity implementation and a packet implementation;
scenes choose per node, and the choice is benchmarked rather than assumed. PacketEvents' open
Folia crash (#1458) means the packet backend is integration-tested on Folia before Sculk declares
Folia support for any packet feature. Packet handlers run under the dev-mode blocking detector.
