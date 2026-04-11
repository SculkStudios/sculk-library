---
title: Hot Reload
description: Reload configs at runtime without restarting the server.
---

Sculk config supports hot reload — reload all configs from disk at runtime and run registered callbacks.

## Triggering a reload

```kotlin
sculk.config.reload()
```

A typical pattern is to expose a `reload` subcommand:

```kotlin
sub("reload") {
    permission = "myplugin.admin"
    player {
        sculk.config.reload()
        reply("<green>Config reloaded.")
    }
}
```

## Reload callbacks

Register a callback to re-read config values after a reload:

```kotlin
var settings = sculk.config.load<Settings>()

sculk.config.onReload {
    settings = sculk.config.load<Settings>()
    logger.info("Settings reloaded: maxHomes=${settings.maxHomes}")
}
```

Callbacks run in registration order, synchronously, after all config files have been re-parsed from disk.

## File versioning and migrations

Add a `configVersion` field to your config to detect when stored values are from an older schema:

```kotlin
@ConfigFile("settings.yml")
data class Settings(
    val configVersion: Int = 2,
    val maxHomes: Int = 5,
)
```

On load, if the file's `config-version` is older than the data-class default, Sculk logs a warning and regenerates the file with current defaults, preserving values for keys that haven't changed.

## Best practices

- Always expose a `/reload` subcommand for operators.
- Use `onReload` to update any live references to config values.
- Increment `configVersion` when you add or rename config keys.
