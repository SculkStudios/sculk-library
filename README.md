# Sculk Studio

> A Kotlin framework for building modern Paper plugins.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/SculkStudios/sculk-library.svg)](https://jitpack.io/#SculkStudios/sculk-library)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Sculk Studio is the shared library behind Sculk Studios plugins: commands, menus, items, typed
config, a data layer, themed text, a HUD, packet-backed visuals and Paper lifecycle wiring.

**Kotlin-only.** Reified generics, default arguments, `suspend` functions and receiver lambdas are
the API, not a compromise around a second language. Target: Paper 1.21.11+, Java 21, Kotlin 2.x.

## Features

- **Text** — a theme of named styles (`<danger>`, `<value>`) expanded before parse, so messages are
  written against meaning rather than colour. Values always enter as unparsed placeholders, so a
  player-supplied name cannot inject markup. Real glyph-width measurement for centred text.
- **Commands** — the command tree is an immutable `CommandSpec`, free of Brigadier and Bukkit, so
  usage, permission filtering and help all test without a server. One adapter compiles it to
  Paper's tree for real client-side completion. Suspend executors, middleware, cooldowns, and a
  generated permission-filtered `/help`.
- **Config** — data-class YAML on kotlinx-serialization, no reflection. The defaults *are* the
  shipped file. A rewrite only ever adds: keys you did not model and comments you wrote are kept.
  Validation reports a path (`storage.mysql.port`), env-var substitution, migrations, hot reload.
- **Data** — suspend repositories over SQLite, MySQL/MariaDB and PostgreSQL, with quoted
  identifiers, real upserts, additive schema migration, `@Index`, and a query DSL with OR/IN/paging.
  Caffeine cache with negative caching; Redis/Valkey for a cache shared across servers.
- **Menus** — chest and container GUIs, per-click-type handlers, dynamic content, animated slots,
  pagination, interactive input slots, and cancel-before-dispatch click routing.
- **HUD** — sidebar, action bar, tab list and boss bars, driven by a single task. Flicker-free
  sidebar; action-bar messages arbitrate by priority and expire on their own.
- **Visual** — particles, sounds, timelines, packet-only holograms that cost the server no entities,
  and nametags ridden as a passenger so the client interpolates them.
- **Packets** — a backend-neutral API over PacketEvents or ProtocolLib, with client-side blocks,
  virtual entities, and a guard so a handler that throws cannot disconnect the player.
- **Testing** — `FakeScheduler` and `FakeVirtualEntityService` ship as test fixtures, because a
  module that hands out an interface should hand out something to test against it with.

## Installation

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts — one dependency gets the whole framework
dependencies {
    implementation("com.github.SculkStudios.sculk-library:sculk-platform:5.0.0")
}
```

Picking modules à la carte? Use the BOM so versions cannot drift:

```kotlin
dependencies {
    implementation(platform("com.github.SculkStudios.sculk-library:sculk-bom:5.0.0"))
    implementation("com.github.SculkStudios.sculk-library:sculk-commands")
    implementation("com.github.SculkStudios.sculk-library:sculk-gui")
}
```

## A plugin

```kotlin
class MyPlugin : SculkPlugin() {
    override val theme = SculkTheme(
        mapOf(
            "value" to ThemeStyle.Gradient(listOf("#8be9fd", "#50fa7b")),
            "danger" to ThemeStyle.Solid("#ff5f5f"),
        ),
    )

    override fun setup() {
        val settings = sculk.config.load<Settings>().getOrThrow()
        val economy = sculk.services.register(EconomyService(sculk.data))

        +command("balance") {
            description = "Shows your balance."
            player {
                val balance = economy.balanceOf(player!!.uniqueId)
                reply("<value><coins></value> coins.", "coins" to balance.toString())
            }
        }
    }
}
```

`onEnable` and `onDisable` are final: the framework starts and stops itself, and everything you
register is closed in reverse order.

## Modules

| Module | Purpose |
| --- | --- |
| `sculk-platform` | The one-line install — re-exports everything below |
| `sculk-common` | Result and handle types, coroutine scope, scheduler, tasks |
| `sculk-text` | Theme, message rendering, text measurement, localisation |
| `sculk-config` | Typed YAML: generated defaults, validation, migrations |
| `sculk-commands` | Command specs as data, Brigadier adapter, generated help |
| `sculk-gui` | Chest and container menus |
| `sculk-data` | Repositories, query DSL, schema migration, caching |
| `sculk-hud` | Sidebar, action bar, tab list, boss bars |
| `sculk-visual` | Particles, sounds, holograms, nametags |
| `sculk-items` | Data-component item builders, PDC, descriptors |
| `sculk-series` | Registry lookups with aliases |
| `sculk-packets-api` | Backend-neutral packets (`-packetevents`, `-protocollib`) |
| `sculk-integrations` | Optional PlaceholderAPI, Vault, LuckPerms adapters |
| `sculk-bom` | Version alignment |

## Documentation

[docs.sculk.studio](https://docs.sculk.studio)

## Building

```
./gradlew build      # compile + test + ktlint, all modules and examples
./gradlew apiCheck   # public surface matches the committed .api dumps
```

## Licence

MIT. PacketEvents is GPL-3.0 and stays `compileOnly`, confined to `sculk-packets-packetevents`, so
it is never redistributed.
