# ADR-0017: No Sculk 5 compatibility facade

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

The directive asked whether a `sculk-compat-v5` module forwarding popular 5.x APIs into 6 should
exist. Sculk 5 was pre-public: 5.1.0 never reached JitPack and no release was published to a
public repository, so the only consumers are Sculk Studios' own plugins. A facade must track the
old surface precisely and would hold the new architecture to shapes it is replacing.

## Decision

Ship no compatibility facade. Provide `MIGRATION_5_TO_6.md` with a mapping for every renamed or
removed public symbol and a `sculkMigrate` Gradle task that detects 5.x imports and prints the
replacement. Migrate the in-house plugins as the M7 pilots.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Full forwarding facade | Constrains the 6 API to 5 shapes for an audience of one organisation. |
| Keep the 5.x engine alive inside 6 | Two implementations of everything; defeats the rewrite. |

## Consequences

Every 5.x consumer performs a source migration. The migration guide and analyzer are release
blockers. Revisit only if a public 5.x user base appears before 6.0.
