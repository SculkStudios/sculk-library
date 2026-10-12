# Modules

One module, one capability. A module depends only on what it uses. Platform-neutral modules
never reach Bukkit, Paper or Velocity; the architecture check enforces every rule in the last
column. Packages are `studio.sculk.<area>`.

Status column: **6.0** ships in the first release; **6.1** is designed for but deferred.

## Foundation

| Module | Capability | Depends on | External | Neutral | Status |
| --- | --- | --- | --- | --- | --- |
| `sculk-core` | `SculkResult` and typed failures, `SculkHandle`, `ResourceScope`, lifecycle phases, `MinecraftVersion`, capability API, `Clock`, service registry, diagnostics model, stability annotations | — | kotlinx-coroutines-core | yes | 6.0 |
| `sculk-coroutines` | Execution contexts (`Global`, `Region`, `Entity`, `Async`, `Proxy`), dispatcher contract, scope hierarchy, tasks (repeating, delayed, cron, debounce, throttle, batch), dev-mode thread checker | core | — | yes | 6.0 |
| `sculk-platform-api` | Runtime contract implemented by each platform: audiences, permissions, scheduling primitives, capability provider, lifecycle hooks | core, coroutines | — | yes | 6.0 |
| `sculk-testkit` | Fakes for every interface a consumer calls; fake ownership runtime that asserts required versus actual context; deterministic clock and scheduler | core, coroutines, platform-api | kotlinx-coroutines-test | yes | 6.0 |

## Platforms

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-paper` | `Sculk.attach`, `SculkPaperPlugin`, Paper and Folia runtime, capability adapters per anchor, bootstrap hook, event bridge, Brigadier compiler host. Folia needs no module of its own: Paper exposes the same region schedulers and ownership checks | platform-api | paper-api (compileOnly) | 6.0 |
| `sculk-canvas` | Canvas capability provider: regionized data, bounding-box and movement ownership checks, async world unload, tick manager | paper | canvas-api (compileOnly) | 6.0 |
| `sculk-velocity` | Velocity runtime: lifecycle over Guice construction, proxy dispatcher, events, Brigadier compiler host | platform-api | velocity-api (compileOnly) | 6.0 |
| `sculk-velocity-runtime` | Provider plugin carrying Kotlin, kotlinx and Sculk for every Velocity plugin, because Velocity's class loaders share classes across plugins | velocity | — | 6.0 |
| `sculk-paper-all` | Aggregate of every Paper-side module for authors who want everything | all Paper-side | — | 6.0 |

## Author experience

| Module | Capability | Depends on | External | Neutral | Status |
| --- | --- | --- | --- | --- | --- |
| `sculk-text` | Themes, semantic styles, trusted-template rendering, translations and plurals, glyph metrics, wrapping, centring | core | adventure, minimessage (compileOnly) | yes | 6.0 |
| `sculk-placeholders` | Typed placeholders as `TagResolver`s with refresh policy and change signals; global, audience, relational, contextual | text, coroutines | — | yes | 6.0 |
| `sculk-config` | Typed YAML with comment and unknown-key preservation, migrations, validation, transactional reload, environment placeholders, secrets | core | kotlinx-serialization-core, snakeyaml-engine | yes | 6.0 |
| `sculk-commands` | Immutable `CommandSpec`, DSL, argument catalogue, middleware, cooldowns, confirmation, flags, help, serverless test runner | text, coroutines | brigadier (compileOnly) | yes | 6.0 |
| `sculk-events` | `listen` (immediate), `observe` (suspending), awaitable events, lifecycle-bound handles | coroutines, platform-api | — | yes | 6.0 |
| `sculk-permissions` | Permission descriptors, native backend, context-aware checks | core, platform-api | — | yes | 6.0 |

## Content

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-items` | `ItemSpec` compiled to data components or `ItemMeta`; typed PDC codecs for every holder; custom-item provider SPI | text, paper | — | 6.0 |
| `sculk-registry` | Registry keys, tags, key and value sets, bootstrap mutation, aliases from generated mappings, datapack discovery | paper | — | 6.0 |
| `sculk-recipes` | Recipe DSL for every vanilla type including brewing and transmute | items, registry | — | 6.0 |

## Interface

| Module | Capability | Depends on | Status |
| --- | --- | --- | --- |
| `sculk-inventory` | State-driven menus with diff rendering, typed native views, full interaction policy, pagination, entity-owned sessions | items, text | 6.0 |
| `sculk-dialogs` | `DialogSpec`, typed responses, registered and dynamic dialogs, explicit fallback | text, paper | 6.0 |
| `sculk-presentation` | Render graph with update policies; sidebar, below-name, tab list, header and footer, action bar, boss bars, titles, nametags; conditions; scoreboard leases | text, placeholders, paper | 6.0 |
| `sculk-config-admin` | Opt-in operator editor generated from a config schema, dialog first with explicit fallback | config, dialogs, inventory | 6.0 |

## Visuals and assets

| Module | Capability | Depends on | Status |
| --- | --- | --- | --- |
| `sculk-scenes` | Scene graph over display and interaction entities; anchors; parenting; native or virtual backend per node | effects, packets-api, paper | 6.0 |
| `sculk-effects` | Animation engine (keyframes, easing, tracks), particle shapes and emitters, Adventure sound cues, post-effects, waypoints, visual budgets | text, paper | 6.0 |
| `sculk-resourcepack` | Pack descriptors and stack management, hosting SPI, configuration-phase sending, glyph metrics feed | text, paper | 6.0 |
| `sculk-resourcepack-ktor` | Embedded pack host | resourcepack, web-ktor | 6.0 |

