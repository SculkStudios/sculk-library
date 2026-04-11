---
title: Architecture
description: How Sculk Studio modules fit together and the dependency flow between them.
---

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

Each module depends only on the modules above it. There are no cycles. `sculk-platform` is the only module that references Paper's plugin lifecycle (`JavaPlugin`).

## Lifecycle model

Everything in Sculk Studio is a `SculkHandle` — a `fun interface` extending `AutoCloseable`. Handles are what you get back when you register a listener, schedule a task, or open a connection pool. Close a handle to cancel or release that resource.

`SculkPlatform` itself is a `SculkHandle`. Calling `sculk.close()` in `onDisable()` tears down every registered resource in reverse-registration order — listeners, commands, GUI sessions, connection pools.

```kotlin
class MyPlugin : JavaPlugin() {
    lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {
            gui()
            config()
            data()
        }
    }

    override fun onDisable() = sculk.close()
}
```

## API stability

Every public symbol carries one of three markers:

| Annotation | Guarantee |
|---|---|
| `@SculkStable` | Semver-protected. Safe to depend on. |
| `@SculkExperimental` | May change in minor releases. |
| `@SculkInternal` | Framework use only. No semver guarantees. Requires opt-in. |
