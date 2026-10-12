# Sculk Studio

> The Kotlin platform for Paper, Folia, Canvas and Velocity plugins.

[![CI](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml/badge.svg?branch=dev)](https://github.com/SculkStudios/sculk-library/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Sculk 6 is in development on this branch.** It is a ground-up rewrite; nothing here is released
yet. Sculk 5 lives on [`main`](https://github.com/SculkStudios/sculk-library/tree/main) until 6.0 ships.

## What it is

The infrastructure most serious plugins end up building for themselves, done once and done
correctly:

- **Ownership, not "sync".** Work declares whether it needs the global region, a region, an entity,
  or no game thread at all. The same code is correct on Paper, Folia and Canvas.
- **Capabilities, not version checks.** Paper 1.21.4 through 26.3 behind one API; every feature
  reports whether it is native, emulated, degraded or unavailable on the running server.
- **Specs as data.** Commands, items, menus, dialogs and scenes are immutable values compiled once
  for the platform, so they test without a server.
- **Native where it matters.** Commands compile to Paper's and Velocity's own Brigadier, so clients validate
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
| M1 build engineering | done, except the supply-chain items (dependency verification, lockfiles, licence allow-list, SBOM, provenance), which are scheduled in `REBUILD.md` and block 6.0 |
| M2 core, platforms, concurrency | done: `sculk-core`, `sculk-coroutines`, `sculk-platform-api`, `sculk-testkit`, `sculk-paper`, `sculk-canvas`, `sculk-velocity`, threading proven on real Paper, Folia, Canvas and Velocity |
| M3 author experience | in progress: `sculk-text` and `sculk-config` done; commands next |

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