## World

| Module | Capability | Depends on | Status |
| --- | --- | --- | --- |
| `sculk-world` | Region-partitioned batches, chunk tickets, world keys and name migration, borders, ray tracing, world creation and unloading | coroutines, paper | 6.0 |
| `sculk-entity` | Spawn specs, async teleport with ownership re-entry, pathfinder and goal helpers, attributes, equipment, lifecycle handles | coroutines, paper | 6.0 |

## Data and network

| Module | Capability | Depends on | External | Neutral | Status |
| --- | --- | --- | --- | --- | --- |
| `sculk-data` | Suspend repositories, query DSL, transactions, migrations with history and checksums, dialects | core, coroutines | kotlinx-serialization-core, HikariCP | yes | 6.0 |
| `sculk-data-sqlite` / `-mysql` / `-mariadb` / `-postgres` | Driver binding and dialect | data | driver (compileOnly) | yes | 6.0 |
| `sculk-cache` | Cache contract, TTL, negative caching, stampede protection, invalidation | core, coroutines | — | yes | 6.0 |
| `sculk-cache-caffeine` | Local backend | cache | Caffeine | yes | 6.0 |
| `sculk-cache-valkey` | Shared backend with pub/sub invalidation | cache | Lettuce (compileOnly) | yes | 6.0 |
| `sculk-network` | Typed messages, transports (plugin channel, Valkey), RPC, frame security | core, coroutines, platform-api | kotlinx-serialization-cbor | yes | 6.0 |

## Packets

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-packets-api` | Send, observe, client blocks, virtual entities, guard | coroutines, platform-api | — | 6.0 |
| `sculk-packets-packetevents` | Primary backend | packets-api | PacketEvents (compileOnly, GPL-3.0) | 6.0 |
| `sculk-packets-protocollib` | Best-effort backend | packets-api | ProtocolLib (compileOnly, GPL-2.0) | 6.0 |

## Integrations

Each is a thin adapter with handles resolved once at startup. All upstream dependencies are
`compileOnly`.

| Module | Upstream | Status |
| --- | --- | --- |
| `sculk-luckperms` | LuckPerms (contexts, QueryOptions, offline loads, never blocks a tick) | 6.0 |
| `sculk-vault` | Vault economy | 6.0 |
| `sculk-placeholderapi` | PlaceholderAPI, Component API where available | 6.0 |
| `sculk-miniplaceholders` | MiniPlaceholders, both directions | 6.0 |
| `sculk-floodgate` | Bedrock context for UX adaptation | 6.0 |
| `sculk-nexo`, `sculk-oraxen`, `sculk-itemsadder` | Custom-item providers | 6.0 |
| `sculk-spark` | Metrics export | 6.0 |
| `sculk-worldedit` | WorldEdit and FAWE bridge for large edits | 6.0 |
| `sculk-viaversion` | Client protocol context | 6.1 |

## Standalone

Never reach Bukkit, Paper or Velocity. A bot or web service uses them directly.

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-discord` | Backend-neutral Discord: message tree, Components V2, slash specs, typed interactions, collectors, webhooks | core, coroutines | kotlinx-serialization-json | 6.0 |
| `sculk-discord-jda` | JDA backend | discord | JDA (compileOnly) | 6.0 |
| `sculk-web` | Routes as data, lifecycle, auth helpers, health | core, coroutines | — | 6.0 |
| `sculk-web-ktor` | Ktor CIO backend | web | Ktor (compileOnly) | 6.0 |

## Internals

Experimental in their entirety, built only where a benchmark justifies them (ADR to follow).

| Module | Status |
| --- | --- |
| `sculk-internals-api`, `sculk-internals-paper` | 6.0 only if justified, otherwise 6.1 |
| `sculk-internals-canvas` | 6.1 |

## Build tooling

| Module | Capability | Status |
| --- | --- | --- |
| `sculk-bom` | Version alignment, generated from the module list | 6.0 |
| `sculk-catalog` | Published Gradle version catalog | 6.0 |
| `sculk-gradle-plugin` | `sculk { }` DSL, metadata generation, packaging, run tasks, resource-pack build and typed assets, Doctor, Migrate, compatibility report | 6.0 |
| `sculk-codegen-annotations`, `sculk-codegen-ksp` | Optional annotation frontends for commands and listeners | 6.0 |
| `sculk-maven-plugin` | Metadata and verification goals | 6.1 |

## Rules the architecture check enforces

| Rule | Scope |
| --- | --- |
| No `org.bukkit`, `io.papermc`, `com.velocitypowered` | every module marked neutral |
| No `net.minecraft` or `com.mojang` | everything outside `sculk-internals-*` |
| No PacketEvents | outside `sculk-packets-packetevents` |
| No ProtocolLib | outside `sculk-packets-protocollib` |
| No JDA | outside `sculk-discord-jda` |
| No Ktor | outside `sculk-web-ktor`, `sculk-resourcepack-ktor` |
| No SQL driver packages | outside `sculk-data-*` driver modules |
| No `BukkitScheduler` | everywhere |
| No `runBlocking` | all main source sets |
| No `kotlin-reflect` | every runtime classpath |
| No class file above major 65 | every published jar |
| Every public declaration carries one stability marker | every published module |
