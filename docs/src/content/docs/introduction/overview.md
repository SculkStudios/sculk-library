---
title: What is Sculk Studio?
description: An overview of the Sculk Studio framework and its core philosophy.
---

Sculk Studio is a **production-grade, Kotlin-first framework** for building Minecraft Paper plugins. It replaces the boilerplate-heavy patterns common in plugin development with clean DSLs, smart defaults, and automatic lifecycle management.

## Core philosophy

- **Kotlin-first, Java-friendly** — every DSL has a paired Java builder.
- **Zero boilerplate** — commands register themselves, listeners clean up automatically, configs generate their own defaults.
- **Paper-first** — targets Paper 1.21.x. No Spigot/Bukkit compatibility shims.
- **Adventure-only** — MiniMessage is the single text format. No legacy colour codes.
- **Performance-first** — zero blocking on the main thread, reflection cached at startup, GUI updates batched per tick.
- **Modular** — each module (`sculk-core`, `sculk-config`, `sculk-data`, …) is standalone but they integrate seamlessly.

## What Sculk Studio is not

- A replacement for Paper's API — it wraps and extends it.
- A cross-version compatibility library — use `sculk-series` only for key lookups, not version shims.
- A runtime dependency you shade away — it is designed to be shaded into your plugin jar.

## Modules at a glance

| Module | Purpose |
|---|---|
| `sculk-core` | Commands, GUIs, Adventure wrapper, scheduler contracts |
| `sculk-config` | Typed YAML configs, hot reload, validation |
| `sculk-series` | Cross-version key lookup for materials, sounds, etc. |
| `sculk-effects` | Particle/sound builders, animation timelines |
| `sculk-data` | Repository pattern, SQLite/MySQL, Caffeine cache |
| `sculk-platform` | Wires all modules into a single Paper plugin bootstrap |
