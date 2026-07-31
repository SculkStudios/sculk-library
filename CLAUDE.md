# Sculk Studio — Contributor & AI Guide

Sculk Studio is a **Kotlin-only Paper (Minecraft) plugin framework**: coroutine-friendly, modular,
Adventure-only. Target: Paper 1.21.11+, Java 21, Kotlin 2.x. Version 5.0 (pre-public).

## Module map

Each docs section maps 1:1 to a module. Packages are `studio.sculk.<area>` (flat — no `.core.`).

| Module | Package | Purpose |
| --- | --- | --- |
| `sculk-common` | `studio.sculk`, `.annotation`, `.coroutine`, `.io`, `.scheduler`, `.task`, `.version` | `SculkResult`/`SculkHandle`, coroutine scope, scheduler contract + `FakeScheduler` fixture, cron/repeating/batch tasks, directory watching |
| `sculk-text` | `studio.sculk.text` | Theme, MiniMessage rendering, glyph-width measurement, per-player localisation |
| `sculk-config` | `studio.sculk.config` | Typed YAML on kotlinx-serialization, generated defaults, validation, migrations |
| `sculk-series` | `studio.sculk.series` | Registry lookups (Material/Sound/Particle), aliases |
| `sculk-items` | `studio.sculk.items` | Data-component item builders, PDC, descriptors, dyes |
| `sculk-commands` | `studio.sculk.command` | `CommandSpec` as data, one Brigadier adapter, generated help |
| `sculk-gui` | `studio.sculk.gui` | Chest/container menus, animations, pagination |
| `sculk-data` | `studio.sculk.data` | Suspend repositories, query DSL, schema migration, Caffeine + Redis/Valkey cache |
| `sculk-hud` | `studio.sculk.hud` | Sidebar, action bar, tab list, boss bars — one driver task |
| `sculk-visual` | `studio.sculk.visual` | Particles, sounds, timelines, packet-only holograms, nametags |
| `sculk-integrations` | `studio.sculk.integrations` | Optional PlaceholderAPI / Vault / LuckPerms adapters |
| `sculk-packets-api` (+ `-packetevents`, `-protocollib`) | `studio.sculk.packets` | Backend-neutral packet API, client blocks, virtual entities |
| `sculk-platform` | `studio.sculk.platform` | `SculkPlugin`, `SculkPlatform`, `ServiceRegistry`, events, scheduler impl |
| `sculk-bom` | — | Version BOM, generated from the subproject list |

### Dependency order (acyclic, flat)

```
common          ← (nothing)              + coroutines
config          ← common                 + serialization, kaml
text            ← common                 + kaml
series          ← common
items           ← common, text, series
commands        ← common, text
gui             ← common, text, items
data            ← common, config
packets-api     ← common
packets-*       ← packets-api
visual          ← common, series, text, packets-api
hud             ← common, text
integrations    ← common
platform        ← everything (api)
```

A module depends **only on what it uses**. Two edges are load-bearing and easy to break:

- **`sculk-text` must not depend on `sculk-config`.** It would drag the config system onto the
  compile classpath of gui, items and commands. The bundle loader uses kaml directly.
- **`sculk-visual` must not depend on a packet backend.** Holograms go through
  `VirtualEntityService` in `sculk-packets-api`. Importing PacketEvents there is what previously
  made ProtocolLib unable to serve holograms at all.

## Non-negotiable rules

1. **Kotlin-only.** No `@JvmStatic`, `@JvmOverloads`, `@JvmName`, no `Consumer`/`Predicate`
   overloads, no `CompletableFuture` bridges. Reified generics, default arguments, `suspend` and
   receiver lambdas are the API surface. The single carve-out is
   `suspend fun <T> CompletableFuture<T>.await()`, which exists because Paper hands *Kotlin*
   callers futures.
2. **Adventure/MiniMessage only** — never legacy colour codes (`&c`, `§c`).
3. **The trust boundary is absolute.** A *template* is trusted and parsed. A *value* is not, and
   goes in through `Placeholder.unparsed` via `SculkMessages`. Never substitute a value into a
   template with `replace()` before rendering — that is a markup-injection hole, it has been
   introduced twice, and both times it looked like ordinary string handling.
4. **`SculkResult<T>` for fallible I/O and user input.** Nullable only where absence is normal and
   the caller cannot act on a reason. Throw only for programmer error — a wiring bug that should
   surface at boot. Never return a `SculkResult` that cannot fail.
5. **Folia-correct** — route timing through `SculkScheduler`; document the thread a callback runs
   on. The region overloads are abstract on purpose: an implementation that has not thought about
   regions must not compile.
6. **Stability markers** — every public type/member carries `@SculkStable`, `@SculkExperimental`,
   or `@SculkInternal`.
7. **ktlint + explicit API + `allWarningsAsErrors`** — `./gradlew build` must be green (max line 140).
8. **Committed `.api` dumps.** Any change to the public surface must show up in a `.api` diff.
   Run `./gradlew apiDump` and commit it in the same change.

## How to write it

- **Comments explain *why* a non-obvious decision was made, and name the consequence of the
  alternative.** Do not narrate what the code does. No banner comments. No KDoc that restates its
  own identifier (`/** The stack size. */ var amount`). There is no target ratio — a percentage
  produces deleted-but-useful documentation.
- **Every module that hands out an interface ships a fake for it** in `src/testFixtures`.
  `FakeScheduler` and `FakeVirtualEntityService` are why gui, hud, visual and data test with no
  server. If you add an interface consumers implement against, add its fake.
- **Test names are sentences describing the invariant**, e.g.
  ``fun `a still centred row is redrawn when the widest row moved`()``.
- **Prefer a real engine to a mock.** The data layer runs against SQLite and H2 in MySQL mode,
  because a string-compared query is a query nobody has parsed.

## How to…

- **Add a feature:** smallest fitting module. Mark public types, KDoc them, add tests, add a
  `docs/src/content/docs/<section>/<page>.mdx` page, wire it into `docs/astro.config.mjs`.
- **Change the public API:** `./gradlew apiDump` and commit the diff.
- **Fix a bug:** add a failing test first; keep the change behaviour-scoped.

## Release checklist

1. `./gradlew build` green (all modules + examples + tests + ktlint).
2. `./gradlew apiCheck` green.
3. `cd docs && bun run build` green.
4. `grep -r 'kotlin("reflect")' */build.gradle.kts` returns nothing.
5. Version bumped in `build.gradle.kts`; `CHANGELOG.md` entry dated.
6. Tag `vX.Y.Z` and draft release notes from the CHANGELOG.

## Commands

```
./gradlew build           # compile + test + ktlint, all modules + examples
./gradlew apiCheck        # public surface matches the committed dumps
./gradlew apiDump         # regenerate them after a deliberate change
./gradlew ktlintFormat    # auto-fix style
cd docs && bun run build  # build the docs site   (bun run deploy to publish)
```

The Postgres integration tests are skipped unless a server is named:

```
SCULK_POSTGRES_URL="jdbc:postgresql://localhost:5432/sculk?user=…&password=…" ./gradlew :sculk-data:test
```

## Layout

`sculk-*` library modules · `examples/*` (compile gates, not published) · `build-logic` (convention
plugins) · `docs` (Astro Starlight).
