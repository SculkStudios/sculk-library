# ADR-0026: Placeholders as typed tag resolvers with refresh policies

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Sculk 6 promises placeholders that are typed, safe and reactive (`MODULES.md`, `PERFORMANCE.md`
"Invalidate, do not poll"). The two ecosystems a server already runs fall short of that
(`docs/research/e2-presentation-gui-config-data-packets.md` §A): PlaceholderAPI expansions return
legacy strings, keep no result cache and are GPL-3.0; MiniPlaceholders is Component-native and sits
inside MiniMessage, but is pull-only, with no invalidation or change notification. TAB shows what
HUDs need instead: a refresh interval per placeholder, asynchronous refresh, and work only for
placeholders in use. On Folia every region thread renders at once, so whatever placeholders keep must
be thread-safe, and a slow value must never be computed on a tick thread.

## Decision

`sculk-placeholders` is a per-plugin `PlaceholderRegistry` owned by a `SculkScope`:

- A key is `namespace:name` and its tag is `<namespace_name>`. Namespaces may not contain `_`, so a
  tag maps to one key; a key that would hide a MiniMessage tag is refused; a duplicate key throws.
- Three kinds, by who a value is for: global, audience (per `Audience`, identified by its
  `Identity.UUID` pointer, so the same code runs on Paper and Velocity) and relational (viewer and
  target). Contextual placeholders are not a fourth kind: any kind declares typed, positional
  arguments (`<stats_stat:kills>`), validated before the resolver runs. A tag with invalid arguments,
  or one whose audience the render lacks, is left as written.
- `resolver()`, `resolver(audience)` and `resolver(viewer, target)` return `TagResolver`s for
  `Text.render`. In the relational form audience placeholders describe the target, as a nametag or a
  chat line does.
- A placeholder is typed (`SculkPlaceholder<T>`): its value is kept as `T` so code can read it, and
  shown through a format. By default a component is inserted as built and anything else as its
  string form, unparsed; arguments reach resolvers as strings. Nothing a placeholder produces is
  parsed as MiniMessage.
- `Refresh` is `Live` (computed every render), `Every(interval)` or `UntilInvalidated`. Synchronous
  resolvers run on the rendering thread. `*Async` placeholders load in `ExecutionContext.Async` with
  stale-while-revalidate: a render never waits, it shows the last value or `initial` and starts at most
  one load per value; a failed load is reported, keeps the last value and backs off five seconds.
- Kept values are bounded per placeholder (least recently used out) under one short lock that no
  resolver ever runs inside. A load or compute that started before an invalidation never counts as
  fresh.
- Change signals are a synchronous listener (`onChange`) receiving `Everyone`, `Audience(uuid)` or
  `Relation(viewer, target)`: on invalidation, and when a recomputed or loaded value differs from the
  one it replaces. `invalidateWith(source)` links derived placeholders into an acyclic graph.

Bridges are not in this module. `sculk-miniplaceholders` and `sculk-placeholderapi` iterate
`registered`, map each kind onto the other system's global, audience or relational hook, and call
`value(...)` and `render(...)`; the reverse direction wraps an external resolver as a Sculk placeholder
whose output is a value, not markup (`SECURITY.md`).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Build on MiniPlaceholders' expansions | Pull-only, so no refresh policy or change signal; a hard dependency on another plugin. |
| PlaceholderAPI-style `%id%` strings | Legacy colours, string replacement into templates, which is the injection Sculk forbids. |
| A `Flow` of changes | A bounded buffer drops signals under load and an unbounded one breaks the cache rule; a listener that marks surfaces dirty loses nothing. |
| Resolvers that always suspend | A render on a tick thread cannot wait; splitting sync and async makes the blocking choice visible at registration. |
| Timers per value for `Every` | One task per value; surfaces already re-render on their own interval, which `refresh` tells them. |

## Consequences

`sculk-presentation` gets per-value change signals and each placeholder's interval, so it can redraw
only dirty lines. Authors must choose async for anything slow; a synchronous resolver that blocks
is a review finding. An audience without a UUID (the console) is cached under its own identity and
signalled as `Everyone`. Revisit if MiniPlaceholders gains invalidation, or if a global registry
shared between plugins becomes necessary without a bridge.
