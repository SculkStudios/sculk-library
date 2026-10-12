# ADR-0018: Repository and branches

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Sculk 6 is a rewrite, not a refactor. The 5.x tree holds behaviour evidence, tests and changelog
history that the rewrite consults constantly, and 5.x may still need maintenance until 6.0 ships.

## Decision

One repository. `release/5.x` carries 5.x maintenance. `sculk-6` is the development branch and
replaces `main` at the first release candidate. The 6 tree is rebuilt in place: 5.x modules are
removed from the build once M1 lands and no 5.x source file is copied forward. History stays
reachable for reference.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| New repository | Loses issue history, tags and the audit trail; invites copying code across. |
| Rebuild module by module on `main` | Becomes the incremental refactor the directive rules out. |

## Consequences

Until the first RC, `main` is 5.x and must not receive 6 work. Commits on `sculk-6` reference the
milestone they belong to. The 5.x documentation site keeps deploying from `release/5.x` until the
6 portal replaces it.
