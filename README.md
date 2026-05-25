# Sculk Studio

> Kotlin-first Paper utilities for building modern Minecraft plugins.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/SculkStudios/sculk-library.svg)](https://jitpack.io/#SculkStudios/sculk-library)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Sculk Studio is the shared Kotlin library used by Sculk Studios plugins. It provides small, composable APIs for commands, GUI menus, item stacks, typed config, data access, Adventure text, effects, optional integrations, and Paper lifecycle integration.

## Features

- **Commands** - Subcommand tree, sender routing, typed arguments, cooldowns, suggestions, and Java builders.
- **GUI menus** - Chest GUI sessions, click routing, pagination, confirm menus, and platform-managed cleanup.
- **Items** - Modern Paper item builders, PDC helpers, skulls, descriptors, custom model data, and Java builders.
- **Typed config** - Data class YAML configs with defaults, validation annotations, strict load mode, and reload results.
- **Series** - Registry helpers, require lookups, validation reports, and curated aliases for modern config keys.
- **Effects** - Particle builders, sound builders, descriptors, timelines, and tick-based sequences.
- **Data** - JDBC repositories, async facades, player profile helpers, SQLite/MySQL configuration, ORM mapping, and Caffeine-backed caching.
- **Packets** - Optional PacketEvents-first packet APIs with ProtocolLib compatibility adapters.
- **Platform** - Paper bootstrap for commands, events, GUI lifecycle, config, data, integrations, and scheduling.

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
    implementation("com.github.SculkStudios.sculk-library:sculk-platform:v2.0.0")
    implementation("com.github.SculkStudios.sculk-library:sculk-items:v2.0.0")
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

```kotlin
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.core.command.command
import studio.sculk.platform.SculkPlatform

class MyPlugin : JavaPlugin() {
    private lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {
            gui()
            config()
        }

        sculk.commands.register(
            command("hello") {
                player {
                    reply("<green>Hello, <yellow>${player.name}</yellow>!")
                }
            }
        )
    }

    override fun onDisable() {
        sculk.close()
    }
}
```

## Java Example

```java
import org.bukkit.plugin.java.JavaPlugin;
import studio.sculk.core.command.java.JavaCommandBuilder;
import studio.sculk.platform.SculkPlatform;
import studio.sculk.platform.java.JavaSculkPlatform;

public class MyPlugin extends JavaPlugin {
    private SculkPlatform sculk;

    @Override
    public void onEnable() {
        sculk = JavaSculkPlatform.create(this, cfg -> cfg.gui().config());

        sculk.getCommands().register(
            JavaCommandBuilder.create("hello")
                .player(ctx -> ctx.reply("<green>Hello from Sculk Studio!"))
                .build()
        );
    }

    @Override
    public void onDisable() {
        sculk.close();
    }
}
```

## Modules

| Module | Description |
|---|---|
| `sculk-core` | Commands, GUI, Adventure helpers, version parsing, scheduler contracts |
| `sculk-config` | Typed configs, hot reload, validation, message config support |
| `sculk-series` | Registry-based compatibility helpers |
| `sculk-items` | Item builders, PDC helpers, skulls, descriptors, Java item builders |
| `sculk-effects` | Particle and sound builders, animation timelines |
| `sculk-data` | JDBC repositories, SQLite/MySQL config, cache layer |
| `sculk-integrations` | Optional PlaceholderAPI, Vault, and LuckPerms adapters |
| `sculk-packets-api` | Small backend-neutral packet contracts and high-level packet service APIs |
| `sculk-packets-packetevents` | Optional PacketEvents packet backend adapter |
| `sculk-packets-protocollib` | Optional ProtocolLib compatibility backend adapter |
| `sculk-content` | High-level client block helpers over packet services |
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

## Requirements

- Paper 26.1.2+
- Java 25+
- Kotlin JVM

## License

MIT - see [LICENSE](LICENSE).

Built by [Sculk Studios](https://sculk.studio).
