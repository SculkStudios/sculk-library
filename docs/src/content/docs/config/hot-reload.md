---
title: Hot Reload
description: Reload configs at runtime without restarting the server.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

## Triggering a reload

<Tabs>
<TabItem label="Kotlin">
```kotlin
sculk.config.reload()
```
</TabItem>
<TabItem label="Java">
```java
sculk.getConfig().reload();
```
</TabItem>
</Tabs>

Wire it to a reload subcommand so operators don't need a restart:

<Tabs>
<TabItem label="Kotlin">
```kotlin
fun adminCommand(sculk: SculkPlatform) = command("myplugin") {
    permission = "myplugin.admin"

    sub("reload") {
        player {
            sculk.config.reload()
            reply("<green>Config reloaded.")
        }
    }
}
```
</TabItem>
<TabItem label="Java">
```java
JavaCommand.builder("myplugin")
    .permission("myplugin.admin")
    .sub("reload", sub -> sub
        .player(ctx -> {
            sculk.getConfig().reload();
            ctx.reply("<green>Config reloaded.");
        })
    );
```
</TabItem>
</Tabs>

## Reload callbacks

Register a callback to re-read live config references after a reload:

<Tabs>
<TabItem label="Kotlin">
```kotlin
var settings = sculk.config.load<Settings>()

sculk.config.onReload {
    settings = sculk.config.load<Settings>()
}
```
</TabItem>
<TabItem label="Java">
```java
Settings[] settings = { sculk.getConfig().load(Settings.class) };

sculk.getConfig().onReload(() -> {
    settings[0] = sculk.getConfig().load(Settings.class);
});
```
</TabItem>
</Tabs>

Callbacks run in registration order, synchronously, after all files have been re-parsed.

## File versioning

Add a `configVersion` field to detect stale configs:

<Tabs>
<TabItem label="Kotlin">
```kotlin
@ConfigFile("settings.yml")
data class Settings(
    val configVersion: Int = 2,
    val maxHomes: Int = 5,
)
```
</TabItem>
<TabItem label="Java">
```java
@ConfigFile("settings.yml")
public record Settings(int configVersion, int maxHomes) {
    public Settings() { this(2, 5); }
}
```
</TabItem>
</Tabs>

If the file's `config-version` is lower than the data class default, Sculk logs a warning and regenerates the file — preserving values for keys that still exist.

## Best practices

- Always expose `/yourplugin reload` so operators never need a full server restart for config changes.
- Use `onReload` to update every live variable that reads from config.
- Increment `configVersion` whenever you add, rename, or remove config keys.
