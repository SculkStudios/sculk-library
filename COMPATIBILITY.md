# Compatibility

How Sculk 6 supports Paper 1.21.4 through 26.3, Folia, Canvas and Velocity 4 without version
checks in application code. Evidence: `docs/research/b-paper-capability-map.md` (jar scans of
six anchors, Fill build lists), `docs/research/d-velocity.md`, `docs/research/f-build-distribution.md`.

## Targets

| Tier | Target | Pinned artifact | Java | Adventure |
| --- | --- | --- | --- | --- |
| Current | Paper 26.3 | `26.3.build.175-stable` (first stable, 2026-10-11; ≥ b159 for post-effect `update`) | 25 | 5.2.0 |
| Compatible | Paper 26.2 | `26.2.build.133-stable` (≥ b109: removal reasons, de-experimentalised API) | 25 | 5.2.0 |
| Compatible | Paper 26.1.2 | `26.1.2.build.74-stable` | 25 | 4.26.1 |
| Compatible | Paper 1.21.11 | `1.21.11-R0.1-20260511.115010-91` | 21 | 4.26.1 |
| Floor | Paper 1.21.4 | `1.21.4-R0.1-20250925.065901-231` | 21 | 4.20.0 |
| Platform | Folia | obtainable builds only: 26.2 (beta builds only), 26.1.2, 1.21.11, 1.21.8, 1.21.6, 1.21.5, 1.21.4. 26.3 is expected shortly after Paper and Canvas went stable but had no Fill build on 2026-10-12. The nightly canary job lists the Folia versions on Fill; moving a new build into the pull-request matrix is a manual change today. | as Paper | as Paper |
| Platform | Canvas | 26.3 stable (builds 976–978, 2026-10-11), 26.2, 26.1.2, 1.21.11, 1.21.8; pinned download from the Canvas builds API (not on Fill); `io.canvasmc.canvas:canvas-api` from `maven.canvasmc.io` | 25 | as Paper |
| Proxy | Velocity 4.2.0 | `com.velocitypowered:velocity-api:4.2.0` | 25 | 5.2.0 |
| Canary | newest Paper, Folia, Canvas and Velocity builds | today the nightly job scans the pinned anchors and lists Folia and Canvas builds; resolving and booting the newest builds is planned for M3 | — | — |
| Packets | PacketEvents 2.14.0 (26.3 support, released 2026-09-23); ProtocolLib 5.4.0 best effort | `compileOnly` | as server | — |

Builds are pinned, not versions. Backports land mid-line: 26.2 build 100 removed `@Experimental`
from data components, menus, commands, dialogs and registries, and build 109 added removal reasons.
A version threshold would be wrong on either side of those builds. `maven-metadata.xml` for
`paper-api` has listed a stray `26.3-pre-2.build.0-alpha` as latest; nothing resolves `latest`.

### Not targets

| Version | Reason |
| --- | --- |
| 1.21, 1.21.1, 1.21.3 | Each lacks some of the APIs the floor relies on (data components, `TAGS` lifecycle, `editPersistentDataContainer`, `Attribute` as an interface; see below); EOL since March 2025. No fallback code exists for them and nothing is promised. |
| 1.21.2, 26.1 | Paper never shipped them. |
| 1.21.5, 1.21.9, 26.1.1 | Never had a stable Paper build. |
| 26.4 | Pre-release. Canary only. |

The floor is 1.21.4 because it is the hard-fork line Paper told plugins to compile against, the
first version with data components, `editPersistentDataContainer`, datapack discovery and
`RegistryBuilderFactory`, and the oldest 1.21 Folia build.

## Version model

One `MinecraftVersion` value type in `sculk-core`, parsed from `ServerBuildInfo.minecraftVersionId()`
on Paper and `ProtocolVersion` on Velocity, never from `Bukkit.getVersion()`. It understands both
`1.21.11` and `26.1.2`, orders them correctly across the format change, carries the Paper build
number where known, and is the only place a version string is ever parsed. It is formally tested
against every version in the table above and the non-targets.

## Capabilities

Application modules never compare versions. They ask the runtime for a capability and receive one
of four states:

| State | Meaning | Caller behaviour |
| --- | --- | --- |
| `Native` | Paper or Velocity provides it directly. | Use it. |
| `Emulated` | Sculk reproduces the behaviour with other APIs at equal fidelity. | Use it; a note in the diagnostic dump says how. |
| `Degraded` | Sculk reproduces part of it and documents what is missing. | Use it knowingly; the capability describes the gap. |
| `Unavailable` | Nothing reasonable exists on this server. | Branch, or let the operation return `UnsupportedCapability`. |

