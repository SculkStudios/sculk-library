# ADR-0004: Capability system

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

ADR-0002 commits to capability-based compatibility. This record fixes the shape. The same answer
must serve three consumers: application code choosing a code path, the runtime diagnostic dump, and
the generated documentation badges. It must cost nothing on hot paths and must never pretend a
feature exists.

## Decision

A capability is a typed key with a service type, declared in the module that owns the feature
(`Capabilities.Dialogs: CapabilityKey<DialogService>`). Platform modules register providers. At
startup the runtime asks each provider to probe the server once and resolves every key to one
sealed state:

```kotlin
sealed interface Capability<out S> {
    data class Native<S>(val service: S) : Capability<S>
    data class Emulated<S>(val service: S, val how: String) : Capability<S>
    data class Degraded<S>(val service: S, val missing: List<String>) : Capability<S>
    data class Unavailable(val reason: String) : Capability<Nothing>
}
```

`runtime.capabilities[Capabilities.Dialogs]` returns the resolved state; `require(key)` returns the
service or a `SculkResult.Failure.UnsupportedCapability` naming the version or platform that would
provide it. Resolution is immutable after the `Ready` lifecycle phase. Services hold direct
references to the implementation chosen at startup, so a hot path never re-checks. Providers probe
by class and method presence through cached `MethodHandles`, never by version string. Every
capability and its resolved state appears in the diagnostic dump, and the same declarations feed
the documentation manifest's `minecraft` and `platforms` fields.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Boolean feature flags | Cannot express emulation or partial support honestly. |
| Nullable services | Loses the reason; callers cannot tell "not on this version" from "not configured". |
| Lazy per-call probing | Reflection on hot paths; inconsistent answers during startup. |

## Consequences

Every feature that differs across the matrix needs a key, a provider per platform adapter, and a
test per anchor that asserts the expected state. Adding a Paper feature means adding a provider, not
editing call sites. A feature that is `Unavailable` everywhere a consumer runs is caught at startup
with a precise message instead of at first use.
