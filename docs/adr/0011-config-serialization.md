# ADR-0011: Configuration serialisation

**Status**: Superseded by ADR-0021 · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Typed configuration on kotlinx.serialization is a cornerstone and must keep working without
`kotlin-reflect`. Operators edit the files by hand, so a save must keep their comments and any
keys the current schema does not know. Sculk 5 achieved that with a text-level, append-only merge
over kaml output; it held up but is fragile for multi-line scalars and flow collections, cannot
remove or relocate keys, and drifts from declaration order.

Research (`docs/research/e2-presentation-gui-config-data-packets.md` §C) found: kaml was archived
on 2025-11-30 at the exact version Sculk 5 pins, and kotaml is the maintained fork now listed by
kotlinx.serialization; Configurate's released YAML loader discards per-node comments (the fixing
pull requests have been open since 2020 and 2023) and needs `kotlin-reflect` for data classes;
snakeyaml-engine 3.x parses and emits comments on its node tree. No library in either family
preserves user comments and unknown keys while decoding into typed Kotlin.

## Decision

Build the hybrid natively. Parse with snakeyaml-engine (`parseComments = true`) into a node tree.
Decode the typed value from that tree through kotaml so errors carry path, line and column. On
save, encode the value to a fresh tree and merge it into the original per path: replace scalar
values, keep the operator's comments where they exist and use `@Comment` text where they do not,
keep unknown subtrees, append new defaults. Emit with comments. Migrations are versioned
transformations over the node tree before decoding. Reload is transactional: a candidate that
fails to parse, migrate or validate never replaces the active value. YAML is the only format in
6.0; JSON is trivial to add, TOML and HOCON are out until a library preserves comments.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep kaml | Archived upstream. |
| Configurate | No YAML comment round-trip in any release; `kotlin-reflect` for data classes; no sealed or polymorphic support. |
| Keep the text-level merge on kotaml | Works today, but carries the fragility that produced three second-boot bugs in 5.x. |
| Fork kaml ourselves | kotaml is that fork, maintained monthly; owning another is cost without benefit. |

## Consequences

`sculk-config` depends on snakeyaml-engine (Apache-2.0, 0 dependencies) and kotaml (Apache-2.0).
Paper exposes classic snakeyaml 2.2 and Velocity 2.5; snakeyaml-engine is a different package and
does not conflict. The tree merge needs a thorough round-trip test corpus built from the Sculk 5
config tests and every second-boot bug in the 5.x changelog. kotaml is a small project; if it
stalls, decoding moves to a direct kotlinx `Decoder` over the snakeyaml-engine tree, which this
design already isolates.
