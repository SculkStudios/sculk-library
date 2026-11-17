# Sculk Studio

> The Kotlin platform for Paper, Folia, Canvas and Velocity plugins.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Sculk 6 is in development on this branch.** It is a ground-up rewrite; nothing here is released
yet. Sculk 5 lives on [`main`](https://github.com/SculkStudios/sculk-library/tree/main) until 6.0 ships.

## What it is

The infrastructure most serious plugins end up building for themselves, built once and tested on
real servers:

- **Threading by ownership.** Work names what it must own (the global region, a region, an entity or
  nothing), and the same code runs correctly on Paper, Folia and Canvas.
- **One API across versions.** Paper 1.21.4 through 26.3; each feature reports whether the running
  server supports it natively, emulated, degraded or not at all, so code never compares versions.
- **Specs as data.** Commands, items, menus and dialogs are immutable values compiled for the
  platform, so they can be tested without a server.
- **Native commands.** Commands compile to Paper's and Velocity's own Brigadier trees, so clients
  validate and complete arguments themselves.
- **Safe text.** Player values enter MiniMessage as placeholders and are never parsed as tags.
- **Modular.** Depend on one module or on all of them.

## Platforms

| Platform | Versions | Java |
| --- | --- | --- |
| Paper | 1.21.4, 1.21.11, 26.1.2, 26.2, 26.3 | 21 for 1.21.x, 25 for 26.x |
| Folia | 26.3, 26.2 (beta), 26.1.2, 1.21.11, 1.21.8, 1.21.6, 1.21.5, 1.21.4 | as Paper |
| Canvas | 26.3, 26.2, 26.1.2, 1.21.11, 1.21.8 | 25 |
| Velocity | 4.3.1 | 25 |

Published artifacts are JVM 21 bytecode and run on both Java generations. Detail in
[`COMPATIBILITY.md`](COMPATIBILITY.md).

## Status

| Milestone | State |
| --- | --- |
| M0 research and architecture | done |
| M1 build engineering | done; its supply-chain checks moved to M3, where they are in force, and M7 |
| M2 core, platforms, concurrency | done: `sculk-core`, `sculk-coroutines`, `sculk-platform-api`, `sculk-testkit`, `sculk-paper`, `sculk-canvas`, `sculk-velocity`, threading proven on real Paper, Folia, Canvas and Velocity |
| M3 author experience | done: text, config, commands, events, placeholders and permissions |
| M4 content and UI | done: items, registry, recipes, menus, dialogs, presentation and the in-game config editor |
| M5 world, effects, data and network | in progress: holograms (`sculk-scenes`), NPCs (`sculk-npcs`) and game rules and time (`sculk-world`) built; effects, data, network, packets and integrations next |
| M6 and M7 | planned: tooling (Gradle plugin, BOM, project generator); hardening and the release candidate |

Which modules exist today and which are planned is in [`MODULES.md`](MODULES.md). The full plan is
in [`REBUILD.md`](REBUILD.md); decisions are in [`docs/adr`](docs/adr).

## Building

```
./gradlew build
```

Needs JDK 21 and JDK 25 installed or auto-provisioned; Gradle's toolchain resolver handles the rest.

## License

MIT. Optional adapters for GPL libraries compile against them without bundling them; see
[`LICENSING.md`](LICENSING.md).