Detection runs once at startup by probing class and method presence with `MethodHandles.lookup()`,
caching the handles that the chosen adapter needs. No reflective lookup happens after boot. The
state of every capability appears in the runtime diagnostic dump.

Seed table from the jar scans (native since; states on older anchors). Only versions from the
1.21.4 floor up are listed: no fallback is built for anything below it.

| Capability | Native since | Below that, within the supported range |
| --- | --- | --- |
| Data components on `ItemStack` | 1.21.3, so every anchor (experimental until 26.2 b100) | — ; per-component-type probes because types churn (`ShownInTooltip` → `TooltipDisplay` at 1.21.5, `MapItemColor` removed 26.3) |
| `editPersistentDataContainer` on `ItemStack` | 1.21.4 | — |
| `MenuType` typed view builders | 1.21.1 (builders verified in 1.21.4 jar) | — |
| Dialogs | 1.21.7 | Unavailable; fallback is a developer decision per dialog |
| Registry modification | per registry: enchantments and game events 1.21; damage types, banners, paintings 1.21.4; dialogs, jukebox songs, instruments, animal variants 1.21.11; trim materials and patterns 26.2; decorated pot patterns 26.3 | Unavailable per registry; `RegistryFreezeEvent` gone by 1.21.11, use `compose()` |
| Datapack discovery | 1.21.4 (still experimental in 26.3) | — |
| Brigadier command API | 1.21, so every anchor (experimental until 26.2 b100) | — ; individual `ArgumentTypes` factories are newer, see Experimental API |
| Connection and configuration-phase API | 1.21.7 | Degraded through login and join events |
| Client brand on the connection | 26.2 | Degraded through `Player.getClientBrandName` |
| Code of conduct event | 26.2 | Degraded: static server property only |
| Post-processing effects | 26.3 b35 (`update` b159) | Unavailable |
| Waypoint style and colour | 1.21.11 | Emulated hide-only through attributes on 1.21.6 to 1.21.10; Unavailable on 1.21.4 and 1.21.5, which have no waypoints |
| Mannequins | 1.21.11 | Degraded through packet or armour-stand NPC |
| Entity removal reason | 26.2 b109 | Degraded through `EntityRemoveEvent.getCause` |
| `GameRules` registry | 1.21.11 | Legacy `GameRule` names |
| Display entities, async teleport, chunk tickets, Folia schedulers, resource-pack stacking | 1.21, so every anchor | — |

## Compatibility generator

Today `./gradlew :tooling:compatibility:scanPaperApi` downloads each pinned anchor `paper-api` jar,
scans it with ASM and writes `paper-api.json`: every public class whose presence, class kind or
class-level `ApiStatus` marking differs between anchors (`ApiStatus` and `Deprecated` have class
retention, so bytecode is the only place to read them; class kind is what caught `Attribute`
turning from enum to interface and will catch `ItemStack` becoming an interface).

Planned extensions, in the milestone that first needs them: method descriptors and method-level
`ApiStatus` from a probe list, and static constant sets on holders such as `RegistryEvents`,
`MenuType` and `DataComponentTypes` (M3, for the command argument catalogue); the Adventure,
Brigadier and JSpecify versions from each POM (M3); generating the CI server matrix from the
anchors and the Fill API instead of the hand-written workflow (M3); runtime probe lists, the
documentation badges and the compatibility report (M6). The generator does not prove behaviour;
real servers do.

## Adventure

Paper 1.21.x bundles Adventure 4.20 to 4.26 and Paper 26.2+ and Velocity 4 bundle Adventure 5.2.
Adventure 5 removed `TranslationRegistry`, `PlainComponentSerializer`, `MessageType` and the
deprecated `ClickEvent` and `HoverEvent` API, and made `ClickEvent.Action` a non-enum. Shared
modules (`sculk-text`, `sculk-placeholders`, `sculk-presentation`, `sculk-dialogs`) compile once,
against the floor Adventure of the lowest anchor they support. Their test suite then runs a second
time with the Adventure 5.2.0 jars in place of the floor jars, on Java 25 (`testAdventureLatest` in
`sculk-text` today), so the same bytecode is proven on both lines. No `when` over an Adventure type
that changed kind. MiniMessage fixtures run on both lines because 5.x tag behaviour is not yet
verified. Translation builds on `TranslationStore`, `MiniMessageTranslationStore` and
`GlobalTranslator`, which exist from Adventure 4.20.0 and survive unchanged into 5.2.0, never on
`TranslationRegistry` (ADR-0024).

