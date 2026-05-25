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
| `sculk-platform` | Paper integration for plugin lifecycle |

## Feature Matrix

| Area | Exists | Quality | Missing | Risk | Priority |
|---|---:|---|---|---|---|
| Commands | Yes | Strong 2.0 foundation | Paper/Brigadier backend research | Bukkit fallback may age poorly | High |
| Configs | Yes | Strong 2.0 foundation | Full migration DSL, comment guarantees | Reflection edge cases | High |
| Data | Yes | Strong 2.0 foundation | Migration runner hardening, more examples | Main-thread misuse by users | High |
| GUI | Yes | Strong 2.0 foundation | More lifecycle tests, richer async examples | Lifecycle leaks if registry cleanup regresses | High |
| Items | Yes | Complete 2.0 foundation | Advanced metadata parity, books/banners/fireworks | Full XItemStack parity is intentionally scoped | High |
| Text | Yes | Strong 2.0 foundation | More template docs, prefix registry polish | Repeated dynamic parsing if users bypass templates | Medium |
| Scheduler | Yes | Strong 2.0 foundation | More fake-scheduler tests | Async Paper misuse by users | Medium |
| Events | Yes | Strong 2.0 foundation | More lifecycle examples | Listener lifecycle bugs | Medium |
| Compatibility | Yes via Series | Strong modern Paper coverage | More curated aliases | New Paper versioning | High |
| Integrations | Yes | Practical optional adapters | More ecosystem adapters | Runtime plugin availability | Medium |
| Storage | Yes | Useful | Migration docs, integration examples | JDBC/runtime config mistakes | Medium |
| Tests | Yes | Decent pure coverage | More lifecycle tests | Platform modules have limited tests | High |
| Docs | Yes | Public-grade direction | More recipes and API reference pages | Examples must stay compile-aligned | High |

## Documentation

Full docs: [docs.sculk.studio](https://docs.sculk.studio)

## Requirements

- Paper 26.1.2+
- Java 25+
- Kotlin JVM

## License

MIT - see [LICENSE](LICENSE).

Built by [Sculk Studios](https://sculk.studio).
