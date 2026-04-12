---
title: Architecture
description: How Sculk Studio modules fit together, the lifecycle model, and the dependency flow.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

## Module dependency flow

```
sculk-core
    ↓
sculk-config
    ↓
sculk-series
    ↓
sculk-effects
    ↓
sculk-data
    ↓
sculk-platform
```

Each module depends only on those above it. There are no cycles. `sculk-platform` is the only module that touches Paper's `JavaPlugin` lifecycle — everything else is pure logic that you can test without a running server.

## What each module does

**`sculk-core`** — the foundation. Defines the command DSL, GUI system, Adventure messaging wrapper, and `SculkScheduler`. No Paper plugin lifecycle here — just the logic.

**`sculk-config`** — typed YAML configs. Annotate a data class with `@ConfigFile`, call `sculk.config.load<T>()`, and you have a typed, validated, hot-reloadable config. Defaults are written on first run. Invalid values fall back to defaults rather than crashing.

**`sculk-series`** — cross-version key resolution. Minecraft renames materials, sounds, and particles between versions. `SculkSeries` resolves by name against the running server and caches the result. Zero overhead after first access.

**`sculk-effects`** — particle and sound builders. Clean DSLs for spawning particles, playing sounds, and composing multi-step animations via timelines and sequences.

**`sculk-data`** — persistent storage. Repository pattern over SQLite (default) and MySQL. Caffeine-backed cache layer. All database calls are async — never blocking the main thread.

**`sculk-platform`** — the glue. Registers commands into Paper's command map, routes inventory events to GUI sessions, wires the scheduler to Paper's scheduler, initialises connection pools, and tears everything down cleanly on `onDisable()`.

## The lifecycle model

Everything in Sculk Studio that holds a resource implements `SculkHandle` — a `fun interface` extending `AutoCloseable`. Listener registrations, scheduled tasks, GUI sessions, connection pools — all of them are handles.

`SculkPlatform` is itself a handle. Calling `sculk.close()` in `onDisable()` tears down every registered resource in reverse-registration order, deterministically, with no leaks.

<Tabs>
<TabItem label="Kotlin">
```kotlin
class MyPlugin : JavaPlugin() {
    lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {
            gui()       // enables GUI session management
            config()    // enables hot-reload config system
            data()      // initialises HikariCP pool + runs migrations
        }

        // Register commands (not part of the builder — commands are registered via sculk.commands)
        sculk.commands.registerAll(
            homeCommand(sculk),
            adminCommand(sculk),
        )

        // Wire a config reload to a command
        sculk.config.onReload {
            logger.info("Config reloaded.")
        }
    }

    override fun onDisable(): Unit = sculk.close()
}
```
</TabItem>
<TabItem label="Java">
```java
public class MyPlugin extends JavaPlugin {
    private SculkPlatform sculk;

    @Override
    public void onEnable() {
        sculk = JavaSculkPlatform.create(this, cfg -> cfg
            .gui()
            .config()
            .data()
        );

        sculk.getCommands().registerAll(
            HomeCommand.build(sculk),
            AdminCommand.build(sculk)
        );
    }

    @Override
    public void onDisable() {
        sculk.close();
    }
}
```
</TabItem>
</Tabs>

## SculkResult — typed error handling

Operations that can fail return `SculkResult<T>` instead of throwing. This makes error paths explicit and forces you to handle them:

<Tabs>
<TabItem label="Kotlin">
```kotlin
when (val result = repo.find(player.uniqueId)) {
    is SculkResult.Success -> {
        val data = result.value
        player.sendMessage("Coins: ${data?.coins ?: 0}")
    }
    is SculkResult.Failure -> {
        logger.warning("Load failed: ${result.message}")
    }
}
```
</TabItem>
<TabItem label="Java">
```java
SculkResult<PlayerData> result = repo.find(player.getUniqueId());
if (result instanceof SculkResult.Success<PlayerData> success) {
    PlayerData data = success.getValue();
    player.sendMessage("Coins: " + (data != null ? data.getCoins() : 0));
} else if (result instanceof SculkResult.Failure failure) {
    getLogger().warning("Load failed: " + failure.getMessage());
}
```
</TabItem>
</Tabs>

## API stability markers

Every public symbol carries one of three markers so you know exactly what you can rely on:

| Annotation | Guarantee |
| --- | --- |
| `@SculkStable` | Semver-protected. Breaking changes require a major version bump. Safe to depend on. |
| `@SculkExperimental` | The API works but its shape may change in a minor release. Opt in with `@OptIn`. |
| `@SculkInternal` | Framework internals. No semver guarantees. Requires compiler opt-in flag to use. |

See [API Stability](/advanced/api-stability) for details on how to use opt-in annotations.
