---
title: Config Overview
description: Typed YAML configs with auto-generation, validation, and MiniMessage message support.
---

`sculk-config` maps YAML files to Kotlin data classes automatically. Missing keys are written with their defaults on first load. Fields use kebab-case in YAML and camelCase in Kotlin.

## Defining a config

```kotlin
@ConfigFile("settings.yml")
data class Settings(
    val maxHomes: Int = 5,           // → max-homes in YAML
    val allowFlight: Boolean = false,
    val prefix: String = "<gray>[<aqua>MyPlugin<gray>]",
)
```

## Loading

```kotlin
val settings = sculk.config.load<Settings>()
```

On first run, `settings.yml` is created in the plugin's data folder with all defaults. On subsequent runs, any missing keys are added automatically.

## Validation

```kotlin
@ConfigFile("settings.yml")
data class Settings(
    @Min(1) @Max(50) val maxHomes: Int = 5,
    @NotEmpty val prefix: String = "<gray>[<aqua>Plugin<gray>]",
)
```

If validation fails, Sculk logs a clear error and falls back to the default value.

## Message configs

Messages are just config files with string values:

```kotlin
@ConfigFile("messages.yml")
data class Messages(
    val noPermission: String = "<red>You don't have permission.",
    val homeSet: String = "<green>Home '<gold>{name}<green>' set.",
)

val messages = sculk.config.load<Messages>()
player.sendMessage(messages.homeSet.replace("{name}", homeName))
```

## Java API

```java
Settings settings = sculk.getConfig().load(Settings.class);
```

## Best practices

- Use one `@ConfigFile` per logical group (settings, messages, economy, …).
- Keep default values sensible for a fresh server install.
- Use `@Min`/`@Max` on numeric fields where invalid values could cause errors.
