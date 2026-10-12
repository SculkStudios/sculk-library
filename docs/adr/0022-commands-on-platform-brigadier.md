# ADR-0022: Commands on the platforms' Brigadier

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: ADR-0009

## Context

ADR-0009 put `brigadier` on the compile classpath of the neutral `sculk-commands` module and used a
pure-JVM Brigadier `CommandDispatcher` as the test runner. That couples the neutral model to a
library each platform ships in its own build: `paper-api` 26.3 brings `com.mojang:brigadier`
1.3.11, `paper-api` 1.21.4 and 26.2 bring 1.3.10 (read from the published POMs), and Velocity
ships its own fork, `com.velocitypowered:velocity-brigadier`. A neutral module compiled against one
of them asserts a binary contract with all of them. ADR-0009 also said Velocity accepts "Mojang
primitive argument types plus custom ones"; that is wrong. Velocity's documentation states:
"Custom plugin command argument types are not supported in Velocity, as they would require the
client to also support them" (docs.papermc.io/velocity/dev/command-api). Finally, ADR-0007 bans
`com.mojang` outside the internals tier, yet `com.mojang.brigadier` is public API on both platforms.

## Decision

Commands use each platform's own Brigadier API and nothing else.

- `sculk-commands` is neutral and has no Brigadier dependency. It holds the immutable
  `CommandSpec`, the DSL, a neutral argument model (primitive types plus validators: ranges,
  lengths, patterns, choices), middleware (permissions, requirements, cooldowns, rate limits,
  confirmation), help and usage generation, and a server-free test runner that interprets the spec
  itself.
- `sculk-commands-paper` compiles a spec through Paper's `io.papermc.paper.command.brigadier` API
  (`Commands`, `CommandSourceStack`, `ArgumentTypes`, `LifecycleEvents.COMMANDS`) and owns the Paper
  argument catalogue: players, entity selectors, positions, registry resources and every other
  `ArgumentTypes` factory, each behind a capability probe where it is newer than 1.21.4.
- `sculk-commands-velocity` compiles a spec into a `BrigadierCommand` against
  `velocity-brigadier`, owns the proxy catalogue (player, server), sends every backend-only type as
  a string node, validates it server-side and supports an explicit forward-to-backend outcome.
- Brigadier types come only from the platform API on the compile classpath; Sculk never declares
  `com.mojang:brigadier` itself. The architecture check treats `com.mojang.brigadier..` as
  platform API: forbidden in neutral modules, allowed in platform and platform-command modules.
  The rest of `com.mojang` stays internals-only (ADR-0007, unchanged in substance).

Separate modules keep `sculk-paper` and `sculk-velocity` free of the argument catalogue for
plugins that register no commands. Everything else in ADR-0009 stands: typed argument handles,
sealed senders, bootstrap registration for Paper plugins, sibling branches for optional middle
arguments with collision checks at declaration, `suspend` executors starting in the sender's
ownership context (THREADING.md rule 9), generated help, no `PluginCommand`, no command-map
reflection.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep `brigadier` compileOnly in `sculk-commands` (ADR-0009) | Binds a neutral module to one Brigadier build; Velocity's fork is a different artifact. |
| Shade or declare our own Brigadier | Duplicates classes the server already loads; a second copy breaks type identity with Paper's tree. |
| Commands inside `sculk-paper` / `sculk-velocity` | Every plugin pays for the catalogue whether it registers commands or not. |
| Test runner on a pure-JVM Brigadier dispatcher | Reintroduces the dependency; the interpreter tests the spec, real servers test the compiled tree. |

## Consequences

The test runner and the compiled trees can disagree on parsing. A shared conformance suite runs
against the interpreter in unit tests and against the compiled tree on real Paper and Velocity in
`integration-tests`, and a divergence fails the build. `ArgumentTypes.angle`, `axes`,
`columnBlockPosition`, `columnFinePosition` and `blockInWorldPredicate` are absent from 1.21.4 and
carry method-level `@ApiStatus.Experimental` in 26.3, so each needs a probe and its own opt-in.
Velocity documents per type which arguments the client cannot validate. `ModuleRules` in
`build-logic` must split `com.mojang.brigadier` from the internals list before the first command
module lands.
