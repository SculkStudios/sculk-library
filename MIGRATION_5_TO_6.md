# Migrating from Sculk 5 to Sculk 6

Sculk 6 is a rewrite. There is no compatibility facade (ADR-0017). This guide maps every public
5.2.0 symbol to its 6 replacement and records behaviour that changed on purpose. The `sculkMigrate`
Gradle task detects 5.x imports in a consumer project and prints the rows below that apply.

Status: skeleton. Module rows are completed as each 6 module lands (M2 to M5). The 5.2.0 surface
is inventoried in `docs/research/a-sculk5-audit.md` §2.

## Modules

| Sculk 5 | Sculk 6 | Notes |
| --- | --- | --- |
| `sculk-common` | `sculk-core`, `sculk-coroutines` | Result, handles, version, tasks and clocks move to core; scheduler and coroutine scope are replaced by execution contexts and dispatchers. |
| `sculk-platform` | `sculk-paper` (+ `sculk-canvas`) | `SculkPlugin` becomes `SculkPaperPlugin`; `Sculk.attach(plugin)` is the primary entry point. |
| `sculk-text` | `sculk-text`, `sculk-placeholders` | Placeholders become a reactive, typed layer. |
| `sculk-config` | `sculk-config` | Same philosophy; transactional reload and layered sources added. |
| `sculk-series` | `sculk-registry` | Registry keys, tags and sets replace string lookups; aliases only where upstream renamed. |
| `sculk-items` | `sculk-items` | `ItemBuilder` becomes an immutable `ItemSpec`; typed PDC absorbed here. |
| `sculk-integrations` | `sculk-permissions`, `sculk-luckperms`, `sculk-vault`, `sculk-placeholderapi`, `sculk-miniplaceholders`, `sculk-nexo`, `sculk-oraxen`, `sculk-itemsadder` | One artifact per integration; typed, cached adapters. |
| `sculk-commands` | `sculk-commands`, `sculk-commands-paper`, `sculk-commands-velocity` | Same `CommandSpec` idea; the neutral module has no Brigadier dependency, and each platform module compiles specs through that platform's own Brigadier API with native argument types (ADR-0022). KSP frontend. |
| `sculk-gui` | `sculk-inventory` | State-driven, diff-rendered menus; typed native views. |
| `sculk-hud` | `sculk-presentation` | Render graph with update policies; tab list, nametags and below-name join sidebar and boss bars. |
| `sculk-visual` | `sculk-scenes`, `sculk-effects` | Scene graph over display entities; animation engine; particle shapes. Holograms and nametags become scene nodes and presentation surfaces. |
| `sculk-data` | `sculk-data`, `sculk-data-*`, `sculk-cache`, `sculk-cache-*` | Drivers and caches split from the core; Caffeine and Valkey become backends. |
| `sculk-packets-*` | `sculk-packets-*` | Same names; `PacketGuard` applies to every backend. |
| `sculk-discord`, `sculk-discord-jda` | same | Ported under the 6 lifecycle, error model and testkit. |
| `sculk-web`, `sculk-web-ktor` | same | Documented for the first time. |
| `sculk-bom` | `sculk-bom`, `sculk-catalog` | Version catalog added. |

## Symbols

Filled per module. Format: 5 symbol → 6 symbol, with a before/after snippet where the shape changed.

### Core

| Sculk 5 | Sculk 6 |
| --- | --- |
| `SculkResult.Failure(message, cause)` | `SculkResult.Failure` becomes a sealed hierarchy (`UnsupportedCapability`, `InvalidConfig`, `NetworkTimeout`, `DatabaseFailure`, `MissingIntegration`, `InvalidResource`, …); `message` remains a sentence. |
| `SculkHandle` | `SculkHandle`, owned by a `ResourceScope`. |
| `SculkScheduler.runSync(entity, task)` and friends | `withEntity(entity) { }`, `withRegion(location) { }`, `withGlobal { }`, `withAsync { }` on the runtime; tick scheduling through `sculk.tasks`. |
| `SculkCoroutineScope.launchMain / launchAsync` | `sculk.scope.launch(sculk.dispatchers.global)` and `.async`; the default dispatcher is no longer async. |
| `SculkTasks.repeating / delayed / cron / debounce / throttle` | Same names on `sculk.tasks`, with entity and region overloads. |
| `MinecraftVersion` | `MinecraftVersion` in `sculk-core`, now aware of the `26.x` format. |

### Platform

| Sculk 5 | Sculk 6 |
| --- | --- |
| `class MyPlugin : SculkPlugin() { override fun setup() }` | `class MyPlugin : SculkPaperPlugin() { override suspend fun start() }` or `Sculk.attach(this)` from any `JavaPlugin`. |
| `+commandSpec` (`unaryPlus`) | `commands { register(spec) }` or `command("name") { }` inside `start()`. |
| `keep(handle)` | Resources are scoped automatically; `scope.own(handle)` for externally created ones. |
| `sculk.events.listen<T> { }` | `events.listen<T> { }` (immediate) and `events.observe<T> { }` (suspending). |

Remaining sections (text, config, registry, items, commands, inventory, presentation, scenes, data,
packets, discord, web) are written when those modules land.

## Behaviour changes

| Area | Sculk 5 | Sculk 6 |
| --- | --- | --- |
| Command execution thread | Executors ran on the async dispatcher by default, without saying so. | Executors run in the context the spec declares; the default is the sender's ownership context. Async work is explicit. |
| Sidebar scoreboard | Replaced the player's scoreboard and could not restore it. | Presentation surfaces lease scoreboard state and restore it on close. |
| Folia | Declared in docs, never booted; HUD, holograms and nametags ran player work from the global task. | Every surface runs on the owning entity or region; tested on real Folia builds. |
| Packet guard | ProtocolLib handlers were unguarded. | Every backend runs handlers through the guard. |
| Integrations | Reflection resolved per call for Vault and LuckPerms. | Handles resolved once at startup. |
| Registry misses | Memoised forever. | Negative cache with expiry; providers can reload. |
| Directory watching | No debounce; one save reloaded several times. | Debounced, transactional reload. |
| Supported Minecraft | 1.21.x only. | 1.21.4 through 26.3; see `COMPATIBILITY.md`. |
| Java | 21. | JVM 21 bytecode; Java 25 on 26.x servers. |
| Adventure | 4.x only. | 4.20 to 5.2 across the matrix. |

## Removed without replacement

| Symbol | Reason |
| --- | --- |
| `SculkBlocksKt` sculk-sensor helpers | Unused brand pun. |
| `PacketDebugService` | Undocumented and unused; PacketEvents' own tooling covers it. |
| `LegacyCodes` on the public surface | Legacy colour codes are not part of the 6 API; a one-shot migration utility converts stored templates. |

## Procedure

1. Run `./gradlew sculkMigrate` on the 5.x project for the report.
2. Replace module coordinates per the table above and import the 6 BOM.
3. Fix imports module by module, following the symbol tables.
4. Review every command executor and scheduled task for the execution context it needs.
5. Run against Paper 1.21.11 and 26.3 before release.