## Java

Every published artifact is JVM 21 bytecode. Paper 1.21.x runs on Java 21; Paper 26.x and
Velocity 4 run on Java 25. The build uses a JDK 25 toolchain with `-Xjdk-release=21` for Kotlin and
`--release 21` for Java, plus a class-file-version gate on every jar. The test suite runs twice,
on Java 21 and Java 25 launchers. Detail in `docs/research/f-build-distribution.md` §2.

## Experimental API

Paper still marks `ItemType`, `PluginBootstrap`, `LifecycleEvents.TAGS`, `DATAPACK_DISCOVERY`,
`DatapackRegistrar` and `BasicCommand` experimental in 26.3, and marked data components, menus,
commands, dialogs and registries experimental until 26.2 b100. Opt-ins are declared per adapter
source set, never globally, so a new experimental symbol is a visible build change.

Some experimental markers sit on single methods of an otherwise stable class. In 26.3
(`26.3.build.175-stable`, read from the jar's bytecode) `ArgumentTypes.angle()`, `axes()`,
`columnBlockPosition()`, `columnFinePosition()` (both overloads) and `blockInWorldPredicate()`
carry method-level `@ApiStatus.Experimental`, and none of them exists in the 1.21.4 jar.
`sculk-commands-paper` therefore opts in per method, never per class, and probes each factory at
startup like any other capability.

## Behavioural shims

Differences the capability engine must paper over, with the version they appear in:

| Version | Difference | Sculk response |
| --- | --- | --- |
| 1.21.5 | Tooltip components reworked | Per-component probes |
| 1.21.6 → 1.21.11 | `RegistryFreezeEvent` → `RegistryComposeEvent` | Registry adapter per anchor |
| 1.21.7 | `PlayerLoginEvent` deprecated for connection events | Connection lifecycle adapter |
| 1.21.10 | Teleport keeps vanilla passenger semantics; `TeleportFlag.EntityState` is a no-op | Explicit `retainPassengers` option implemented per anchor |
| 1.21.11 | Gamerules keyed by registry | Key-based gamerule access |
| 26.1 | World identity by key; `WorldInfo.getName` obsolete; storage layout moved | Keys in persistence; name migration helper |
| 26.2 | Adventure 5; beds lose block entities and PDC | Tests run on both Adventure lines; documented data loss event |
| 26.3 | `PlayerAnimationEvent` → `PlayerArmSwingEvent`; `PotionBrewer` deprecated | Event adapter; data-driven brewing recipes |

## Integration test matrix

Today every pull request boots Paper 1.21.4 (Java 21) and 26.3 (Java 25), runs the threading
scenarios on Paper 1.21.4, Paper 26.3, Folia 26.1.2 and Canvas 26.3, and boots Velocity 4.2.0. The
servers are written by hand in `.github/workflows/ci.yml` and the `integration-tests` builds. The
target matrix below, generated rather than hand-written, lands in M3; there is no nightly job yet.

| When | Servers |
| --- | --- |
| Every pull request | Paper 1.21.4, 1.21.11, 26.1.2, 26.2, 26.3 · Folia 26.3 once published (26.1.2 until then) · Canvas 26.3 · Velocity 4.2.0 · each on the Java the line requires |
| Nightly | Intermediate 1.21.x stable lines (1.21.8), Folia 26.2 beta, 1.21.11 and 1.21.4, newest Canvas build |
| Canary (scheduled) | Newest Paper, Folia, Canvas, Velocity and PacketEvents snapshot; failures open issues and never block a release |

Unit-level MockBukkit lags Paper by weeks (`mockbukkit-v26.2` exists, no 26.3 artifact yet), so the
unit matrix may trail the integration matrix by one Minecraft version.

## Open questions that need a live server

Twelve items are listed in `docs/research/b-paper-capability-map.md` §6, including
configuration-phase audience behaviour for resource packs and dialogs, dialog callback lifetime
on Folia, teleport flag behaviour after 1.21.10 and MiniMessage 5 tag parity. Each becomes an
integration test in M2 or M3 before the dependent capability is marked `Native`.
