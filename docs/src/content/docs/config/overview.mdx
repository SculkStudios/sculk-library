---
title: Config Overview
description: Typed YAML configs that write their own defaults, validate themselves, and hot-reload.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

The config system maps YAML files to Kotlin data classes. Missing keys are written automatically on first load. Everything is typed — no more `getString("some.key")`.

## Defining a config

Annotate a data class with `@ConfigFile`. Field names map to kebab-case YAML keys automatically.

<Tabs>
<TabItem label="Kotlin">
```kotlin
@ConfigFile("settings.yml")
data class Settings(
    val prefix: String = "<gray>[<aqua>Plugin<gray>] ",
    val maxHomes: Int = 5,
    val allowFlight: Boolean = false,
    val spawnWorld: String = "world",
)
```

Generated `settings.yml`:
```yaml
prefix: "<gray>[<aqua>Plugin<gray>] "
max-homes: 5
allow-flight: false
spawn-world: world
```
</TabItem>
<TabItem label="Java">
```java
@ConfigFile("settings.yml")
public record Settings(
    String prefix,
    int maxHomes,
    boolean allowFlight,
    String spawnWorld
) {
    public Settings() {
        this("<gray>[<aqua>Plugin<gray>] ", 5, false, "world");
    }
}
```
</TabItem>
</Tabs>

## Loading

<Tabs>
<TabItem label="Kotlin">
```kotlin
val settings = sculk.config.load<Settings>()
```
</TabItem>
<TabItem label="Java">
```java
Settings settings = sculk.getConfig().load(Settings.class);
```
</TabItem>
</Tabs>

On first run the YAML file is created with all defaults. On subsequent runs any missing keys are added — existing values are never overwritten.

## Validation

<Tabs>
<TabItem label="Kotlin">
```kotlin
@ConfigFile("settings.yml")
data class Settings(
    @Min(1) @Max(50) val maxHomes: Int = 5,
    @NotEmpty val prefix: String = "<gray>[<aqua>Plugin<gray>] ",
)
```
</TabItem>
<TabItem label="Java">
```java
@ConfigFile("settings.yml")
public record Settings(
    @Min(1) @Max(50) int maxHomes,
    @NotEmpty String prefix
) { ... }
```
</TabItem>
</Tabs>

Invalid values log a warning and fall back to the field's default — the plugin never crashes on bad config.

## Lists, maps, enums, and UUIDs

The config system supports collections, enums, and UUIDs out of the box:

<Tabs>
<TabItem label="Kotlin">
```kotlin
enum class Difficulty { EASY, NORMAL, HARD }

@ConfigFile("settings.yml")
data class Settings(
    val allowedWorlds: List<String> = listOf("world", "world_nether"),
    val rewards: Map<String, Int> = mapOf("daily" to 100, "weekly" to 500),
    val difficulty: Difficulty = Difficulty.NORMAL,
    val adminUuid: UUID? = null,
)
```

Generated YAML:
```yaml
allowed-worlds:
  - world
  - world_nether
rewards:
  daily: 100
  weekly: 500
difficulty: NORMAL
admin-uuid: null
```
</TabItem>
<TabItem label="Java">
```java
@ConfigFile("settings.yml")
public record Settings(
    List<String> allowedWorlds,
    Map<String, Integer> rewards,
    Difficulty difficulty,
    UUID adminUuid
) {
    public Settings() {
        this(List.of("world", "world_nether"), Map.of("daily", 100), Difficulty.NORMAL, null);
    }
}
```
</TabItem>
</Tabs>

Enum values are matched case-insensitively — `NORMAL`, `normal`, and `Normal` all resolve to the same constant.

## Message configs

Messages are just another config file with string values. Use MiniMessage tags freely.

<Tabs>
<TabItem label="Kotlin">
```kotlin
@ConfigFile("messages.yml")
data class Messages(
    val noPermission: String = "<red>You don't have permission.",
    val homeSet: String = "<green>Home <white>{name} <green>set.",
    val homeTeleport: String = "<aqua>Teleporting to <white>{name}<aqua>…",
)

val msg = sculk.config.load<Messages>()

// usage
player.sendMessage(msg.homeSet.replace("{name}", homeName))
```
</TabItem>
<TabItem label="Java">
```java
@ConfigFile("messages.yml")
public record Messages(String noPermission, String homeSet, String homeTeleport) {
    public Messages() {
        this(
            "<red>You don't have permission.",
            "<green>Home <white>{name} <green>set.",
            "<aqua>Teleporting to <white>{name}<aqua>…"
        );
    }
}

Messages msg = sculk.getConfig().load(Messages.class);
player.sendMessage(msg.homeSet().replace("{name}", homeName));
```
</TabItem>
</Tabs>

## Best practices

- One `@ConfigFile` per logical group: `settings.yml`, `messages.yml`, `economy.yml`.
- Keep defaults sensible for a fresh server install — operators should never need to touch the file just to get running.
- Use `@Min`/`@Max` on any numeric field where an out-of-range value would cause a bug.
