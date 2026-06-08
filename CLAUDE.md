# Sculk Studio — Contributor & AI Guide

Sculk Studio is a **Kotlin-first Paper (Minecraft) plugin framework**: coroutine-native, modular,
Adventure-only. Target: Paper 26.1.2+, Java 25, Kotlin 2.x. Version 4.0 (pre-public).

## Module map

Each docs section maps 1:1 to a module. Packages are `studio.sculk.<area>` (flat — no `.core.`).

| Module | Package | Purpose |
| --- | --- | --- |
| `sculk-common` | `studio.sculk`, `.annotation`, `.coroutine`, `.scheduler`, `.version` | Base: `SculkResult`/`SculkHandle`, coroutine scope + dispatchers, scheduler contract, version parsing, stability annotations |
| `sculk-adventure` | `studio.sculk.adventure` | MiniMessage messaging helpers + templates |
| `sculk-commands` | `studio.sculk.command` | Brigadier-native command DSL, arguments, cooldowns |
| `sculk-gui` | `studio.sculk.gui` | Chest/container GUI menus, animations, pagination |
| `sculk-events` | `studio.sculk.event` | Coroutine-friendly event bus |
| `sculk-config` | `studio.sculk.config` | Typed YAML config, validation, hot reload, env-var substitution |
| `sculk-series` | `studio.sculk.series` | Registry lookups (Material/Sound/Particle), aliases |
| `sculk-items` | `studio.sculk.items` | Data-component item builders, PDC, skulls, descriptors |
| `sculk-effects` | `studio.sculk.effects` | Particle/sound builders, animation timelines |
| `sculk-data` | `studio.sculk.data` | Suspend repositories, query DSL, transactions, Caffeine + Redis cache |
| `sculk-text` | `studio.sculk.text` | Per-player localization, bundles, pluralization |
| `sculk-tasks` | `studio.sculk.tasks` | Cron, repeating/delayed tasks, debounce/throttle |
| `sculk-integrations` | `studio.sculk.integrations` | Optional PlaceholderAPI / Vault / LuckPerms adapters |
| `sculk-packets-api` (+ `-packetevents`, `-protocollib`) | `studio.sculk.packets` | Backend-neutral packet API + optional backends |
| `sculk-content` | `studio.sculk.content` | High-level client-side block helpers over packets |
| `sculk-platform` | `studio.sculk.platform` | Paper bootstrap, `SculkPlugin` base class, wires everything |
| `sculk-bom` | — | Version BOM for à-la-carte consumers |

### Dependency order (acyclic, flat)

```
common ← adventure, items, series, config, events, tasks, integrations, packets-api
commands ← common, adventure
gui      ← common, adventure, items
effects  ← common, series
data     ← common, config
text     ← common, adventure, config
content  ← common, packets-api
platform ← (re-exports everything)
```

A module must depend **only on what it uses**. `sculk-platform` `api`-re-exports all modules so the
one-line install surfaces the whole DSL; consumers may instead pick individual modules.

## Non-negotiable rules

1. **Kotlin-first, Java-equal** — Kotlin DSLs are the primary authoring surface, but every
   `@SculkStable` public member must also be *first-class from Java*. That means: companion
   factories carry `@JvmStatic`; public functions with default arguments carry `@JvmOverloads`
   (or explicit overloads); Kotlin function-type / `suspend` parameters get a sibling overload
   taking `java.util.function.*` (`Consumer`/`Function`/`Predicate`/`Supplier`/`Runnable`). Parity
   is achieved by annotating and overloading the *same* Kotlin API — **never** by parallel Java
   `*Builder` classes. The Kotlin call site must stay unchanged.
2. **Adventure/MiniMessage only** — never legacy color codes (`&c`, `§c`).
3. **Coroutines first, with Java bridges** — IO/DB stays `suspend` for Kotlin callers. Any
   *value-returning* `suspend` public API must also expose a Java bridge: a
   `CompletableFuture`-returning overload (preferred) or a `Consumer<SculkResult<T>>` callback,
   marked clearly and documented with the thread it resolves on. The `CompletableFuture.await()`
   bridge still covers Paper APIs that hand Kotlin callers a future.
4. **Folia-correct** — route timing through `SculkScheduler` / `SculkCoroutineScope`; document the
   thread a callback runs on.
5. **Stability markers required** — every public type/member carries `@SculkStable`,
   `@SculkExperimental`, or `@SculkInternal`.
6. **`SculkResult<T>` for fallible ops**; throw only for programmer errors.
7. **ktlint + explicit API** — code must pass `./gradlew ktlintCheck` and `explicitApi()` (max line 140).
8. **KDoc on public members**, with a short usage example where it helps.

## How to…

- **Add a feature:** put it in the smallest fitting module (or a new leaf module added to
  `settings.gradle.kts` + re-exported by `sculk-platform`). Mark public types `@SculkStable`, KDoc
  them, add tests, add a `docs/src/content/docs/<section>/<page>.mdx` page, wire it into the sidebar in
  `docs/astro.config.mjs`.
- **Keep Java parity (rule #1):** any new `@SculkStable` member needs a Java path. Receiver blocks
  (`X.() -> Unit`), `() -> Unit`, `(T) -> Unit`, and `suspend` params need a sibling overload taking
  `Consumer`/`Runnable`/`Predicate`. **Value-returning** plain functions (`(T) -> R`) are already
  Java-callable via lambda/method-ref — do **not** add a `Function<T,R>` overload (it makes the call
  ambiguous from Java). Companion factories → `@JvmStatic`; default args → `@JvmOverloads`; top-level
  DSL functions → a `@file:JvmName("Sculk…")` facade. Add the API to
  `examples/java-basic-plugin` (the Java compile gate).
- **Add docs:** MDX with `title`/`description` frontmatter; show the Kotlin snippet and a Java tab via
  `<Tabs syncKey="lang">` so the choice syncs site-wide; lead with the `SculkPlugin` bootstrap. Java
  builder lambdas use block bodies (`x -> { … }`).
- **Fix a bug:** add a failing test first; keep the change behavior-scoped.

## Release checklist

1. `./gradlew build` green (all modules + examples incl. `java-basic-plugin` + tests + ktlint).
2. `cd docs && bun run build` green.
3. Version bumped in `build.gradle.kts`; `CHANGELOG.md` entry dated; `sculk-bom` lists every module.
4. `git diff origin/main...HEAD` reviewed; renovate PRs triaged/closed.
5. Tag `vX.Y.Z` and draft GitHub release notes from the CHANGELOG.

## Commands

```
./gradlew build           # compile + test + ktlint, all modules + examples
./gradlew test            # tests only
./gradlew ktlintFormat    # auto-fix style
cd docs && bun run build  # build the docs site   (bun run deploy to publish)
```

## Layout

`sculk-*` library modules · `examples/*` (showcase plugins, not published) · `benchmarks` (JMH) ·
`build-logic` (convention plugins) · `docs` (Astro Starlight).
