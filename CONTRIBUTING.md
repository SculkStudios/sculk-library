# Contributing to Sculk Studio

Thank you for your interest in contributing to Sculk Studio. This document covers everything you need to get started.

## Prerequisites

- JDK 21+
- Gradle (use the wrapper — `./gradlew`)
- A Paper 1.21.11 server for integration testing

## Project Structure

```
sculk-studio/
 ├── build-logic/        Convention plugins (shared Gradle config)
 ├── sculk-common/       Base: result/handle, coroutines, scheduler, version, annotations
 ├── sculk-adventure/    MiniMessage messaging helpers
 ├── sculk-commands/     Brigadier command DSL
 ├── sculk-gui/          GUI menus
 ├── sculk-events/       Event bus
 ├── sculk-config/       Typed configs, hot reload
 ├── sculk-series/       Registry-based material/sound/particle mapping
 ├── sculk-items/        Item builders, data components, PDC
 ├── sculk-effects/      Particle/sound builders, timelines
 ├── sculk-data/         Suspend repositories, query DSL, cache
 ├── sculk-text/         Localization
 ├── sculk-tasks/        Cron & coroutine scheduling
 ├── sculk-integrations/ Optional PAPI/Vault/LuckPerms adapters
 ├── sculk-packets-*/    Packet API + PacketEvents/ProtocolLib backends
 ├── sculk-content/      Client-side block helpers
 ├── sculk-platform/     Paper bootstrap + SculkPlugin; re-exports everything
 ├── sculk-bom/          Version BOM for à-la-carte use
 ├── examples/           Example plugins (not published)
 ├── benchmarks/         JMH microbenchmarks (not published)
 └── docs/               Documentation site
```

See the [Modules & Architecture](https://docs.sculk.studio/introduction/modules/) page for the full
section ↔ module ↔ package map.

## Getting Started

```bash
git clone https://github.com/SculkStudios/sculk-library.git
cd sculk-library
./gradlew build
```

## Non-Negotiable Rules

These rules apply to every line of code in this repository:

- **Kotlin-first only.** No Java builder classes, no `@JvmStatic`/`@JvmOverloads`. DSLs only.
- **Adventure-only messaging.** MiniMessage strings everywhere. Zero legacy color codes.
- **No hardcoded config or messages.** Everything must be user-configurable.
- **Coroutines, not callbacks.** IO/DB is `suspend`; no `CompletableFuture` on public APIs.
- **Folia-correct.** Route timing through `SculkScheduler` / `SculkCoroutineScope`; document the thread.
- **Stability markers required.** Every public type carries `@SculkStable`, `@SculkExperimental`, or `@SculkInternal`.
- **ktlint + explicit API.** Code must pass `./gradlew ktlintCheck` and `explicitApi()` (max line 140).

## Module Dependency Flow

Modules depend only on what they use; the graph is flat and acyclic. `sculk-common` is the base;
`sculk-platform` re-exports everything. See the
[Modules & Architecture](https://docs.sculk.studio/introduction/modules/) page for the full graph.

No circular dependencies. Ever.

## Common Commands

```bash
# Build everything
./gradlew build

# Run all tests
./gradlew test

# Check code style
./gradlew ktlintCheck

# Auto-fix style issues
./gradlew ktlintFormat

# Run benchmarks
./gradlew :benchmarks:jmh
```

## Submitting a Pull Request

1. Fork the repository
2. Create a branch: `git checkout -b feat/your-feature`
3. Make your changes — follow the rules above
4. Ensure `./gradlew build` and `./gradlew ktlintCheck` pass
5. Open a PR against `main`

Please fill out the PR template completely. PRs without passing CI will not be reviewed.

## Questions

Open a [GitHub Discussion](https://github.com/SculkStudios/sculk-library/discussions) or join us at [sculk.studio](https://sculk.studio).
