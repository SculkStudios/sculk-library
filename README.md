# Sculk Studio

> A Kotlin-first, Java-compatible, production-grade Minecraft framework.

[![CI](https://github.com/SculkStudios/sculk-studio/actions/workflows/ci.yml/badge.svg)](https://github.com/SculkStudios/sculk-studio/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/SculkStudios/sculk-studio.svg)](https://jitpack.io/#SculkStudios/sculk-studio)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

Sculk Studio powers all internal [Sculk Studios](https://sculk.gg) plugins. Clean DSLs, zero boilerplate, elite developer experience.

## Features

- **Command System** — Subcommand tree, auto tab-complete, auto help generation
- **GUI System** — Chest & anvil GUIs, pagination, state-based updates
- **Typed Config** — Data class configs, hot reload, MiniMessage messages
- **Series** — Registry-based cross-version material, sound & particle mapping
- **Effects** — Particle builders, sound builders, animation timelines
- **Data** — Async SQLite/MySQL abstraction with Caffeine caching
- **Platform** — One-line Paper bootstrap with full lifecycle management

## Quick Start

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("com.github.SculkStudios:sculk-platform:1.0.0")
}
```

### Kotlin

```kotlin
class MyPlugin : JavaPlugin() {
    lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {
            commands()
            gui()
            config()
        }

        command("hello") {
            player {
                reply("<gradient:aqua:blue>Hello from Sculk Studio!")
            }
        }
    }

    override fun onDisable() = sculk.close()
}
```

### Java

```java
public class MyPlugin extends JavaPlugin {
    private SculkPlatform sculk;

    @Override
    public void onEnable() {
        sculk = SculkPlatform.create(this, cfg -> cfg.commands().gui().config());

        Command.builder("hello")
            .player(ctx -> ctx.reply("<gradient:aqua:blue>Hello from Sculk Studio!"))
            .register(this);
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
| `sculk-core` | Commands, GUI, Adventure wrapper, scheduler |
| `sculk-config` | Typed configs, hot reload, message system |
| `sculk-series` | Cross-version registry mapping |
| `sculk-effects` | Particle & sound builders, animation timelines |
| `sculk-data` | Async database abstraction + cache |
| `sculk-platform` | Paper integration (use this one) |

## Documentation

Full docs at [sculk.gg/docs](https://sculk.gg/docs) — every feature includes Kotlin and Java examples.

## Requirements

- Paper 1.21.11+
- Java 21+

## License

MIT — see [LICENSE](LICENSE).

---

Built with care by [Sculk Studios](https://sculk.gg).
