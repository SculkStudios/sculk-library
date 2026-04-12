---
title: What is Sculk Studio?
description: A production-grade Kotlin-first framework for building Minecraft Paper plugins — designed by Sculk Studios.
---

Sculk Studio is a **production-grade, Kotlin-first framework** for building Minecraft Paper plugins. It was built by Sculk Studios to power all internal plugins and open-sourced for everyone.

Instead of wrestling with Bukkit boilerplate — manual command parsing, raw `InventoryClickEvent` handling, `getConfig().getString("path.to.value")` everywhere — Sculk Studio gives you clean DSLs, automatic lifecycle management, and typed everything.

## What it looks like

Here's a full working command with tab-completion, a typed config, and a GUI — the kind of thing that would take hundreds of lines with raw Bukkit:

```kotlin
@ConfigFile("settings.yml")
data class Settings(val maxHomes: Int = 5)

val homeMenu = gui("<dark_aqua>Your Homes") {
    size = 27
    item(13) {
        material = Material.COMPASS
        name = "<aqua>Set Home"
        onClick {
            reply("<green>Home set!")
            close()
        }
    }
}

fun homeCommand(sculk: SculkPlatform) = command("home") {
    permission = "homes.use"

    sub("set") {
        string("name")
        player {
            val name = argument<String>("name")
            val settings = sculk.config.load<Settings>()
            reply("<green>Home '$name' set. Max homes: ${settings.maxHomes}")
        }
    }

    sub("menu") {
        player {
            homeMenu.openFor(player!!)
        }
    }
}
```

No event listeners. No `onCommand`. No switch statements. No manual tab-completion registration.

## Core philosophy

**Kotlin-first, Java-friendly.** Every DSL has a paired Java builder. Nothing in the public API is Kotlin-only.

**Zero boilerplate.** Commands register themselves. Config files write their own defaults. GUI sessions clean up when players disconnect. You define the behaviour; Sculk Studio handles the plumbing.

**Paper-first.** Sculk Studio targets Paper 1.21.x and uses its modern APIs directly. There are no Spigot/Bukkit compatibility shims slowing things down.

**Adventure-only.** MiniMessage is the single text format. No legacy colour codes, no `ChatColor.GREEN`, no mixed string escaping. Every message is a clean MiniMessage string.

**Performance-first.** Zero blocking on the main thread. Reflection runs once at startup and is cached. GUI inventory updates are batched per tick. The data layer uses HikariCP connection pooling and Caffeine for in-memory caching.

**Modular.** Each module (`sculk-core`, `sculk-config`, `sculk-data`, …) is a standalone Gradle artifact. Include only what you need.

## Modules at a glance

| Module | What it does |
| --- | --- |
| `sculk-core` | Command DSL, GUI system, Adventure messaging, scheduler contracts |
| `sculk-config` | Typed YAML configs, hot reload, validation annotations, message files |
| `sculk-series` | Cross-version key lookup for materials, sounds, particles, entities, and more |
| `sculk-effects` | Particle builders, sound builders, animation timelines, effect sequences |
| `sculk-data` | Repository pattern, SQLite + MySQL, Caffeine-backed in-memory cache |
| `sculk-platform` | Wires everything into a single Paper plugin bootstrap entry point |

## What Sculk Studio is not

- A replacement for Paper's API — it wraps and extends it, not hides it.
- A cross-version compatibility shim — `sculk-series` handles key lookups, not API differences.
- Something you need a framework degree for — if you know Kotlin and Paper, you can read the DSL on first sight.
