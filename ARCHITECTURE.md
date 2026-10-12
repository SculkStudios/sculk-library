# Architecture

How Sculk 6 is put together. `MODULES.md` lists the modules, `THREADING.md` the execution
contract, `COMPATIBILITY.md` the version strategy; decisions and their reasons are in `docs/adr/`.

## Layers

```
 application code (plugin, proxy plugin, bot, web service)
        │
 ┌──────┴───────────────────────────────────────────────────────────────┐
 │ feature modules     text · placeholders · config · commands · events │
 │                     items · registry · inventory · dialogs ·         │
 │                     presentation · scenes · effects · resourcepack · │
 │                     world · entity · data · cache · network          │
 └──────┬───────────────────────────────────────────────────────────────┘
        │ depend only on
 ┌──────┴──────────────────────────┐   ┌──────────────────────────────┐
 │ platform-api  coroutines  core  │   │ standalone: discord · web    │
 └──────┬──────────────────────────┘   └──────────────────────────────┘
        │ implemented by
 ┌──────┴────────────┬─────────────────┐
 │ sculk-paper       │ sculk-velocity  │
 │  (Paper, Folia)   │                 │
 │  └ sculk-canvas   │                 │
 └──────┬────────────┴─────────────────┘
        │ capability adapters per anchor, selected once at startup
 Paper · Folia · Canvas · Velocity API
        │ only where the API cannot
 packet backends (PacketEvents, ProtocolLib) · internals tier (experimental)
```

Dependencies point downward only. `core`, `coroutines`, `platform-api` and every module marked
neutral in `MODULES.md` compile without Bukkit, Paper or Velocity on the classpath, enforced on
bytecode by the architecture check. Platform modules are the only place platform types are
implemented against; feature modules that need a platform type (an `ItemStack`, a `Player`) depend
on `sculk-paper` and say so.

## The runtime

`SculkRuntime` is the one object a plugin holds. It is created by `Sculk.attach(plugin)` or by the
`SculkPaperPlugin` and `SculkVelocityPlugin` base classes, which call the same function.

| Member | Purpose |
| --- | --- |
| `lifecycle` | Current phase and phase hooks (`Bootstrap` … `Stopped`) |
| `scope` | The plugin `ResourceScope` that owns everything below |
| `capabilities` | Resolved capability states (ADR-0004) |
| `dispatchers`, `tasks` | Ownership-aware coroutine dispatchers and tick schedulers (ADR-0005) |
| `services` | Type-keyed service registry; modules register here, nothing is static |
| `diagnostics` | Runtime dump: platform, version, capabilities, backends, integrations, execution model |
| feature entry points | `commands`, `events`, `config`, `menus`, `presentation`, … contributed by installed modules as extension properties |

Feature modules contribute to the runtime through a small module SPI registered at attach time;
there is no classpath scanning and no reflection-based injection. A module that is not on the
classpath contributes nothing and costs nothing.

## Resource ownership

Everything Sculk creates is a resource with an owner: listeners, commands, tasks, coroutine jobs,
menus and their sessions, display and virtual entities, scenes, network channels, database pools,
caches, HTTP servers, Discord gateways, file watchers. Resources register with a `ResourceScope`.
Scopes nest (plugin → player → session) and close deterministically in reverse registration order,
collecting exceptions rather than stopping at the first. A resource opened lazily is never opened
by shutdown. Closing a scope is the only way resources end; there is no separate "unregister" path
to forget.

## Specs and compilers

User intent is captured as immutable data and compiled once by a platform adapter. This is what
made 5.x testable without a server and what 6 keeps everywhere:

| Spec | Built by | Compiled to |
| --- | --- | --- |
| `CommandSpec` | DSL or KSP annotations | Paper Brigadier tree (`sculk-commands-paper`), Velocity `BrigadierCommand` (`sculk-commands-velocity`); the neutral test runner interprets the spec without Brigadier (ADR-0022) |
| `ItemSpec` | DSL or config | data components or `ItemMeta`, per capability |
| `MenuSpec<S>` | DSL | typed menu view plus a renderer that diffs state |
| `DialogSpec` | DSL | Paper dialog, or an explicit fallback |
| `SceneSpec` | DSL | display entities or per-viewer packet entities |
| `RecipeSpec` | DSL | Paper recipe registration |
| `TableSchema` | kotlinx descriptor | SQL per dialect |
| `DiscordMessage` | DSL | JDA components or webhook payload |

Specs hold no platform types they do not need, are equal by value, and are validated at
construction; a spec that cannot compile fails where it was declared, not on first use.

## Errors

Expected failures are values: `SculkResult<T>` with a sealed failure hierarchy
(`UnsupportedCapability`, `InvalidConfig`, `NetworkTimeout`, `DatabaseFailure`,
`MissingIntegration`, `InvalidResource`, …). Each failure carries a sentence answering what happened,
where, what was expected, what was received and what to do, plus the original cause, with secrets
redacted. Programmer errors (wrong context in development mode, an undeclared argument name, a
duplicate service) throw at the point of the mistake. Nothing returns a `SculkResult` that cannot
fail.

## Text and trust

One renderer turns templates into components. Templates come from code, configuration or
translation bundles and are trusted. Values come from players, databases, other plugins and the
network and are not: they enter MiniMessage only as unparsed or component placeholders. External
placeholder output (PlaceholderAPI, MiniPlaceholders) is a value unless an integration is
explicitly configured to trust an expansion. Documentation examples are compiled, so the docs can
no longer teach the injection the code prevents.

## Presentation and state

Menus, presentation surfaces and scenes share one model: state goes in, a rendered tree comes out,
the previous tree is diffed against the new one, and only changes are sent. Placeholders and other
sources signal invalidation; only dependent nodes re-render. Update policies (`OnChange`,
`OnEvent`, `Interval`, `Manual`, `EveryTick` by explicit opt-in) bound the work, and one driver per
surface type schedules it on the right owner.

## Testing

Every interface a consumer calls ships a fake in `sculk-testkit`, including a fake ownership
runtime that fails a unit test when an operation runs in the wrong context. Fakes refuse what they
cannot model. Data tests run on real engines. Platform behaviour is proven on real Paper, Folia,
Canvas and Velocity servers in CI, using a test plugin that runs suites inside the server and
reports results as JSON. MockBukkit remains useful for unit-level Bukkit behaviour and is never
treated as proof of platform correctness.

## Build

A JDK 25 toolchain emits JVM 21 bytecode with API fencing and a class-version gate (ADR-0003).
Convention plugins in `build-logic` carry explicit API mode, warnings as errors, ktlint, ABI dumps,
the architecture check, licence report, Dokka, signing and Central publishing (ADR-0015). The
compatibility generator scans pinned anchor jars and feeds runtime probes, CI and documentation
(ADR-0002). The `studio.sculk` Gradle plugin gives consumers the same conventions plus metadata
generation, packaging, run tasks, the resource-pack compiler and `sculkDoctor`.

## What is deliberately not here

No dependency-injection container, no runtime annotation scanning, no global singletons, no
version checks outside adapters, no Bukkit scheduler, no `runBlocking` in runtime code, no
`kotlin-reflect`, no bundled GPL code, no required mixin loader.
