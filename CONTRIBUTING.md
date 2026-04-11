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
 ├── sculk-core/         Commands, GUI, Adventure wrapper, scheduler
 ├── sculk-config/       Typed configs, hot reload, message system
 ├── sculk-series/       Registry-based material/sound/particle mapping
 ├── sculk-effects/      Particle builders, sound builders, timelines
 ├── sculk-data/         Async database abstraction + cache
 ├── sculk-platform/     Paper integration layer (bootstrap)
 ├── examples/           Example plugins (not published)
 ├── benchmarks/         JMH microbenchmarks (not published)
 └── docs/               Documentation site
```

## Getting Started

```bash
git clone https://github.com/SculkStudios/sculk-studio.git
cd sculk-studio
./gradlew build
```

## Non-Negotiable Rules

These rules apply to every line of code in this repository:

- **No generics in public APIs.** `ArgumentParser<T>` is internal only.
- **Adventure-only messaging.** MiniMessage strings everywhere. Zero legacy color codes.
- **No hardcoded config or messages.** Everything must be user-configurable.
- **Java compatibility.** Every Kotlin DSL entry point needs a paired Java builder.
- **No blocking the main thread.** All IO and DB calls must be async.
- **Stability markers required.** Every public class needs `@SculkStable`, `@SculkExperimental`, or `@SculkInternal`.

## Module Dependency Flow

```
sculk-core → sculk-config → sculk-series → sculk-effects → sculk-data → sculk-platform
```

No skipping layers. No circular dependencies. Ever.

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

Open a [GitHub Discussion](https://github.com/SculkStudios/sculk-studio/discussions) or join us at [sculk.gg](https://sculk.gg).
