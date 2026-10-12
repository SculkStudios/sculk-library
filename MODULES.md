# Modules

One module, one capability. A module depends only on what it uses. Platform-neutral modules
never reach Bukkit, Paper or Velocity; the architecture check enforces every rule in the last
column. Packages are `studio.sculk.<area>`.

Every module below ships in 6.0; nothing is deferred to a later minor. The Status column says
where the implementation stands on `dev` today: **done** (exists, tested, ABI dumped), **in
progress** (exists, incomplete), **planned** (designed, no code yet). The milestone that builds each
planned module is in `REBUILD.md`.

## Foundation

| Module | Capability | Depends on | External | Neutral | Status |
| --- | --- | --- | --- | --- | --- |
| `sculk-core` | `SculkResult` and typed failures, `SculkHandle`, `ResourceScope`, lifecycle phases, `MinecraftVersion`, capability API, `Clock`, service registry, diagnostics model, stability annotations | — | — | yes | done |
| `sculk-coroutines` | Execution contexts (`Global`, `Region`, `Entity`, `Async`, `Proxy`), dispatcher contract, scope hierarchy, tasks (repeating, delayed, cron, debounce, throttle, batch), dev-mode thread checker | core | kotlinx-coroutines-core | yes | done |
| `sculk-platform-api` | Runtime contract implemented by each platform: audiences, permissions, scheduling primitives, capability provider, lifecycle hooks | core, coroutines | — | yes | done |
| `sculk-testkit` | Fakes for every interface a consumer calls; fake ownership runtime that asserts required versus actual context; deterministic clock and scheduler | core, coroutines, platform-api | kotlinx-coroutines-test | yes | done |

## Platforms

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-paper` | `Sculk.attach`, `SculkPaperPlugin`, Paper and Folia runtime, capability adapters per anchor, bootstrap hook, event bridge. Folia needs no module of its own: Paper exposes the same region schedulers and ownership checks | platform-api | paper-api (compileOnly) | done |
| `sculk-canvas` | Canvas capability provider: regionized data, bounding-box and movement ownership checks, async world unload, tick manager | paper | canvas-api (compileOnly) | done |
| `sculk-velocity` | Velocity runtime: lifecycle over Guice construction, proxy dispatcher, events | platform-api | velocity-api (compileOnly) | done |
| `sculk-velocity-runtime` | Optional provider plugin carrying Kotlin, kotlinx and Sculk once for every Velocity plugin on a proxy, because Velocity's class loaders share classes across plugins (`LICENSING.md`, Consumers) | velocity | — | planned |

## Author experience

| Module | Capability | Depends on | External | Neutral | Status |
| --- | --- | --- | --- | --- | --- |
| `sculk-text` | Themes, semantic styles, trusted-template rendering, translations and plurals, glyph metrics, wrapping, centring | core | adventure, minimessage (compileOnly) | yes | done; translations move onto Adventure's `MiniMessageTranslationStore` and `GlobalTranslator` with CLDR plurals in M3 (ADR-0024) |
| `sculk-placeholders` | Typed placeholders as `TagResolver`s with refresh policy and change signals; global, audience, relational, contextual | text, coroutines | — | yes | planned |
| `sculk-config` | Typed YAML with comment and unknown-key preservation, migrations, validation, transactional reload, environment placeholders, secrets | core | kotlinx-serialization-core, snakeyaml-engine | yes | done |
| `sculk-commands` | Immutable `CommandSpec`, DSL, neutral argument model (primitive types and validators), middleware, cooldowns, confirmation, flags, help and usage, server-free test runner that interprets the spec (ADR-0022) | text, coroutines | — (no Brigadier) | yes | planned (next) |
| `sculk-commands-paper` | Compiles a spec through Paper's `io.papermc.paper.command.brigadier` API (`Commands`, `ArgumentTypes`, `LifecycleEvents.COMMANDS`); Paper argument catalogue: players, entity selectors, positions, registry resources | commands, paper | paper-api (compileOnly; brings Brigadier) | no | planned |
| `sculk-commands-velocity` | Compiles a spec into a `BrigadierCommand`; proxy catalogue (player, server); backend-only types sent as strings and validated on the proxy, since Velocity supports no custom argument types | commands, velocity | velocity-api (compileOnly; brings velocity-brigadier) | no | planned |
| `sculk-events` | `listen` (immediate), `observe` (suspending), awaitable events, lifecycle-bound handles | coroutines, platform-api | — | yes | planned |
| `sculk-permissions` | Permission descriptors, native backend, context-aware checks | core, platform-api | — | yes | planned |

## Content

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-items` | `ItemSpec` compiled to data components (native on every anchor, probed per component type); typed PDC codecs for every holder; custom-item provider SPI | text, paper | — | planned |
| `sculk-registry` | Registry keys, tags, key and value sets, bootstrap mutation, aliases from generated mappings, datapack discovery | paper | — | planned |
| `sculk-recipes` | Recipe DSL for every vanilla type including brewing and transmute | items, registry | — | planned |

