---
title: API Stability
description: Understanding @SculkStable, @SculkExperimental, and @SculkInternal — what you can depend on and what may change.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

Every public symbol in Sculk Studio carries one of three stability annotations. This system tells you exactly what you can safely depend on and what might change between releases — no surprises, no guessing.

## @SculkStable

```kotlin
@SculkStable
public fun command(name: String, block: CommandBuilder.() -> Unit): CommandBuilder
```

**Semver-protected.** A breaking change to any `@SculkStable` symbol requires a major version bump. All primary DSL entry points — `command {}`, `gui {}`, `sculk.config.load<T>()`, `sculk.data.repository<T, ID>()` — are stable.

If you only use `@SculkStable` symbols, you can upgrade to any `1.x` release without changes to your plugin.

---

## @SculkExperimental

```kotlin
@SculkExperimental
public class AnimationTimeline { ... }
```

The API exists and works, but its shape may change in a **minor** release. Experimental symbols are usually newly-added features still gathering feedback.

To use them without a compiler warning, opt in at the call site:

<Tabs>
<TabItem label="Kotlin">
```kotlin
// Single function opt-in
@OptIn(SculkExperimental::class)
fun myAnimation(player: Player) {
    timeline {
        at(0) { particle(Particle.FLAME) { location = player.location; count = 10; spawn() } }
    }.start(sculk.scheduler)
}
```
</TabItem>
<TabItem label="Java">
```java
// Java — no opt-in annotation needed, but IDE may show a warning
AnimationTimeline timeline = JavaTimeline.builder()
    .at(0, () -> ParticleBuilder.of(Particle.FLAME)
        .location(player.getLocation()).count(10).spawn())
    .build();
```
</TabItem>
</Tabs>

Or suppress for an entire module via your build file:

<Tabs>
<TabItem label="Kotlin">
```kotlin
// build.gradle.kts
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkExperimental")
    }
}
```
</TabItem>
<TabItem label="Java">
```java
// No build change needed for Java — opt-in annotations are Kotlin-only
```
</TabItem>
</Tabs>

---

## @SculkInternal

```kotlin
@SculkInternal
public class SculkCommandBridge { ... }
```

**Framework internals.** No semver guarantees whatsoever — these can change in any patch release. They are `public` only because modules within the Sculk Studio monorepo need to access them; they are not part of the public API surface.

The compiler enforces this: `@SculkInternal` is a `RequiresOptIn` at `Level.ERROR`. Your code will not compile if you use an internal symbol without explicitly opting in:

<Tabs>
<TabItem label="Kotlin">
```kotlin
// build.gradle.kts — only do this if you genuinely need internal access
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkInternal")
    }
}
```
</TabItem>
</Tabs>

Only use `@SculkInternal` symbols if you are building tooling on top of Sculk Studio's internals and you accept that it may break without warning on any release.

---

## Summary

| Annotation | Guarantee | Opt-in required? |
| :--- | :--- | :--- |
| `@SculkStable` | Semver-protected. Safe to depend on across `1.x`. | No |
| `@SculkExperimental` | Works, may change in minor releases. | Yes (`@OptIn`) |
| `@SculkInternal` | No semver. Can break in any release. | Yes (compiler flag, `Level.ERROR`) |

## What to do if you hit a symbol with no annotation

All stable entry points are annotated. If you find a public symbol with no stability annotation, treat it as `@SculkExperimental` and open an issue on GitHub — it likely needs to be triaged.
