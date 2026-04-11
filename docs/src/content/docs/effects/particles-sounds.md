---
title: Particles & Sounds
description: Builder DSLs for spawning particles and playing sounds.
---

`sculk-effects` provides clean builders for both particles and sounds.

## Particles

```kotlin
particle(Particle.FLAME) {
    location = player.location
    count = 20
    offset(0.5, 0.5, 0.5)  // x, y, z spread
    speed = 0.1
    spawn()
}
```

| Property | Type | Default |
|---|---|---|
| `location` | `Location` | Required |
| `count` | `Int` | `1` |
| `speed` | `Double` | `0.0` |
| `offset(x, y, z)` | — | `0.0, 0.0, 0.0` |

## Sounds

```kotlin
sound(Sound.ENTITY_PLAYER_LEVELUP) {
    volume = 1.0f
    pitch = 1.2f
    playTo(player)
}
```

| Property | Type | Default |
|---|---|---|
| `volume` | `Float` | `1.0` |
| `pitch` | `Float` | `1.0` |

### Play at a location

```kotlin
sound(Sound.BLOCK_ANVIL_LAND) {
    volume = 0.8f
    playAt(location)
}
```

## Combining them

Particles and sounds compose naturally with Kotlin's standard control flow:

```kotlin
player {
    particle(Particle.END_ROD) {
        location = player!!.location.add(0.0, 1.0, 0.0)
        count = 30
        offset(0.3, 0.5, 0.3)
        spawn()
    }
    sound(Sound.ENTITY_PLAYER_LEVELUP) {
        pitch = 1.5f
        playTo(player!!)
    }
}
```

## Java API

```java
ParticleBuilder.of(Particle.FLAME)
    .location(player.getLocation())
    .count(20)
    .offset(0.5, 0.5, 0.5)
    .speed(0.1)
    .spawn();

SoundBuilder.of(Sound.ENTITY_PLAYER_LEVELUP)
    .volume(1.0f)
    .pitch(1.2f)
    .playTo(player);
```