## Interface

| Module | Capability | Depends on | Status |
| --- | --- | --- | --- |
| `sculk-inventory` | State-driven menus with diff rendering, typed native views, full interaction policy, pagination, entity-owned sessions | items, text | planned |
| `sculk-dialogs` | `DialogSpec`, typed responses, registered and dynamic dialogs, explicit fallback | text, paper | planned |
| `sculk-presentation` | Render graph with update policies; sidebar, below-name, tab list, header and footer, action bar, boss bars, titles, nametags; conditions; scoreboard leases. No packet dependency: on Folia, sidebar, below-name and nametag teams report `Unavailable` (ADR-0023) | text, placeholders, paper | planned |
| `sculk-presentation-packets` | Opt-in packet-backed sidebar, below-name and nametag teams for Folia, through whichever packet backend is installed | presentation, packets-api | planned |
| `sculk-config-admin` | Opt-in operator editor generated from a config schema, dialog first with explicit fallback | config, dialogs, inventory | planned |

## Visuals and assets

| Module | Capability | Depends on | Status |
| --- | --- | --- | --- |
| `sculk-scenes` | Scene graph over display and interaction entities; anchors; parenting; native or virtual backend per node | effects, packets-api, paper | planned |
| `sculk-effects` | Animation engine (keyframes, easing, tracks), particle shapes and emitters, Adventure sound cues, post-effects, waypoints, visual budgets | text, paper | planned |
| `sculk-resourcepack` | Pack descriptors and stack management, hosting SPI, configuration-phase sending, glyph metrics feed | text, paper | planned |
| `sculk-resourcepack-ktor` | Embedded pack host | resourcepack, web-ktor | planned |

## World

| Module | Capability | Depends on | Status |
| --- | --- | --- | --- |
| `sculk-world` | Region-partitioned batches, chunk tickets, world keys and name migration, borders, ray tracing, world creation and unloading | coroutines, paper | planned |
| `sculk-entity` | Spawn specs, async teleport with ownership re-entry, pathfinder and goal helpers, attributes, equipment, lifecycle handles | coroutines, paper | planned |

## Data and network

| Module | Capability | Depends on | External | Neutral | Status |
| --- | --- | --- | --- | --- | --- |
| `sculk-data` | Suspend repositories, query DSL, transactions, migrations with history and checksums, dialects | core, coroutines | kotlinx-serialization-core, HikariCP | yes | planned |
| `sculk-data-sqlite` / `-mysql` / `-mariadb` / `-postgres` | Driver binding and dialect | data | driver (compileOnly) | yes | planned |
| `sculk-cache` | Cache contract, TTL, negative caching, stampede protection, invalidation | core, coroutines | — | yes | planned |
| `sculk-cache-caffeine` | Local backend | cache | Caffeine | yes | planned |
| `sculk-cache-valkey` | Shared backend with pub/sub invalidation | cache | Lettuce (compileOnly) | yes | planned |
| `sculk-network` | Typed messages, RPC, frame security, transport SPI, and the plugin-messaging transport over the platform-api messaging contract. Plugin messaging needs a connected player on the target server: with none, the transport reports no carrier as a capability and a send fails with `NoCarrier` rather than queueing silently | core, coroutines, platform-api | kotlinx-serialization-cbor | yes | planned |
| `sculk-network-valkey` | Valkey pub/sub transport, which reaches servers with no players online | network | Lettuce (compileOnly) | yes | planned |

