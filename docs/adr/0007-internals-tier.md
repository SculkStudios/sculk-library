# ADR-0007: Where Minecraft internals are permitted

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Some operations may be impossible or materially slower through Paper API. Internals are tempting
and fragile: since 26.1 the server jar is unobfuscated and Paper's remapper is gone, so only
Mojang-mapped code built against the exact server version works; paperweight-userdev
2.0.0-beta.24 officially supports only the latest Minecraft version
(`docs/research/f-build-distribution.md` §1.3). Most perceived needs are met by Paper API or
PacketEvents (ADR-0012).

## Decision

Minecraft internals (`net.minecraft`, `com.mojang`, CraftBukkit) appear only in
`sculk-internals-*` modules, enforced by the architecture check. No internal type appears in any
signature outside them. Those modules are experimental in their entirety, are never a transitive
dependency of another module, and register as capability providers so they degrade to
`Unavailable` on any version they were not built for. An internals implementation exists only
beside a Paper API implementation (and a PacketEvents one where relevant) of the same operation,
with a JMH or macro benchmark in the same change showing a material gain on a hot path, and an ADR
naming the operation. If the gain is negligible, the internals implementation is deleted. The
decision order is: Paper API, then a Canvas capability on Canvas, then the packet backend, then
internals. No mixin loader is ever required.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Internals for performance by default | Breaks on every Minecraft release; often reaches the same code Paper API calls. |
| No internals ever | Leaves no escape hatch for a proven hot path. |
| Mixins (Horizon or similar) | Requires operators to install an instrumentation launcher; unacceptable compatibility risk for a framework. |

## Consequences

The internals tier supports only the current Minecraft version; older anchors report the
capability as unavailable or fall back to the API path. 6.0 may ship with no internals module at
all, and that is an acceptable outcome.
