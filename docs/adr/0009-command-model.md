# ADR-0009: Command model

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Paper 26.3 marks `CommandExecutor`, `PluginCommand` and `JavaPlugin.onCommand` obsolete in favour
of Brigadier, and exposes 37 native argument types through `ArgumentTypes`, including registry
values through `resource(RegistryKey)`. CommandAPI re-exports the same breadth through per-version
internals modules; cloud maps about twelve types natively and string-parses the rest; Lamp maps
five; LiteCommands registers through the command map with none. cloud defaults suspend execution to
`GlobalScope`, Lamp resumes on whatever thread completes the continuation. Velocity accepts only
Mojang primitive argument types plus custom ones. Sculk 5 had the right shape (spec as data, one
adapter) but typed every argument as a Brigadier string, so clients never validated anything, and
ran executors on the async dispatcher (`docs/research/e1-commands-coroutines-compat.md` §A,
`docs/research/d-velocity.md` §4, `docs/research/a-sculk5-audit.md` §7).

## Decision

One immutable, platform-neutral `CommandSpec` tree is the only command model. The DSL and the
optional annotation frontend both produce it. Compilers turn it into:

- a Paper Brigadier tree registered through `LifecycleEvents.COMMANDS` (bootstrap registration when
  the plugin is a Paper plugin, so commands are visible to datapack functions), with every argument
  mapped to the native `ArgumentTypes` factory where one exists, so the client validates and
  suggests;
- a Velocity `BrigadierCommand`, with backend-only types parsed from strings and validated
  server-side, and an explicit forward-to-backend outcome;
- a pure-JVM Brigadier `CommandDispatcher` for tests, so a command tree executes in JUnit without a
  server or MockBukkit.

Argument handles are typed (`val target = argument<Player>("target")`, read as `target()` in the
executor), never cast. Senders are a sealed type (player, console, entity, block, proxy), so the
sender/executor split of `/execute` cannot be confused. Validators (ranges, length, patterns) are
spec data. Cross-cutting behaviour (permissions per node, contextual requirements, cooldowns, rate
limits, confirmation) is a pre/post-processor pipeline modelled on cloud's. Flags and switches are
supported where the tree stays unambiguous; optional middle arguments compile to sibling branches
and are rejected at declaration when two branches would collide. Executors are `suspend` and run in
the plugin's supervised scope, starting in the sender's ownership context (THREADING.md rule 9).
Help, usage and the documentation command reference are generated from the spec. No
`PluginCommand`, no command-map reflection.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Depend on CommandAPI | Per-version internals modules and a 600 KB shade to re-export what Paper already exposes. |
| Depend on cloud | String-parses most types on Paper; `GlobalScope` coroutine default; Java-first API. |
| Depend on Lamp | Reflection-based annotations as the primary API; five native types. |
| String arguments parsed by Sculk (5.x) | No client-side validation; worse than Paper itself. |

## Consequences

The argument catalogue is Sculk's to maintain, but it is a thin mapping onto public Paper API with
no internals. Velocity cannot validate backend-only types client-side; documentation says so per
type. Changing a requirement at runtime needs `Player.updateCommands()`, which the permission
module calls when a descriptor's result changes.
