# Sculk Studio

> Kotlin-first Paper utilities for building modern Minecraft plugins.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/SculkStudios/sculk-library.svg)](https://jitpack.io/#SculkStudios/sculk-library)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Sculk Studio is the shared Kotlin library used by Sculk Studios plugins. It provides small, composable APIs for commands, GUI menus, item stacks, typed config, data access, Adventure text, localization, scheduling, effects, optional integrations, and Paper lifecycle integration. Sculk 4.0 is Kotlin-first and coroutine-based throughout — see the [4.0 migration guide](https://docs.sculk.studio/advanced/migration-to-sculk-4/).

## Features

- **Coroutines** - A plugin-scoped `SculkCoroutineScope` with Folia-aware dispatchers powers every async API.
- **Commands** - Brigadier-native: the `command { }` DSL compiles to Paper's command tree for real client-side completion, with suspend executors, middleware, and stateful cooldowns.
- **GUI menus** - Chest & container GUIs, click routing, per-click-type handlers, pagination, animated slots, interactive input slots, and platform-managed cleanup.
- **Items** - Data-component item builders (1.20.5+), typed/nested PDC, food/rarity/model-data helpers, and a generic component escape hatch.
- **Typed config** - Data class YAML configs with defaults, validation, strict load mode, env-var substitution, and file-watch auto-reload.
- **Data** - Suspend JDBC repositories, a type-safe query DSL, transactions, ORM mapping, and Caffeine + Redis caching.
- **Localization** - Per-player message bundles, MiniMessage templates, placeholders, and pluralization.
- **Tasks** - Coroutine scheduling with cron expressions, debounce, and throttle.
- **Series / Effects / Packets / Integrations** - Registry helpers, particle/sound builders, optional PacketEvents/ProtocolLib adapters, and PlaceholderAPI/Vault/LuckPerms bridges.
- **Platform** - Paper bootstrap wiring commands, events, GUI lifecycle, config, data, text, tasks, integrations, and scheduling.

## Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    implementation("com.github.SculkStudios.sculk-library:sculk-platform:3.0.0")
    implementation("com.github.SculkStudios.sculk-library:sculk-items:3.0.0")
}
```

Shade and relocate the library into your plugin jar:

```kotlin
plugins {
    id("com.gradleup.shadow") version "9.4.1"
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("studio.sculk", "your.plugin.libs.sculk")
}
```

## Quick Start

Extend `SculkPlugin` — it creates the platform, exposes it as `sculk`, and closes it on disable.
No lifecycle boilerplate:

```kotlin
import studio.sculk.core.command.command
import studio.sculk.platform.SculkPlugin

class MyPlugin : SculkPlugin({ gui(); config() }) {
    override fun setup() {
        sculk.commands.register(
            command("hello") {
                player { reply("<green>Hello, <yellow>${player.name}</yellow>!") }
            },
        )
    }
}
```

Prefer full control? Extend `JavaPlugin` and call `SculkPlatform.create(this) { … }` in `onEnable`
yourself.

## Modules

You only ever depend on **`sculk-platform`** — it transitively re-exports the entire DSL. The table
below is for reference; à-la-carte use is possible for minimal builds.

| Module | Description |
|---|---|
| `sculk-core` | Commands, GUI, Adventure helpers, coroutines, scheduler, version parsing |
| `sculk-config` | Typed configs, hot reload, validation, env-var substitution, file-watch reload |
| `sculk-series` | Registry-based compatibility helpers |
| `sculk-items` | Data-component item builders, typed PDC, skulls, descriptors |
| `sculk-effects` | Particle and sound builders, animation timelines |
| `sculk-data` | Suspend JDBC repositories, query DSL, transactions, Caffeine + Redis caching |
| `sculk-text` | Per-player localization with bundles, placeholders, and pluralization |
| `sculk-tasks` | Coroutine scheduling: cron, repeating, debounce, throttle |
| `sculk-integrations` | Optional PlaceholderAPI, Vault, and LuckPerms adapters |
| `sculk-packets-api` | Backend-neutral packet contracts and high-level packet services |
| `sculk-packets-packetevents` | Optional PacketEvents packet backend adapter |
| `sculk-packets-protocollib` | Optional ProtocolLib compatibility backend adapter |
| `sculk-content` | High-level client block helpers over packet services |
| `sculk-platform` | The single dependency — wires everything and re-exports the full DSL |
| `sculk-platform` | Paper integration for plugin lifecycle |

## Recipe Examples

Compile-checked example plugins live in `examples/` and the matching guides live in the docs:

- `economy-plugin`: cached accounts, async persistence, payments, admin balance tools, and top balances.
- `player-profiles`: join load, quit save, profile cache, GUI display, and shutdown flush.
- `server-menu`: config-backed menu buttons, pagination, profile pages, and confirm flows.
- `staff-tools`: staff mode, freeze, inspect menus, staff chat, effects, and transient state cleanup.
- `crate-system`: key items, PDC markers, weighted rewards, preview menus, and reward delivery.
- `kits-plugin`: item descriptors, kit cooldown persistence, preview menus, permissions, and safe give/drop handling.

## Stability Notes

| Area | Current status |
|---|---|
| Commands | Stable DSL for Bukkit command-map registration, typed arguments, cooldowns, help, permissions, and suggestions. |
| Configs | Data class YAML configs with defaults, validation, comments, migrations, strict mode, and reload results. |
| Data | Blocking JDBC repositories plus async facades, player profile helpers, cache layer, SQLite/MySQL configuration, and ORM mapping. |
| GUI | Chest GUI sessions, click routing, pagination, confirm menus, dynamic items, and platform-managed cleanup. |
| Items | Standalone item builders, descriptors, PDC helpers, skulls, modern custom model data, glint override, and Java builders. |
| Text | MiniMessage, Adventure helpers, message templates, titles, action bars, sounds, and broadcasts. |
| Scheduler | Paper/Folia-aware scheduler contract with sync, async, delayed, repeating, and async-to-sync handoff helpers. |
| Events | Disposable listeners, once listeners, filters, priority, ignore-cancelled support, and platform lifecycle cleanup. |
| Series | Modern Paper registry lookups, require helpers, validation reports, and curated aliases. |
| Integrations | Optional PlaceholderAPI, Vault, and LuckPerms adapters with failure results for missing plugins. |
| Packets | Optional backend-neutral packet contracts with PacketEvents preferred, ProtocolLib compatibility, client block previews, and debug sessions. |
| Docs | Public manual with feature pages and compile-checked real server recipe examples. |

## Documentation

Full docs: [docs.sculk.studio](https://docs.sculk.studio)

For existing plugins, start with the migration guide:

- [Migration to Sculk 3](https://docs.sculk.studio/advanced/migration-to-sculk-3/)
- [Migration Checklist](https://docs.sculk.studio/advanced/migration-checklist/)

## Requirements

- Paper 26.1.2+
- Java 25+
- Kotlin JVM

## License

MIT - see [LICENSE](LICENSE).

Built by [Sculk Studios](https://sculk.studio).
