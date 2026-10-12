# Sculk Studio 6 — Contributor & AI Guide

Sculk Studio is a Kotlin platform for Paper, Folia, Canvas and Velocity plugins. Version 6 is a
ground-up rewrite on branch `sculk-6`; 5.x lives on `release/5.x`. Read `REBUILD.md` first: it has
the milestones, the decisions already taken and the release blockers.

## Where things are

| Need | Read |
| --- | --- |
| Why a decision was made | `docs/adr/` |
| Which module owns what, and what it may depend on | `MODULES.md` |
| Version and platform support, pinned anchors | `COMPATIBILITY.md` |
| Execution ownership and the threading contract | `THREADING.md` |
| Stability markers, ABI, deprecation | `API_POLICY.md` |
| Licences of optional dependencies | `LICENSING.md` |
| Evidence behind all of the above | `docs/research/` |

## Layout

`sculk-*` published modules · `build-logic` convention plugins (`sculk.jvm`, `sculk.library`,
`sculk.publish`, `sculk.test-plugin`, `sculk.compatibility`) · `integration-tests/*` server-side
test plugins, never published · `tooling/*` build tooling · `docs` documentation site.

## Rules the build enforces

- JDK 25 toolchain, JVM 21 bytecode, `-Xjdk-release=21`; a jar above class-file version 65 fails.
- Unit tests run on Java 21 and Java 25.
- Explicit API mode, warnings as errors, ktlint (140 columns).
- Every public declaration carries exactly one of `@SculkStable`, `@SculkExperimental`,
  `@SculkInternal`.
- Module boundaries in `MODULES.md` (no platform types in neutral modules, no `net.minecraft`
  outside internals, no `runBlocking`, no `GlobalScope`, no `kotlin-reflect`).
- Committed ABI dumps in `<module>/api/`. A public change needs `./gradlew updateKotlinAbi` in the
  same commit.

## Rules review enforces

- Templates are trusted; values enter MiniMessage only as unparsed or component placeholders.
  Never `replace()` a value into a template.
- `SculkResult` for fallible I/O and user input; throw only for programmer error.
- Every world-touching API states the execution context it requires (`THREADING.md`).
- Every interface a consumer calls ships a fake in test fixtures; fakes refuse what they cannot model.
- Prefer a real engine or a real server to a mock.
- Comments state an invariant, a concurrency requirement, a security boundary, a compatibility
  reason, an upstream bug or a non-obvious performance choice, in one to three lines. No history,
  no banners, no narration. KDoc states the contract, never the identifier.
- No `!!`, no operator-overload DSLs, no ambiguous nested receivers.
- Test names are sentences describing the invariant.

## Commands

```
./gradlew build                               # compile, ktlint, tests on 21 and 25, ABI, architecture, bytecode
./gradlew updateKotlinAbi                     # after a deliberate public API change
./gradlew :integration-tests:boot:verifyBoot  # real Paper 1.21.4 on Java 21 and 26.3 on Java 25
./gradlew :tooling:compatibility:scanPaperApi # regenerate the Paper API compatibility data
./gradlew publishToMavenLocal
```

## Git

Conventional commits authored by the maintainer only; no co-author trailers and no AI mentions.
Work on `sculk-6` or a branch from it; never commit to `main` until 6.0 replaces it.
