# ADR-0010: DSL first, compile-time annotations second

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Annotation-driven commands and listeners are popular and concise, but every major command library
implements them with runtime reflection, which defeats explicit-API checks, ABI dumps and
shrinking, needs `kotlin-reflect` for Kotlin default arguments, and reports mistakes as startup
exceptions. KSP 2.3.12 has versioning decoupled from Kotlin and runs as a standalone tool; its
pairing with Kotlin 2.4.21 has no official statement yet. Maven has only a community KSP plugin
(`me.kpavlov.ksp.maven:ksp-maven-plugin` 0.4.7)
(`docs/research/e1-commands-coroutines-compat.md` §A.4–A.5, `docs/research/f-build-distribution.md` §1.4).

## Decision

The Kotlin DSL is the primary, complete API and needs no code generation. Annotations
(`@Command`, `@Permission`, `@Listen`, and argument annotations) are optional sugar processed by KSP
at compile time into exactly the specs the DSL builds; there is no second engine. Invalid
signatures fail the build with a message naming the function and the mismatch (for example, a
declared `<target>` with no matching parameter). Nothing is scanned at runtime. Generated code is
readable Kotlin a developer could have written by hand. Gradle is the supported KSP integration;
Maven annotation support is documented as unavailable until a CI job proves the community plugin
end to end. The Kotlin/KSP pairing is verified in M1 before the codegen modules are built.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Runtime reflection | Startup cost, `kotlin-reflect`, late errors, invisible to API tooling. |
| Annotation-first API | Makes codegen mandatory and couples the architecture to KSP's release cycle. |
| kapt | Deprecated path, Java stubs, slow. |

## Consequences

Two frontends, one model, one set of tests for the model. The KSP processor has its own test suite
using compile-testing. A plugin that never applies KSP pays nothing.
