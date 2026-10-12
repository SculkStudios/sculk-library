# Sculk Studio

> The Kotlin platform for Paper, Folia, Canvas and Velocity plugins.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg?branch=sculk-6)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Sculk 6 is in development on this branch.** It is a ground-up rewrite; nothing here is released
yet. Sculk 5 lives on [`release/5.x`](https://github.com/SculkStudios/sculk-library/tree/release/5.x).

## What it is

The infrastructure most serious plugins end up building for themselves, done once and done
correctly:

- **Ownership, not "sync".** Work declares whether it needs the global region, a region, an entity,
  or no game thread at all. The same code is correct on Paper, Folia and Canvas.
- **Capabilities, not version checks.** Paper 1.21.4 through 26.3 behind one API; every feature
  reports whether it is native, emulated, degraded or unavailable on the running server.
- **Specs as data.** Commands, items, menus, dialogs and scenes are immutable values compiled once
  for the platform, so they test without a server.
- **Native where it matters.** Commands compile to Paper's Brigadier types, so clients validate
  arguments themselves.
- **Safe text.** Values never become markup.
- **Modular.** Depend on one module or on everything.

## Platforms

| Platform | Versions | Java |
| --- | --- | --- |
| Paper | 1.21.4, 1.21.11, 26.1.2, 26.2, 26.3 | 21 for 1.21.x, 25 for 26.x |
| Folia | every build PaperMC publishes in that range | as Paper |
| Canvas | 26.x | 25 |
| Velocity | 4.2 | 25 |

Published artifacts are JVM 21 bytecode and run on both Java generations. Detail in
[`COMPATIBILITY.md`](COMPATIBILITY.md).

## Status

| Milestone | State |
| --- | --- |
| M0 research and architecture | done |
| M1 build engineering | done: JDK 25 builds JVM 21 bytecode that boots on Paper 1.21.4 (Java 21) and 26.3 (Java 25) |
| M2 core, platforms, concurrency | next |

The full plan is in [`REBUILD.md`](REBUILD.md); decisions are in [`docs/adr`](docs/adr).

## Building

```
./gradlew build
```

Needs JDK 21 and JDK 25 installed or auto-provisioned; Gradle's toolchain resolver handles the rest.

## License

MIT. Optional adapters for GPL libraries compile against them without bundling them; see
[`LICENSING.md`](LICENSING.md).
