---
title: Timelines & Sequences
description: Compose multi-step particle and sound animations across ticks — with looping, cancellation, and async safety.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

When an effect needs to play out over multiple ticks — a charge-up animation, a countdown, a looping ambient effect — use a `timeline` or `sequence`. Both are scheduled through `SculkScheduler` and run on the main thread, so Paper APIs are safe to call inside them.

## Timeline

A `timeline` schedules actions at specific **absolute tick offsets** from when `start()` is called. Use it when you know exactly what should happen at tick 0, tick 10, tick 20, and so on.

<Tabs>
<TabItem label="Kotlin">
```kotlin
val origin = player.location.clone()

val handle = timeline {
    at(0) {
        // tick 0 — initial burst
        particle(Particle.SMOKE_NORMAL) {
            location = origin
            count = 15
            offset(0.3, 0.3, 0.3)
            spawn()
        }
    }
    at(10) {
        // tick 10 — sound cue
        sound(Sound.ENTITY_BLAZE_AMBIENT) {
            pitch = 0.8f
            playTo(player)
        }
    }
    at(20) {
        // tick 20 — release burst
        particle(Particle.FLAME) {
            location = origin
            count = 40
            offset(0.5, 0.8, 0.5)
            speed = 0.05
            spawn()
        }
        sound(Sound.ENTITY_BLAZE_SHOOT) {
            pitch = 1.2f
            playTo(player)
        }
    }
    loop(3)  // repeat the whole 20-tick sequence 3 times total
}.start(sculk.scheduler)
```
</TabItem>
<TabItem label="Java">
```java
Location origin = player.getLocation().clone();

SculkHandle handle = JavaTimeline.builder()
    .at(0, () -> {
        ParticleBuilder.of(Particle.SMOKE_NORMAL)
            .location(origin).count(15).offset(0.3, 0.3, 0.3).spawn();
    })
    .at(10, () -> {
        SoundBuilder.of(Sound.ENTITY_BLAZE_AMBIENT)
            .pitch(0.8f).playTo(player);
    })
    .at(20, () -> {
        ParticleBuilder.of(Particle.FLAME)
            .location(origin).count(40).offset(0.5, 0.8, 0.5).speed(0.05).spawn();
        SoundBuilder.of(Sound.ENTITY_BLAZE_SHOOT)
            .pitch(1.2f).playTo(player);
    })
    .loop(3)
    .start(sculk.getScheduler());
```
</TabItem>
</Tabs>

### Cancelling a timeline

`start()` returns a `SculkHandle`. Call `close()` on it to cancel the timeline at any point — useful if the player dies, disconnects, or the ability is interrupted:

<Tabs>
<TabItem label="Kotlin">
```kotlin
val handle = timeline { ... }.start(sculk.scheduler)

// Cancel early (e.g. player took damage)
handle.close()
```
</TabItem>
<TabItem label="Java">
```java
SculkHandle handle = JavaTimeline.builder()
    /* ... */
    .start(sculk.getScheduler());

// Cancel early
handle.close();
```
</TabItem>
</Tabs>

### Looping

`loop(n)` repeats the timeline `n` times total (including the first run). `loop(0)` or omitting it means play once. There is no infinite loop option — always provide a finite count to avoid runaway effects.

---

## Sequence

A `sequence` builds a chain of steps with **accumulated delays**. Instead of specifying absolute tick offsets, you declare how long to wait between each step. Use it when relative timing matters more than absolute positions.

<Tabs>
<TabItem label="Kotlin">
```kotlin
val origin = player.location.clone()

sequence {
    step {
        // immediately
        particle(Particle.SMOKE_NORMAL) {
            location = origin
            count = 5
            spawn()
        }
    }
    delay(5)   // wait 5 ticks
    step {
        particle(Particle.FLAME) {
            location = origin
            count = 10
            spawn()
        }
    }
    delay(5)   // wait another 5 ticks (10 total from start)
    step {
        particle(Particle.END_ROD) {
            location = origin.add(0.0, 0.5, 0.0)
            count = 30
            offset(0.4, 0.4, 0.4)
            spawn()
        }
        sound(Sound.ENTITY_GENERIC_EXPLODE) {
            volume = 0.6f
            pitch = 1.4f
            playTo(player)
        }
    }
}.start(sculk.scheduler)
```
</TabItem>
<TabItem label="Java">
```java
JavaSequence.builder()
    .step(() -> {
        ParticleBuilder.of(Particle.SMOKE_NORMAL)
            .location(origin).count(5).spawn();
    })
    .delay(5)
    .step(() -> {
        ParticleBuilder.of(Particle.FLAME)
            .location(origin).count(10).spawn();
    })
    .delay(5)
    .step(() -> {
        ParticleBuilder.of(Particle.END_ROD)
            .location(origin.clone().add(0, 0.5, 0))
            .count(30).offset(0.4, 0.4, 0.4).spawn();
        SoundBuilder.of(Sound.ENTITY_GENERIC_EXPLODE)
            .volume(0.6f).pitch(1.4f).playTo(player);
    })
    .start(sculk.getScheduler());
```
</TabItem>
</Tabs>

### Timeline vs Sequence — when to use which

| | Timeline | Sequence |
| --- | --- | --- |
| Timing style | Absolute tick offsets | Relative delays between steps |
| Looping | Yes (`loop(n)`) | No (use a timeline for looping) |
| Cancellation | Yes (`handle.close()`) | Yes (`handle.close()`) |
| Best for | Looping ambience, structured animations | One-shot multi-phase effects |

---

## Async safety

All `at { }` and `step { }` blocks run on the **main server thread** — scheduled by `SculkScheduler` via Paper's scheduler. It is safe to call any Paper API (entity manipulation, world interaction, GUI updates) inside these blocks.

Do not run heavy computation inside timeline steps. If you need to compute something async before an effect, do the computation first and capture the result in a `val` that the timeline closes over.

---

## Use cases

- **Ability charge-up:** smoke → glow → release burst
- **Countdown:** "3… 2… 1… Go!" with sound + particles at each number
- **Environmental loops:** campfire smoke, portal ambience, arena borders
- **Quest cinematics:** sequential sound + particle narrative
- **Death effects:** fade-out ring expanding from player location
