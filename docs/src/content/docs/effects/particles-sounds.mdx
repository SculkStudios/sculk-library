---
title: Particles & Sounds
description: Clean builder DSLs for spawning particles and playing sounds — no raw Bukkit API calls needed.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

`sculk-effects` wraps Bukkit's particle and sound APIs in clean, readable builders. Instead of calling `world.spawnParticle(...)` with six positional arguments, you use a DSL that names everything.

## Particles

Spawn particles at a location with full control over count, spread, and speed:

<Tabs>
<TabItem label="Kotlin">
```kotlin
particle(Particle.FLAME) {
    location = player.location.add(0.0, 1.0, 0.0)
    count = 20
    offset(0.5, 0.5, 0.5)   // x/y/z spread radius
    speed = 0.1
    spawn()
}
```
</TabItem>
<TabItem label="Java">
```java
JavaParticleBuilder.of(Particle.FLAME)
    .location(player.getLocation().add(0, 1, 0))
    .count(20)
    .offset(0.5, 0.5, 0.5)
    .speed(0.1)
    .spawn();
```
</TabItem>
</Tabs>

### Particle properties

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `location` | `Location` | Required | Where to spawn the particle |
| `count` | `Int` | `1` | Number of particles to spawn |
| `offset(x, y, z)` | `Double` | `0, 0, 0` | Random spread radius on each axis |
| `speed` | `Double` | `0.0` | Particle velocity (behaviour varies by type) |

### Directional particles

For particles like `FIREWORK` that use `offset` as a direction vector, set `count = 0` and provide the direction:

<Tabs>
<TabItem label="Kotlin">
```kotlin
particle(Particle.FIREWORK) {
    location = player.location
    count = 0                    // 0 = directional mode
    offset(0.0, 1.0, 0.0)       // fires straight up
    speed = 0.5
    spawn()
}
```
</TabItem>
<TabItem label="Java">
```java
JavaParticleBuilder.of(Particle.FIREWORK)
    .location(player.getLocation())
    .count(0)
    .offset(0.0, 1.0, 0.0)
    .speed(0.5)
    .spawn();
```
</TabItem>
</Tabs>

### Player-only particles

Spawn particles visible only to a specific player:

<Tabs>
<TabItem label="Kotlin">
```kotlin
particle(Particle.HEART) {
    location = player.location.add(0.0, 2.0, 0.0)
    count = 5
    receivers(player)   // only this player sees them
    spawn()
}
```
</TabItem>
<TabItem label="Java">
```java
JavaParticleBuilder.of(Particle.HEART)
    .location(player.getLocation().add(0, 2, 0))
    .count(5)
    .receivers(player)
    .spawn();
```
</TabItem>
</Tabs>

---

## Sounds

Play sounds to a player or at a world location:

<Tabs>
<TabItem label="Kotlin">
```kotlin
sound(Sound.ENTITY_PLAYER_LEVELUP) {
    volume = 1.0f
    pitch = 1.2f
    playTo(player)
}
```
</TabItem>
<TabItem label="Java">
```java
JavaSoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP)
    .volume(1.0f)
    .pitch(1.2f)
    .playTo(player);
```
</TabItem>
</Tabs>

### Sound properties

| Property | Type | Default | Description |
| --- | --- | --- | --- |
| `volume` | `Float` | `1.0` | Audible distance multiplier |
| `pitch` | `Float` | `1.0` | Playback speed (0.5 = half speed, 2.0 = double) |

### Play at a location

Sounds played at a location are heard by every player within range based on `volume`:

<Tabs>
<TabItem label="Kotlin">
```kotlin
sound(Sound.BLOCK_ANVIL_LAND) {
    volume = 0.8f
    pitch = 0.9f
    playAt(player.location)
}
```
</TabItem>
<TabItem label="Java">
```java
JavaSoundBuilder.of(Sound.BLOCK_ANVIL_LAND)
    .volume(0.8f)
    .pitch(0.9f)
    .playAt(player.getLocation());
```
</TabItem>
</Tabs>

### Custom sounds

Play custom sounds registered in your resource pack:

<Tabs>
<TabItem label="Kotlin">
```kotlin
sound("myplugin:ui.click") {
    volume = 1.0f
    playTo(player)
}
```
</TabItem>
<TabItem label="Java">
```java
JavaSoundBuilder.ofKey("myplugin:ui.click")
    .volume(1.0f)
    .playTo(player);
```
</TabItem>
</Tabs>

---

## Combining particles and sounds

Mix both freely within any handler — command, GUI click, event:

<Tabs>
<TabItem label="Kotlin">
```kotlin
// On a level-up event
fun onLevelUp(player: Player) {
    particle(Particle.END_ROD) {
        location = player.location.add(0.0, 1.0, 0.0)
        count = 40
        offset(0.4, 0.6, 0.4)
        spawn()
    }
    sound(Sound.ENTITY_PLAYER_LEVELUP) {
        pitch = 1.3f
        playTo(player)
    }
}
```
</TabItem>
<TabItem label="Java">
```java
void onLevelUp(Player player) {
    JavaParticleBuilder.of(Particle.END_ROD)
        .location(player.getLocation().add(0, 1, 0))
        .count(40)
        .offset(0.4, 0.6, 0.4)
        .spawn();

    JavaSoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP)
        .pitch(1.3f)
        .playTo(player);
}
```
</TabItem>
</Tabs>

For multi-step animations spread across multiple ticks, see [Timelines & Sequences](/effects/timelines).

## Using with sculk-series

If you're loading particle or sound keys from config, use `sculk-series` to resolve them safely:

<Tabs>
<TabItem label="Kotlin">
```kotlin
val p = SculkSeries.particle(config.particleType) ?: Particle.FLAME
particle(p) {
    location = player.location
    count = 10
    spawn()
}
```
</TabItem>
<TabItem label="Java">
```java
Particle p = SculkSeries.particle(config.getParticleType());
if (p == null) p = Particle.FLAME;
JavaParticleBuilder.of(p).location(player.getLocation()).count(10).spawn();
```
</TabItem>
</Tabs>

## Best practices

- Always set `location` — it is the only required property.
- Use `offset` for ambient effects (smoke, sparkles). Use direction mode (`count = 0`) for effects that need to travel.
- Keep particle counts reasonable — `count = 200` every tick will cause client-side lag for all nearby players.
- Use `playTo(player)` for UI feedback sounds. Use `playAt(location)` for world events heard by everyone nearby.
