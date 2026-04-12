---
title: Registries Reference
description: All available SculkSeries registries and how to use them.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

`sculk-series` ships registries for every commonly-renamed Minecraft key type. Each registry resolves keys against the running server version and caches results after first access.

## Registry reference

| Method | Returns | Covers |
| --- | --- | --- |
| `SculkSeries.material(key)` | `Material?` | All item/block materials |
| `SculkSeries.sound(key)` | `Sound?` | All sound events |
| `SculkSeries.particle(key)` | `Particle?` | All particle types |
| `SculkSeries.enchantment(key)` | `Enchantment?` | All enchantments |
| `SculkSeries.entityType(key)` | `EntityType?` | All entity types |
| `SculkSeries.potionEffect(key)` | `PotionEffectType?` | All potion effects |
| `SculkSeries.biome(key)` | `Biome?` | All biome types |
| `SculkSeries.gameMode(key)` | `GameMode?` | `survival`, `creative`, `adventure`, `spectator` |
| `SculkSeries.difficulty(key)` | `Difficulty?` | `peaceful`, `easy`, `normal`, `hard` |

## Materials

<Tabs>
<TabItem label="Kotlin">
```kotlin
// By modern key
val diamond = SculkSeries.material("diamond")
val sword   = SculkSeries.material("diamond_sword")

// From a config value — handles any version's naming
val configMaterial = SculkSeries.material(config.iconMaterial)
    ?: run {
        logger.warning("Unknown material '${config.iconMaterial}', defaulting to stone.")
        Material.STONE
    }
```
</TabItem>
<TabItem label="Java">
```java
Material diamond = SculkSeries.material("diamond");

// From a config value with fallback
Material icon = SculkSeries.material(config.getIconMaterial());
if (icon == null) {
    logger.warning("Unknown material '" + config.getIconMaterial() + "', defaulting to stone.");
    icon = Material.STONE;
}
```
</TabItem>
</Tabs>

## Sounds

<Tabs>
<TabItem label="Kotlin">
```kotlin
// Play a level-up sound — key resolves regardless of version naming
val levelUp = SculkSeries.sound("entity.player.levelup")
if (levelUp != null) {
    player.playSound(player.location, levelUp, 1.0f, 1.0f)
}

// Or with sculk-effects:
sound(SculkSeries.sound("entity.player.levelup") ?: Sound.ENTITY_EXPERIENCE_ORB_PICKUP) {
    playTo(player)
}
```
</TabItem>
<TabItem label="Java">
```java
Sound levelUp = SculkSeries.sound("entity.player.levelup");
if (levelUp != null) {
    player.playSound(player.getLocation(), levelUp, 1.0f, 1.0f);
}
```
</TabItem>
</Tabs>

## Particles

<Tabs>
<TabItem label="Kotlin">
```kotlin
val flame = SculkSeries.particle("flame")
if (flame != null) {
    particle(flame) {
        location = player.location.add(0.0, 1.0, 0.0)
        count = 20
        spawn()
    }
}
```
</TabItem>
<TabItem label="Java">
```java
Particle flame = SculkSeries.particle("flame");
if (flame != null) {
    ParticleBuilder.of(flame)
        .location(player.getLocation().add(0, 1, 0))
        .count(20)
        .spawn();
}
```
</TabItem>
</Tabs>

## Enchantments

<Tabs>
<TabItem label="Kotlin">
```kotlin
val sharpness = SculkSeries.enchantment("sharpness")
if (sharpness != null) {
    sword.addEnchantment(sharpness, 5)
}
```
</TabItem>
<TabItem label="Java">
```java
Enchantment sharpness = SculkSeries.enchantment("sharpness");
if (sharpness != null) {
    sword.addEnchantment(sharpness, 5);
}
```
</TabItem>
</Tabs>

## Entity types

<Tabs>
<TabItem label="Kotlin">
```kotlin
val zombie = SculkSeries.entityType("zombie")
val warden = SculkSeries.entityType("warden") // null on servers that predate 1.19

zombie?.let { world.spawnEntity(location, it) }
```
</TabItem>
<TabItem label="Java">
```java
EntityType zombie = SculkSeries.entityType("zombie");
EntityType warden = SculkSeries.entityType("warden"); // null on pre-1.19

if (zombie != null) world.spawnEntity(location, zombie);
```
</TabItem>
</Tabs>

## Potion effects

<Tabs>
<TabItem label="Kotlin">
```kotlin
val speed = SculkSeries.potionEffect("speed")
if (speed != null) {
    player.addPotionEffect(PotionEffect(speed, 200, 1))
}
```
</TabItem>
<TabItem label="Java">
```java
PotionEffectType speed = SculkSeries.potionEffect("speed");
if (speed != null) {
    player.addPotionEffect(new PotionEffect(speed, 200, 1));
}
```
</TabItem>
</Tabs>

## Custom registries

If you need to look up a type not covered by the built-in registries, you can extend the system:

<Tabs>
<TabItem label="Kotlin">
```kotlin
// Register a custom resolver
SculkSeries.register<MyType> { key ->
    MyType.values().firstOrNull { it.name.equals(key, ignoreCase = true) }
}

// Look up a value
val value = SculkSeries.lookup<MyType>("my_key")
```
</TabItem>
<TabItem label="Java">
```java
SculkSeries.register(MyType.class, key ->
    Arrays.stream(MyType.values())
        .filter(t -> t.name().equalsIgnoreCase(key))
        .findFirst()
        .orElse(null)
);

MyType value = SculkSeries.lookup(MyType.class, "my_key");
```
</TabItem>
</Tabs>

## Performance note

The first call to any registry method does the actual lookup (name matching, namespace resolution). All subsequent calls for the same key return from a `ConcurrentHashMap` — effectively free. Pre-warm critical lookups at plugin startup:

<Tabs>
<TabItem label="Kotlin">
```kotlin
override fun onEnable() {
    sculk = SculkPlatform.create(this) { ... }

    // Pre-warm — ensures any missing-key warnings appear at startup, not mid-game
    val sword  = SculkSeries.material("diamond_sword")
    val levelUp = SculkSeries.sound("entity.player.levelup")
}
```
</TabItem>
<TabItem label="Java">
```java
@Override
public void onEnable() {
    // Pre-warm at startup so missing-key warnings surface immediately
    Material sword  = SculkSeries.material("diamond_sword");
    Sound   levelUp = SculkSeries.sound("entity.player.levelup");
}
```
</TabItem>
</Tabs>
