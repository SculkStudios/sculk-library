---
title: Timelines & Sequences
description: Compose tick-accurate particle and sound animations.
---

## Animation timeline

A `timeline` lets you schedule actions at specific tick offsets, with optional looping.

```kotlin
val origin = player.location

val handle = timeline {
    at(0) {
        particle(Particle.FLAME) {
            location = origin
            count = 10
            spawn()
        }
    }
    at(10) {
        sound(Sound.ENTITY_PLAYER_LEVELUP) {
            playTo(player)
        }
    }
    at(20) {
        particle(Particle.END_ROD) {
            location = origin.add(0.0, 1.0, 0.0)
            count = 30
            spawn()
        }
    }
    loop(3)  // repeat 3 times total
}.start(sculk.scheduler)
```

`start()` returns a `SculkHandle`. Call `handle.close()` to cancel the timeline early.

## Sequence

A `sequence` builds a chain of steps with accumulated delays:

```kotlin
sequence {
    step {
        particle(Particle.SMOKE_NORMAL) { location = origin; count = 5; spawn() }
    }
    delay(5)
    step {
        particle(Particle.FLAME) { location = origin; count = 10; spawn() }
    }
    delay(10)
    step {
        sound(Sound.ENTITY_BLAZE_DEATH) { playTo(player) }
    }
}.start(sculk.scheduler)
```

Steps run at their accumulated tick offset from when `start()` is called.

## Async safety

All scheduling goes through `SculkScheduler`. Actions in `at { }` and `step { }` blocks run on the main server thread — safe to call Paper APIs directly.

## Use cases

- Ability animations (charge-up → release → trail)
- Countdown effects (3… 2… 1… Go!)
- Environmental loops (campfire smoke, portal ambience)
- Quest or event cutscenes
