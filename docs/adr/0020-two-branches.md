# ADR-0020: Two branches, main and dev

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: ADR-0018

## Context

ADR-0018 introduced `release/5.x` for 5.x maintenance and `sculk-6` for the rewrite. In practice the
repository also carried old release branches, merged feature branches and a queue of Renovate
pull requests against 5.x, which made it hard to see where work was happening. `release/5.x` was
identical to `main`.

## Decision

The repository has two long-lived branches. `main` holds the released 5.x line until 6.0 ships.
`dev` holds the Sculk 6 rewrite and is where all current work lands; it becomes `main` at the first
release candidate. Short-lived topic branches are deleted when merged. Released versions are
recorded as tags, not branches. Renovate targets `dev` only and opens a pull request only after it
is approved on the dependency dashboard.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep `release/5.x` beside `main` | Duplicates `main` exactly until 6.0. |
| Rewrite directly on `main` | Leaves no buildable 5.x line while the rewrite is incomplete. |

## Consequences

5.x fixes, if any are needed before 6.0, go to `main` and are tagged. CI runs on both branches.
