# ADR-0005: Execution ownership

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

On Folia and Canvas there is no main thread. Chunks, blocks and entities belong to region threads
keyed by region section; the global region owns world-wide state; scoreboards are unsupported on
Folia; synchronous teleport throws. Paper exposes the same four schedulers (global, region, entity,
async) with real retire semantics, so one code path serves all three.

Sculk 5 got the scheduler contract right (abstract entity and location overloads) but defaulted
its coroutine scope to the async dispatcher, so command executors ran off-thread silently; ran HUD,
hologram, nametag and menu-animation work for every player from the global task; and ran the task
body inside Paper's retired callback, where world access is forbidden
(`docs/research/a-sculk5-audit.md` §7, `docs/research/c-folia-canvas.md` §1.2). FoliaLib and
MorePaperLib stop at choosing a scheduler and map "sync" to the global region.

## Decision

Model execution as ownership: `Global`, `Region` (keyed by section), `Entity`, `Async`, `Proxy`,
with `Current` as an assertion. Expose it as suspend functions (`withEntity`, `withRegion`,
`withGlobal`, `withAsync`), coroutine dispatchers and tick schedulers. Entity work returns
`Ran | EntityGone`; the retired callback only completes that outcome. Teleport is a suspend
operation on an entity scope that resumes on the entity's dispatcher. Sculk tracks every task in a
`ResourceScope` because the platform cannot cancel region or entity tasks. The rules in
`THREADING.md` are the contract; a development-mode checker and a fake ownership runtime in the test
kit enforce them without a server; twelve scenarios on real Folia and Canvas builds prove them.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep sync/async with "sync" meaning global | Correct on Paper, a data race on Folia for every entity and block operation. |
| Wrap a third-party scheduler library | None models ownership as a value, entity-gone outcomes, teleport re-entry, cancellation for region tasks or section partitioning. MorePaperLib is LGPL-3.0. |
| Infer ownership from an entity's location | Wrong across teleports and region merges; Paper says entity ownership must be checked on the entity. |

## Consequences

Every world-touching API in every module states the context it requires (`@Requires`). The test kit
can fail a unit test for a Folia bug. Some APIs become suspend functions where 5.x returned
immediately. Region batching and the Canvas regionized-data façade build on the section model.
Velocity uses the same vocabulary with only `Proxy` and `Async` available.
