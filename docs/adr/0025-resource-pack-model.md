# ADR-0025: Resource-pack model: own, shaped after creative

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

ADR-0013 gives the Gradle plugin a pack compiler that validates plugin assets, generates typed
keys, emits glyph metrics and writes a deterministic zip. That compiler needs an in-memory model of
a resource pack plus a reader and a writer. `team.unnamed:creative` (MIT) is the established Java
library for exactly that, so it was assessed before writing one (GitHub and Maven Central,
2026-10-12):

| Fact | Finding |
| --- | --- |
| Latest release | 1.7.3, 2024-07-02, on Maven Central. It predates the 1.21.4 item model definitions (`assets/<ns>/items/`) that the floor requires. |
| `main` branch | 1.8.4-SNAPSHOT. Has 1.21.4+ item models, equipment layers, waypoint styles and `major.minor` pack formats. |
| Publishing | Configured for Sonatype OSSRH, which was retired on 2025-06-30. No 1.8.x artifact was found on Central or on `repo.unnamed.team`. |
| Activity | Community pull requests merged sporadically (December 2025, May 2026, July 2026); 19 open issues; no release in 27 months. |

## Decision

Sculk owns a narrow pack model inside `sculk-gradle-plugin`: only the asset kinds ADR-0013
validates (item model definitions, models, textures, fonts and glyph providers, sounds, pack
metadata with `major.minor` formats from 1.21.4 to 26.3). Its types are shaped after creative's
so that switching later is mechanical; any code ported from creative keeps its MIT notice. The
runtime module `sculk-resourcepack` does not need a pack model at all: it sends packs through
Adventure's `ResourcePackRequest`.

Revisit when creative publishes a Central release that covers every pack format from 1.21.4 to
26.3. At that point the reader and writer are the replaceable part, and depending on a maintained
MIT library beats maintaining our own.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Depend on creative 1.7.3 | Cannot model 1.21.4 item model definitions, so it fails the floor. |
| Depend on a creative snapshot or our own fork | No published snapshot; a fork would make Sculk the de facto maintainer of a general-purpose library. |
| Vendor creative's `main` branch | Same maintenance burden as a fork, hidden inside our tree. |

## Consequences

The pack model is Sculk's to maintain for each Minecraft drop, limited to what the compiler
validates; pack-format tables come from the compatibility generator's anchors. Golden-file tests
cover reading and writing for every anchor's pack format. The status of creative is rechecked at
each Sculk minor release and recorded in `docs/research/e2-presentation-gui-config-data-packets.md`.
