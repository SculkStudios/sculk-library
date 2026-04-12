---
title: Series Overview
description: Cross-version key lookups for materials, sounds, particles, and more — no hardcoded version checks.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

Minecraft renames keys between versions. A sound called `entity.player.levelup` in 1.8 became `entity.player.level_up` in 1.9. A material called `ROSE` became `RED_FLOWER` then `POPPY`. `sculk-series` resolves these transparently without fragile `if (version == ...)` checks scattered through your code.

## How it works

`SculkSeries` holds a registry for each key type. On first lookup, the key is resolved against the running server version and the result is cached. Every subsequent lookup is a pure cache read — no reflection, no version checks, no overhead.

## Basic usage

<Tabs>
<TabItem label="Kotlin">
```kotlin
val sword    = SculkSeries.material("diamond_sword")   // Material
val levelUp  = SculkSeries.sound("entity.player.levelup")  // Sound
val flame    = SculkSeries.particle("flame")           // Particle
val strength = SculkSeries.enchantment("strength")     // Enchantment
val zombie   = SculkSeries.entityType("zombie")        // EntityType
val speed    = SculkSeries.potionEffect("speed")       // PotionEffectType
val plains   = SculkSeries.biome("plains")             // Biome
```
</TabItem>
<TabItem label="Java">
```java
Material   sword    = SculkSeries.material("diamond_sword");
Sound      levelUp  = SculkSeries.sound("entity.player.levelup");
Particle   flame    = SculkSeries.particle("flame");
Enchantment strength = SculkSeries.enchantment("strength");
EntityType zombie   = SculkSeries.entityType("zombie");
PotionEffectType speed = SculkSeries.potionEffect("speed");
Biome      plains   = SculkSeries.biome("plains");
```
</TabItem>
</Tabs>

All lookups return `null` if the key is unknown on the running server, so you can safely handle unsupported types without crashing.

## Null-safe pattern

<Tabs>
<TabItem label="Kotlin">
```kotlin
val material = SculkSeries.material("netherite_sword")
    ?: SculkSeries.material("diamond_sword")!! // fallback for older servers
```
</TabItem>
<TabItem label="Java">
```java
Material material = SculkSeries.material("netherite_sword");
if (material == null) {
    material = SculkSeries.material("diamond_sword");
}
```
</TabItem>
</Tabs>

## Why use this instead of `Material.valueOf()`?

`Material.valueOf("ROSE")` throws `IllegalArgumentException` on 1.9+ where it was renamed. `SculkSeries.material("rose")` resolves the correct name for the running version and returns `null` instead of throwing.

This matters especially when:
- Your plugin supports a range of Paper versions
- You load material names from config (user input can be any version's name)
- You're building reusable libraries that run on many servers

## Best practices

- Look up keys once at startup and store the result — cache means it's already fast, but a field is even faster.
- Always handle the `null` case: if the key truly doesn't exist on the running server, log clearly and disable the feature.
- Use `sculk-series` for everything external: materials from configs, sounds from messages, particles from effect definitions.
