# Changelog

All notable changes to Sculk Studio are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [4.5.0] — "Modern Core + Java Parity" — 2026-06-06

A major, breaking modernisation around Kotlin coroutines and the latest Paper APIs, shipped together
with a first-class Java surface so Java plugin authors get the same ergonomics as Kotlin. See the
[4.0 → 4.5 migration guide](https://docs.sculk.studio/advanced/migration-to-sculk-4-5/) and the
[Java getting-started guide](https://docs.sculk.studio/getting-started/java/).

> This release supersedes the never-shipped `4.0.0 "Modern Core"` line: it folds in that work plus
> the dependency updates (Kotlin 2.4, kotlinx-coroutines 1.11, kotlinx-serialization 1.11, Lettuce 7,
> PacketEvents 2.12, Caffeine 3.2, MiniMessage 5.1, JUnit 6) and the new Java-parity layer.

### Added — Java parity (new in 4.5.0)
- **Kotlin stays first-class; Java is now equal.** Every `@SculkStable` entry point is callable from
  idiomatic Java with no `Unit.INSTANCE`, no `.Companion.`, and no `Function1`.
- `@JvmStatic` on stable companion factories (`SculkPlatform.create`, `SculkConfig.create`,
  `SculkData.create`/`withDataSource`, `SculkText.create`, `SoundBuilder.of`/`ofKey`) and on the
  `SculkSeries` registry lookups.
- `java.util.function.*` overloads (`Consumer`/`Function`/`Predicate`/`Runnable`/`BiConsumer`)
  alongside every Kotlin DSL block and `suspend` fire-and-forget API — commands, events, tasks, GUI,
  items, effects, text, packets, platform bootstrap.
- `@JvmOverloads` on stable APIs with default arguments, so Java skips optional parameters.
- `Class<T>` overloads for the `inline reified` APIs (`SculkEventBus.listen`/`once`,
  `SculkConfig.load`/`reload`, `SculkData.repository`, `CommandContext.argument`, `GuiState.get`,
  `CommandBuilder.enum`).
- `SculkData.transactionAsync(...)` — a `CompletableFuture` bridge for the value-returning
  `suspend transaction`.
- The whole `suspend` data layer is usable from Java via `CompletableFuture` bridges:
  `SculkData.javaRepository(...)` (`JavaRepository`), `SculkData.javaCache(...)` (`JavaCache` —
  repository ops plus `findOrCreate`/`findTopBy`/`invalidate`/`invalidateAll`), and
  `SculkData.javaPlayerProfiles(...)` (`JavaProfileStore` — `getOrCreate`/`save`).
- `SculkResult` gained Java-first member methods (`isSuccess()`, `isFailure()`, `getOrNull()`,
  `getOrThrow()`, `getOrDefault(x)`, `ifSuccess(...)`, `ifFailure(...)`) so Java no longer calls the
  `SculkResultKt` extensions.
- `SculkPlugin` gains `Consumer<SculkPlatformBuilder>` and no-arg constructors so Java plugins can
  `extends SculkPlugin`.
- Clean Java facades for top-level DSL functions via `@JvmName`: `SculkCommands`, `SculkGui`,
  `SculkItems`, `SculkSounds`, `SculkParticles`, `SculkMessenger`, `SculkBlocks`.
- New `examples/java-basic-plugin` — a full plugin written entirely in Java, compiled by CI as the
  Java parity gate.

### Added — Sculk blocks
- `SculkBlocks` helpers (`isSculkBlock`, `SCULK_BLOCKS`, typed `sculkSensorAt`/`sculkCatalystAt`/
  `sculkShriekerAt`/`calibratedSculkSensorAt` state accessors).

### Added
- **Coroutine core**: plugin-scoped `SculkCoroutineScope` (`sculk.scope`) with Folia-aware
  `main`/`async` dispatchers, `launchMain`/`launchAsync`/`withMain`/`withAsync`, and a
  `CompletableFuture.await()` bridge.
- **Brigadier commands**: the `command { }` DSL now compiles to Paper's native Brigadier tree
  (client-side completion & error highlighting). Suspend executors, per-node `middleware { }`, and a
  stateful `CooldownStore`.
- **Data components**: `ItemBuilder` rewritten on the data-component API with `itemName`, `rarity`,
  `maxStackSize`, `maxDamage`, `food`, typed/nested `pdc`, and a generic `component`/`unsetComponent`
  escape hatch.
- **Suspend data layer**: suspend repositories, a type-safe `repo.query { }` DSL, `data.transaction { }`,
  and a distributed `RedisCache` backend (Lettuce + kotlinx.serialization) behind the unified
  `SculkCache` interface.
- **GUI**: coroutine-driven animated slots, per-click-type handlers (`onLeftClick`/`onRightClick`/
  `onShiftClick`), interactive input slots, non-chest container GUIs, and `session.renderSlot`.
- **`sculk-text`**: per-player localization with YAML bundles, MiniMessage templates, placeholders,
  and pluralization (`sculk.text`).
- **`sculk-tasks`**: coroutine scheduling with cron expressions, repeating/delayed tasks, and
  debounce/throttle helpers (`sculk.tasks`).
- **Config**: environment-variable substitution (`${VAR:-default}`) and file-watch auto-reload
  (`config.watch { }`).

### Changed
- `SculkRepository` methods are now `suspend`. The old `AsyncRepository` / `CompletableFuture`
  facade has been removed.
- Commands must be registered during `onEnable` (Brigadier lifecycle).

### Removed
- The legacy parallel Java builder classes (`Java*Builder`). Java parity in 4.5.0 is delivered by
  annotating and overloading the *same* Kotlin API (`@JvmStatic`/`@JvmOverloads`/`java.util.function`
  overloads) — not by duplicate Java types.

---

## [1.0.0] — Unreleased

### Added

#### Core
- Command DSL (`command { }`) with nested subcommands, typed arguments, sender-type routing, and auto tab-complete
- Built-in argument types: `string`, `int`, `long`, `double`, `boolean`, `player`, `choice`, `greedy`
- Custom `ArgumentParser<T>` for plugin-defined argument types
- `argumentOrNull<T>()` for optional arguments
- GUI DSL (`gui { }`) with per-player `GuiSession`, click routing, and automatic cleanup
- Paginated GUIs via `pagination { }` + `session.setEntries()`, `nextPage()`, `previousPage()`, `setPage(n)`
- `dynamicContent { player -> }` for per-player item rendering
- `GuiState` per-session key-value store with `session.state["key"]` and typed `session.state.get<T>("key")`
- `SculkResult<T>` sealed type with `map`, `flatMap`, `fold`, `onSuccess`, `onFailure`, `recover`, `getOrNull`, `getOrDefault`
- `SculkMessenger` Adventure extension functions: `reply`, `title`, `actionbar`, `playSound`, `parseMessage`
- Scheduler DSL: `runSync`, `runAsync`, `runLater`, `runRepeating`, `runAsyncRepeating`
- Event DSL: `sculk.events.listen<T> { }` with automatic cleanup on `sculk.close()`
- `SculkHandle` lifecycle contract — all subsystems implement `close()`
- `@SculkStable` / `@SculkExperimental` / `@SculkInternal` stability annotations

#### Config (`sculk-config`)
- `@ConfigFile` data-class configs with YAML serialization and default generation
- Field validation annotations: `@Min`, `@Max`, `@NotEmpty`
- Hot-reload support via `sculk.config.onReload<T> { }`
- `reloadAll()` — reloads all registered config files from disk

#### Data (`sculk-data`)
- `SculkRepository<T, ID>` repository pattern over SQLite and MySQL
- Async JDBC execution — never blocks the main thread
- Caffeine cache layer via `SculkCache<K, V>`
- `JavaRepository.wrap(repo)` — `CompletableFuture`-based Java API

#### Series (`sculk-series`)
- `SculkSeries` registry mapping for cross-version `Material`, `Sound`, and `Particle` names

#### Effects (`sculk-effects`)
- `ParticleBuilder` and `SoundBuilder` DSLs
- `AnimationTimeline` and `AnimationSequence` for tick-accurate effect scheduling

#### Platform (`sculk-platform`)
- `SculkPlatform.create(plugin) { }` — single-line Paper bootstrap
- `JavaSculkPlatform.create(plugin, cfg -> ...)` — Java bootstrap
- Full lifecycle management: all subsystems close cleanly on `sculk.close()`

#### Java compatibility
- `JavaCommandBuilder` — fluent Java builder for the command DSL
- `JavaGui` / `JavaGuiBuilder` / `JavaGuiItemBuilder` — fluent Java builders for the GUI DSL
- `JavaSculkPlatform` — Java bootstrap
- `JavaRepository` — `CompletableFuture` repository wrapper
- Every public Kotlin DSL has a paired Java builder in a `*.java` sub-package

### Requirements
- Paper 1.21.11+
- Java 21+
- Kotlin 2.x (for Kotlin plugins)
