# ADR-0003: Build on JDK 25, publish JVM 21 bytecode

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Paper 1.21.x runs on Java 21; Paper 26.1+ and Velocity 4 require Java 25. `velocity-api` 4.x is
compiled by a Java 25 toolchain, so the compiler must read JDK 25 class files (Kotlin ≥ 2.3).
Kotlin 2.4.21 and Gradle 9.8.1 are current and mutually workable
(`docs/research/f-build-distribution.md` §1 and §2).

## Decision

One JDK 25 toolchain compiles everything. Every published module sets Kotlin `jvmTarget = 21` with
`-Xjdk-release=21` and Java `options.release = 21`, so no JDK 22+ API can resolve at compile time.
A class-file-version gate in `check` rejects any jar containing a class above major 65. The test
suite runs on both a Java 21 and a Java 25 launcher in the same build, and the real-server matrix
runs each Paper line on the Java it requires. A module that genuinely needs Java 25 bytecode is a
separate, clearly named artifact; none is expected in 6.0.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| JDK 21 toolchain | Cannot read Velocity 4 class files; the build machine runs 25 anyway. |
| Publish Java 25 bytecode | Drops every 1.21.x server, which the directive forbids. |
| Multi-release jars | Complexity for no identified Java-25-only feature. |

## Consequences

The convention plugin carries the three flags and the gate; contributors on a single JDK still
build through the Foojay toolchain resolver. CI provisions Java 21 and 25 together. Kotlin's
`-Xjdk-release` is documented as not guaranteed on every JDK distribution, which the class-version
gate and the Java 21 integration run cover.