## Packets

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-packets-api` | Send, observe, client blocks, virtual entities, guard | coroutines, platform-api | — | planned |
| `sculk-packets-packetevents` | Primary backend | packets-api | PacketEvents (compileOnly, GPL-3.0) | planned |
| `sculk-packets-protocollib` | Best-effort backend | packets-api | ProtocolLib (compileOnly, GPL-2.0) | planned |

## Integrations

Each is a thin adapter with handles resolved once at startup. All upstream dependencies are
`compileOnly`.

| Module | Upstream | Status |
| --- | --- | --- |
| `sculk-luckperms` | LuckPerms (contexts, QueryOptions, offline loads, never blocks a tick) | planned |
| `sculk-vault` | Economy through VaultUnlocked (active, Folia-aware, UUID-based, multi-currency) as the primary target, with the legacy Vault `Economy` service as fallback when only Vault or a Vault-only provider is present | planned |
| `sculk-placeholderapi` | PlaceholderAPI, Component API where available | planned |
| `sculk-miniplaceholders` | MiniPlaceholders, both directions | planned |
| `sculk-floodgate` | Bedrock context for UX adaptation | planned |
| `sculk-nexo`, `sculk-oraxen`, `sculk-itemsadder` | Custom-item providers | planned |
| `sculk-spark` | Metrics export | planned |
| `sculk-worldedit` | WorldEdit and FAWE bridge for large edits | planned |
| `sculk-viaversion` | Client protocol context | planned |

## Standalone

Never reach Bukkit, Paper or Velocity. A bot or web service uses them directly.

| Module | Capability | Depends on | External | Status |
| --- | --- | --- | --- | --- |
| `sculk-discord` | Backend-neutral Discord: message tree, Components V2, slash specs, typed interactions, collectors, webhooks | core, coroutines | kotlinx-serialization-json | planned |
| `sculk-discord-jda` | JDA backend | discord | JDA (compileOnly) | planned |
| `sculk-web` | Routes as data, lifecycle, auth helpers, health | core, coroutines | — | planned |
| `sculk-web-ktor` | Ktor CIO backend | web | Ktor (compileOnly) | planned |

## Internals

Experimental in their entirety. Each exists only with an ADR and a benchmark that justify it
(ADR-0007); 6.0 may ship with none, and that is a decision, not a deferral.

| Module | Status |
| --- | --- |
| `sculk-internals-api`, `sculk-internals-paper`, `sculk-internals-canvas` | planned, only where a benchmark justifies them |

## Build tooling

| Module | Capability | Status |
| --- | --- | --- |
| `sculk-bom` | Version alignment, generated from the module list | planned |
| `sculk-catalog` | Published Gradle version catalog | planned |
| `sculk-paper-all` | POM-only aggregate depending on every Paper-side module, for authors who want everything; contains no code | planned |
| `sculk-gradle-plugin` | `sculk { }` DSL, metadata generation, packaging, run tasks, resource-pack build and typed assets, Doctor, Migrate, compatibility report | planned |
| `sculk-codegen-annotations`, `sculk-codegen-ksp` | Optional annotation frontends for commands and listeners | planned |
| `sculk-maven-plugin` | Metadata and verification goals | planned |

Never published: `integration-tests/*` (server-side test plugins) and `tooling/compatibility`
(the Paper API scanner) exist today and back the M1 and M2 gates.

## Rules the architecture check enforces

| Rule | Scope |
| --- | --- |
| No `org.bukkit`, `io.papermc`, `com.velocitypowered` | every module marked neutral |
| No `com.mojang.brigadier` (platform API, as `paper-api` and `velocity-api` bring it) | every module marked neutral |
| No `net.minecraft`, CraftBukkit, or any other `com.mojang` package | everything outside `sculk-internals-*` |
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
