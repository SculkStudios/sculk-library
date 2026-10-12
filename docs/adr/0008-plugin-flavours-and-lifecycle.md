# ADR-0008: Paper plugins, Bukkit-style plugins and the Sculk lifecycle

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Paper supports two plugin flavours. Bukkit-style plugins (`plugin.yml`) are stable and familiar.
Paper plugins (`paper-plugin.yml`) add a bootstrapper that runs before the server loads, isolated
class loaders and dependency control, and are still labelled experimental; registry modification
and datapack discovery are only possible from the bootstrapper, and an exception there shuts the
server down. Registering lifecycle handlers can disable Bukkit's `/reload`. Brigadier command
registration through `LifecycleEvents.COMMANDS` works for both flavours. Sculk 5 required
inheriting `SculkPlugin` (`docs/research/b-paper-capability-map.md` §2–3).

## Decision

Sculk works in both flavours and both adoption styles:

| | Bukkit-style plugin | Paper plugin |
| --- | --- | --- |
| Lightweight: `Sculk.attach(this)` from any `JavaPlugin` | Supported | Supported |
| Managed: `SculkPaperPlugin` with `suspend fun start()` | Supported | Supported |
| Bootstrap phase (`SculkBootstrap`) | Unavailable | Supported |

The Sculk lifecycle is `Bootstrap → Configure → Load → Start → Ready → Stopping → Stopped`.
`Bootstrap` exists only for Paper plugins and is where registry mutation, datapack discovery and
bootstrap-time commands are declared; APIs that need it take a `BootstrapContext` parameter, so
calling them later is a compile error, not a runtime surprise. Bukkit-style plugins join at
`Configure`. Every resource created in a phase belongs to the plugin's `ResourceScope` and closes in
reverse order at `Stopping`. Velocity maps the same phases onto its own events (ADR-0005,
`THREADING.md`).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Require Paper plugins | Forces experimental semantics and isolated class loaders on every user for features most never touch. |
| Require the base class | The directive's "base class is convenience, not a prison". |
| Runtime check for bootstrap-only calls | Fails late and mysteriously; the type system can rule it out. |

## Consequences

The Gradle plugin generates either metadata file. Documentation states per feature whether it needs
a Paper plugin. `Sculk.attach` and the base class share one runtime, so moving between them is a
refactor, not a migration.
