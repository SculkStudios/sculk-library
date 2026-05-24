# Sculk Studio

> Kotlin-first Paper utilities for building modern Minecraft plugins.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/SculkStudios/sculk-library.svg)](https://jitpack.io/#SculkStudios/sculk-library)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Sculk Studio is the shared Kotlin library used by Sculk Studios plugins. It provides small, composable APIs for commands, GUI menus, typed config, data access, Adventure text, effects, and Paper lifecycle integration.

## Features

- **Commands** - Subcommand tree, sender routing, typed arguments, suggestions, and Java builders.
- **GUI menus** - Chest GUI sessions, click routing, pagination, and platform-managed cleanup.
- **Typed config** - Data class YAML configs with defaults, validation annotations, and reload support.
- **Series** - Registry helpers for materials, sounds, particles, enchantments, entities, and effects.
- **Effects** - Particle builders, sound builders, timelines, and tick-based sequences.
- **Data** - JDBC repositories, SQLite/MySQL configuration, ORM mapping, and Caffeine-backed caching.
- **Platform** - Paper bootstrap for commands, events, GUI lifecycle, config, data, and scheduling.

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
    implementation("com.github.SculkStudios.sculk-library:sculk-platform:v1.0.0")
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
| `sculk-effects` | Particle and sound builders, animation timelines |
| `sculk-data` | JDBC repositories, SQLite/MySQL config, cache layer |
| `sculk-platform` | Paper integration for plugin lifecycle |

## Feature Matrix

| Area | Exists | Quality | Missing | Risk | Priority |
|---|---:|---|---|---|---|
| Commands | Yes | Good foundation | Brigadier/Paper command API research, richer errors, cooldowns | Bukkit fallback may age poorly | High |
| Configs | Yes | Good foundation | Migration helpers, more docs, comment guarantees | Reflection edge cases | High |
| Data | Yes | Useful but broad | Async safety docs, shutdown examples, SQL hardening | Main-thread misuse by users | High |
| GUI | Yes | Good foundation | Confirm menus, richer pagination docs, leak tests | Lifecycle leaks if registry cleanup regresses | High |
| Items | Partial via GUI items | Needs dedicated API | Standalone item builder, PDC helpers, skulls | Docs could overpromise | High |
| Text | Yes | Good foundation | Template/prefix system, cache strategy | Repeated MiniMessage parsing | Medium |
| Scheduler | Yes | Good foundation | More Folia tests/docs | Async Paper misuse by users | Medium |
| Events | Yes | Basic | More examples, unregister docs | Listener lifecycle bugs | Medium |
| Compatibility | Yes via Series | Early | More registry coverage | New Paper versioning | High |
| Storage | Yes | Useful | Migration docs, integration examples | JDBC/runtime config mistakes | Medium |
| Tests | Yes | Decent pure coverage | More lifecycle tests | Platform modules have limited tests | High |
| Docs | Yes | Promising | More real-world examples, migration guide expansion | Outdated examples | High |

## Documentation

Full docs: [docs.sculk.studio](https://docs.sculk.studio)

## Requirements

- Paper 26.1.2+
- Java 25+
- Kotlin JVM

## License

MIT - see [LICENSE](LICENSE).

Built by [Sculk Studios](https://sculk.studio).
