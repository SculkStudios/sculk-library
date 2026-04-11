---
title: API Stability
description: Understanding @SculkStable, @SculkExperimental, and @SculkInternal.
---

Every public symbol in Sculk Studio is annotated with one of three stability markers.

## @SculkStable

```kotlin
@SculkStable
public fun command(name: String, block: CommandBuilder.() -> Unit): CommandBuilder
```

Semver-protected. Breaking changes to `@SculkStable` symbols require a major version bump. Safe to depend on in your own plugins.

## @SculkExperimental

```kotlin
@SculkExperimental
public class AnimationTimeline { ... }
```

The API exists and works, but its shape may change in minor releases. Opt in at the call site:

```kotlin
@OptIn(SculkExperimental::class)
fun myAnimation() {
    timeline { ... }.start(scheduler)
}
```

Or add the opt-in as a compiler flag for a whole module:

```kotlin
// build.gradle.kts
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkExperimental")
    }
}
```

## @SculkInternal

```kotlin
@SculkInternal
public class SculkCommandBridge { ... }
```

Framework-internal. Not subject to semver. Opt-in is `Level.ERROR` — the compiler will refuse to compile usages without the flag:

```kotlin
// build.gradle.kts
tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=gg.sculk.core.annotation.SculkInternal")
    }
}
```

Only use `@SculkInternal` symbols if you are building tooling on top of Sculk Studio and understand the risk of breakage.

## Rule of thumb

If a symbol is not annotated, check whether it is transitively reachable from `@SculkStable` code. All entry points in the public DSL surface are `@SculkStable`.
