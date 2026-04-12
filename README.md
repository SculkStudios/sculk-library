# Sculk Studio

> A Kotlin-first, Java-compatible, production-grade Minecraft framework.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/SculkStudios/sculk-library.svg)](https://jitpack.io/#SculkStudios/sculk-library)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Sculk Studio powers all internal [Sculk Studios](https://sculk.gg) plugins. Clean DSLs, zero boilerplate, elite developer experience.

## Features

- **Command System** — Subcommand tree, typed arguments, auto tab-complete, auto help
- **GUI System** — Chest GUIs, per-player sessions, pagination, automatic cleanup
- **Typed Config** — Data class configs, hot reload, validation, MiniMessage messages
- **Series** — Registry-based cross-version material, sound & particle mapping
- **Effects** — Particle builders, sound builders, animation timelines & sequences
- **Data** — Async SQLite/MySQL via repository pattern + Caffeine cache layer
- **Platform** — Single-line Paper bootstrap with full lifecycle management

## Quick Start

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    implementation("com.github.SculkStudios.sculk-library:sculk-platform:1.0.0")
}
```

Shadow (shade) Sculk Studio into your plugin jar:

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks.shadowJar {
    archiveClassifier = ""
    relocate("gg.sculk", "your.plugin.libs.sculk")
}
```

### Kotlin

```kotlin
class MyPlugin : JavaPlugin() {
    lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {
            gui()
            config()
        }

        sculk.commands.register(
            command("hello") {
                player {
                    reply("<gradient:aqua:blue>Hello from Sculk Studio!")
                }
            }
        )
    }

    override fun onDisable(): Unit = sculk.close()
}
```

### Java

```java
public class MyPlugin extends JavaPlugin {
    private SculkPlatform sculk;

    @Override
    public void onEnable() {
        sculk = JavaSculkPlatform.create(this, cfg -> cfg.gui().config());

        sculk.getCommands().register(
            JavaCommandBuilder.create("hello")
                .player(ctx -> ctx.reply("<gradient:aqua:blue>Hello from Sculk Studio!"))
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
| `sculk-core` | Commands, GUI, Adventure wrapper, scheduler contracts |
| `sculk-config` | Typed configs, hot reload, validation, message system |
| `sculk-series` | Cross-version registry mapping |
| `sculk-effects` | Particle & sound builders, animation timelines |
| `sculk-data` | Repository pattern, SQLite/MySQL, Caffeine cache |
| `sculk-platform` | Paper integration — use this one |

## Documentation

Full docs at [docs.sculk.gg](https://docs.sculk.gg) — every feature includes Kotlin and Java examples.

## Requirements

- Paper 1.21.11+
- Java 21+

## License

MIT — see [LICENSE](LICENSE).

---

Built with care by [Sculk Studios](https://sculk.gg).
