# ADR-0021: Configuration codec on snakeyaml-engine

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: ADR-0011

## Context

ADR-0011 chose a snakeyaml-engine node tree for comment preservation and kotaml, the maintained fork
of the archived kaml, to decode that tree into kotlinx-serializable types. Building it showed that
kotaml parses YAML itself, with its own node types, so pairing it with snakeyaml-engine meant two
parses of every file and a conversion between two trees, and it kept a small, young fork on the
dependency path of every Sculk plugin.

## Decision

Sculk decodes and encodes kotlinx-serializable types directly against snakeyaml-engine's node tree
with its own `Decoder` and `Encoder`, using only stable kotlinx.serialization API (polymorphism goes
through the public type-then-value protocol). One parse yields the tree, the comments, and the line
of every node. Keys are kebab-case; sealed types are chosen by a `type` key; validation annotations
are checked as values are decoded; `${NAME}` placeholders resolve from the environment and are kept
as written when the file is saved. Everything else in ADR-0011 stands: tree merge on save, versioned
migrations with a backup, transactional reload, YAML only in 6.0.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| kotaml over a second parse | Two parses, two node models, a young fork as a transitive dependency. |
| Configurate | No YAML comment round-trip in any release; `kotlin-reflect` for data classes. |

## Consequences

`sculk-config` depends only on kotlinx-serialization and snakeyaml-engine (Apache-2.0, no
dependencies). The decoder and encoder are Sculk's to maintain and are covered by tests that read
and write real files. Error messages are exact: file, dotted path, line, expected, found.
