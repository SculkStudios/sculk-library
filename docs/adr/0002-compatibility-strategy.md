# ADR-0002: Compatibility strategy for Paper 1.21.4 to 26.3

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

The supported range spans two version formats (`1.21.x`, `26.x`), two Java generations, two
Adventure majors, and API that moved from experimental to stable mid-line (26.2 build 100) or was
added mid-line (removal reasons at 26.2 build 109). Jar scans of six anchors
(`docs/research/b-paper-capability-map.md`) show that version thresholds would be wrong for several
capabilities and that class and method presence is the reliable signal. Sculk 5 already probed for
data-component fields after a `NoSuchFieldError` on 1.21.4 broke every configured item.

## Decision

Compatibility is capability-based. Application modules consume capabilities with four states
(`Native`, `Emulated`, `Degraded`, `Unavailable`). Adapters are selected once at startup by
probing class and method presence through `MethodHandles`, keyed by data generated at build time
from ASM scans of pinned anchor `paper-api` jars. The floor is Paper 1.21.4; CI anchors are
1.21.4, 1.21.11, 26.1.2, 26.2 and 26.3, pinned by build number. Shared Adventure-facing modules
compile against the floor Adventure and are verified by a second compilation against Adventure
5.2. Behaviour is proven on real servers, not by symbol presence.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Version comparisons at call sites | Wrong across mid-line backports; spreads through every module; the pattern the directive forbids. |
| Floor at Paper 1.21 or 1.21.1 | No data components, `MenuType` or `TAGS`; `Attribute` still an enum; EOL since March 2025; three fewer registry providers. The cost falls on every module for servers that no longer receive Paper updates. |
| One Adventure line (5.2 only) | Drops every Java 21 server. |
| Hand-maintained compatibility tables | Rots on each Paper release; the generator costs one task and stays correct. |
| No generator (workstream E1 suggests this for a 1.21.11 floor) | With a 1.21.4 floor, the jar scans already found mid-line backports and enum-to-interface changes that a hand table would miss; the generator also feeds the docs badges. |

## Consequences

`sculk-core` owns `MinecraftVersion` and the capability API; platform modules own adapters per
anchor. The build gains a compatibility generator task and per-adapter experimental opt-ins. The
docs manifest gets `minecraft.native` and `fallback` fields for free. Twelve open questions in the
capability map need live-server answers before the affected capabilities are marked `Native`.
Revisit the floor when Paper 1.21.x host share drops or when Paper converts `ItemStack` to an
interface, which the class-kind scan will flag.
