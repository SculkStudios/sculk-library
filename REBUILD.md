# Sculk Studio 6 — Rebuild Charter

Sculk 6 is a ground-up rewrite. The 5.x tree on `main` is evidence of behaviour users
rely on, lessons already paid for, and mistakes not to repeat. It is not the specification.

## Goal

Be the platform a Kotlin developer starts with when targeting Paper, Folia, Canvas or Velocity,
so that the usual pile of ten unrelated helper libraries is unnecessary — while a tiny plugin can
still depend on one small module.

## Principles

| Principle | What it means in code |
| --- | --- |
| Kotlin first | `suspend`, structured concurrency, receiver DSLs, immutable specs, sealed hierarchies, value classes, kotlinx.serialization. No Java-parity distortion. |
| Progressive disclosure | Every subsystem has a five-line path and an expert escape hatch. Neither requires understanding the whole framework. |
| API first, internals capable | Paper/Canvas/Velocity API by default. Internals only when the API cannot do it or a benchmark proves a material gain in a hot path. |
| Modular | One module, one capability. `sculk-config` never drags in JDA, Ktor, SQL, Redis or a packet backend. |
| Composition before inheritance | `Sculk.attach(plugin)` is the primary entry point. `SculkPaperPlugin` is convenience. |
| Capability, not version | Application modules consume `Native / Emulated / Degraded / Unavailable` capabilities. Version checks live only at adapter boundaries. |
| Ownership, not sync | Work is `Global`, `Region`, `Entity`, `Async` or `Proxy`. The platform adapter decides the thread. |
| Resource ownership | Everything Sculk creates belongs to a `ResourceScope` and closes with it. |
| Templates trusted, values not | Untrusted text enters MiniMessage only through unparsed or component placeholders. |

## Platform contract

| Tier | Targets | Promise |
| --- | --- | --- |
| Current | Paper 26.3 | Every applicable capability. |
| Compatible | Paper 1.21.4 (floor), 1.21.11, 26.1.2, 26.2 | Same high-level API where semantics hold; capabilities report otherwise. |
| Platform-specialised | Folia, Canvas | Region-aware implementations and platform-exclusive features, tested on obtainable builds only. |
| Proxy | Velocity 4.2 | First-class runtime, not a utility. |
| Canary | Upcoming Paper / Folia / Canvas / Velocity builds | CI signal. Never advertised as stable. |

Pinned builds and the reasoning behind the floor are in `COMPATIBILITY.md`.

Bytecode targets JVM 21, built on JDK 25. Anything that genuinely needs Java 25 lives in a
clearly named module. Fabric, Sponge, Minestom and Bungee are out of scope for 6.0; the
platform API must not prevent them later.

## Decisions already taken

| Decision | Choice | Reason |
| --- | --- | --- |
| Repository | Same repo, two branches: `main` carries 5.x until 6.0 ships, `dev` carries the rewrite (ADR-0020). | History stays reachable; no code is copied from 5.x. |
| 5.x compatibility facade | Not shipped. `MIGRATION_5_TO_6.md` plus a `sculkMigrate` analysis task instead. | 5.x never reached public distribution; a facade would constrain the new architecture to serve one consumer. |
| Persistence | JDBC + HikariCP + bounded IO dispatcher in 6.0. | Mature and predictable. R2DBC and Exposed are 6.x candidates behind the same repository contract. |
| Code generation | KSP2, Gradle first. Annotations compile into the same models the DSL produces. | One command model, no runtime scanning. Maven KSP only once CI proves it. |
| Maven | First-class consumption and `sculk-maven-plugin` (metadata and verification goals) in 6.0; the Maven plugin is built in M6 after the Gradle plugin. | Correct metadata and a BOM come first. Maven KSP stays documented as unavailable until CI proves it (ADR-0010). |
| Scope of 6.0 | Every module in `MODULES.md`. Nothing is deferred to 6.1; where a plan is thin it is strengthened, not cut. | The rebuild replaces the whole 5.x surface and the integrations authors rely on. |
| Commands | Each platform's own Brigadier: Paper's `io.papermc.paper.command.brigadier` API and Velocity's `BrigadierCommand`. The neutral `sculk-commands` module has no Brigadier dependency (ADR-0022). | Native client-side validation without binding a neutral module to one Brigadier build. |
| Packets | PacketEvents primary, ProtocolLib where tests pass. Both `compileOnly`, never bundled. Scoreboard surfaces on Folia need an opt-in packet module (ADR-0023). | Upstream tracks protocol changes; Sculk should not. Licence stance in `LICENSING.md`. |
| Documentation | Custom portal, framework chosen after the spike in `docs/research/g-docs-spike.md`. | Navigation and badges are generated from a manifest; a hand-maintained sidebar is a defect. |

Decisions that need research evidence are recorded as ADRs under `docs/adr/`.

## Milestones and gates

No milestone writes bulk code before the previous gate is green.

| Milestone | Scope | Gate |
| --- | --- | --- |
| M0 Research and architecture | Research reports, this charter, `ARCHITECTURE.md`, `MODULES.md`, `COMPATIBILITY.md`, `THREADING.md`, `PERFORMANCE.md`, `SECURITY.md`, `API_POLICY.md`, `MIGRATION_5_TO_6.md` skeleton, `DOCS_ARCHITECTURE.md`, `LICENSING.md`, ADRs. | Documents reviewed and approved. No production code. |
| M1 Build engineering (done, except supply chain) | Convention plugins, JDK 25 → JVM 21, architecture checks, compatibility generator skeleton, publishing, hand-written CI matrix, integration harness. Dependency verification, lockfiles, licence allow-list, SBOM and provenance were planned here and are not built; they move to M3 and M7 below. | One module compiled on JDK 25 boots on Java 21 + Paper 1.21.x and Java 25 + Paper 26.3. |
| M2 Core and platforms (done) | `sculk-core`, `sculk-coroutines`, `sculk-platform-api`, `sculk-paper`, `sculk-canvas`, `sculk-velocity`, region batching, `sculk-testkit` v1, real-server tests. | Folia and Canvas threading scenarios pass on real servers. No `runBlocking` in runtime code. |
| M3 Author experience (in progress: text and config done, commands next) | text (then translations on Adventure stores, ADR-0024), placeholders, config, commands with `sculk-commands-paper` and `sculk-commands-velocity` (ADR-0022), events, permissions. Carried over from M1: dependency verification metadata, lockfiles, licence report with allow-list in `check`. New: CI server matrix generated from the anchors and Fill; method-level probes in the compatibility generator. | Two example plugins run on the full matrix; JMH baselines for rendering and commands; supply-chain checks fail the build on a violation. |
| M4 Content and UI | items, PDC, registry, recipes, inventory, dialogs, presentation and `sculk-presentation-packets`, config admin UI. | Menu, staff and HUD examples rebuilt; Folia inventory-ownership tests pass. |
| M5 Scenes, assets, world, data, network | scenes, effects, resource packs, world, entity, data (order below), cache, network with `sculk-network-valkey`, packets, integrations (VaultUnlocked first in `sculk-vault`, ViaVersion included), Discord, web. | Real-database CI; Paper↔Velocity network test; packet tests on every anchor. |
| M6 Tooling and docs | Gradle plugin, BOM, catalog, `sculk-paper-all`, Maven plugin and verification, docs portal, generator, Doctor, internals only if benchmarked. | Generated projects build under Gradle and Maven; docs and snippets build in CI. |
| M7 Hardening and RC | Macro benchmarks, leak and concurrency tests, security audit, pilot migrations, migration guide, reproducible release with signing, CycloneDX SBOM, build-provenance attestations and a second-runner checksum rebuild. | Every release blocker below is checked. |

### Order inside M5: data

The whole data plan ships in 6.0 (ADR-0014). It is built in this order, each step usable and
tested on real SQLite, MySQL, MariaDB and PostgreSQL before the next starts, so the most-used
path is solid first:

1. Pool and dispatcher: HikariCP behind the bounded IO dispatcher, driver modules, SQLite settings.
2. Repositories generated from kotlinx descriptors, executed only through prepared statements with
   bound parameters, upserts and batching.
3. Transactions spanning repositories.
4. Additive entity-table migration, then the versioned script runner with history and checksums.
5. Dialects completed for every engine in the suite.
6. Query DSL, compiled to prepared statements by the same dialects; nothing in it concatenates
   values into SQL.

## Definition of done for a public feature

Implementation · tests · integration tests where platform behaviour is involved · KDoc contract ·
docs page with metadata · compiled example · threading contract · compatibility contract · error
behaviour · resource cleanup · stability marker · performance sanity check · `.api` dump.

## Release blockers

6.0 does not ship while any of these is true: a Paper anchor fails; declared Folia or Canvas
support fails; Velocity fails; a platform-neutral module reaches Bukkit; blocking IO exists on a
known tick path; a coroutine outlives its scope; compatibility docs disagree with CI; an example
or documentation snippet fails to compile; an API dump change is unreviewed; the migration guide
is incomplete; Maven or Gradle consumers cannot build; release artifacts are not reproducible; a
dependency boundary is broken; dependency verification, the licence allow-list, the SBOM or
provenance attestations are missing from the build; a planned module is missing.

## Code

- Comments state an invariant, a concurrency requirement, a security boundary, a compatibility
  reason, an upstream bug or a non-obvious performance choice. One to three lines. No history,
  no banners, no narration of what the code does.
- KDoc states the contract: what it does, which thread it requires, how it fails. It never
  restates its identifier.
- No `!!`. No operator-overload DSLs. No nested receivers where `this` is ambiguous. Public data
  is immutable; mutable internals are encapsulated and measured.
- Files stay focused. Functions stay short. Early returns over nesting.
- `./gradlew build` is green with explicit API, warnings as errors, ktlint, architecture checks
  and API validation. Every change to the public surface shows up in a committed `.api` diff.

## What Sculk 6 is not

Not another LuckPerms, WorldEdit, PacketEvents, Adventure, Ktor, JDA or ORM. Not a reflection
DI container, a runtime annotation scanner, an NMS-first framework, a packets-first framework, a
pre-1.21 compatibility layer, or a web UI framework forced into inventories. Those projects are
integrated, not rebuilt.

## Audit 2026-10-12

Corrections made to the plan after review. Details are in the commits of that date.

| Area | Correction |
| --- | --- |
| Scope | Every planned module ships in 6.0; `sculk-viaversion`, internals and `sculk-maven-plugin` moved out of 6.1. |
| Modules | `MODULES.md` shows done / in progress / planned; `sculk-core` lists no coroutines dependency; `sculk-paper-all` is a POM-only aggregate. |
| Commands | ADR-0022 supersedes ADR-0009: platform Brigadier only, neutral module without Brigadier, new `sculk-commands-paper` and `sculk-commands-velocity`. |
| Architecture rule | `com.mojang.brigadier` is platform API (forbidden in neutral modules); the rest of `com.mojang` stays internals-only. `ModuleRules` in `build-logic` still needs the split. |
| Velocity | Removed the false claim that Velocity accepts custom argument types. |
| Folia HUD | ADR-0023 supersedes ADR-0012: on Folia, sidebar, below-name and nametag teams are `Unavailable` without the opt-in `sculk-presentation-packets`. |
| Licences | Paper is GPL-3.0 with no classpath exception; MiniPlaceholders is Apache-2.0; rows added for WorldEdit, FAWE, ViaVersion, spark, PlaceholderAPI, Vault, VaultUnlocked, LuckPerms, Floodgate, Oraxen, Nexo, ItemsAdder; shade refusal list extended. |
| Velocity runtime | `sculk-velocity-runtime` stays in 6.0 as an optional provider plugin; `LICENSING.md` no longer says otherwise. |
| Supply chain | Verification, lockfiles, licence allow-list, SBOM and provenance are planned (M3, M7) release blockers, not current facts. |
| Economy | `sculk-vault` targets VaultUnlocked, with legacy Vault as fallback. |
| Compatibility | Removed fallbacks below the 1.21.4 floor (`ItemMeta`, `editMeta`, `createInventory`) and the `ItemMeta` benchmark. |
| Adventure | Shared modules compile once and run their tests again against Adventure 5.2.0 jars; they are not compiled twice. |
| CI matrix | Hand-written today; generation from the anchors and Fill is planned for M3. |
| Experimental API | Per-method opt-ins and probes for 26.3 `ArgumentTypes.angle`, `axes`, `columnBlockPosition`, `columnFinePosition`, `blockInWorldPredicate`. |
| Translations | ADR-0024: translations on `MiniMessageTranslationStore` and `GlobalTranslator`, with CLDR plurals. |
| Network | Valkey transport in its own `sculk-network-valkey`; plugin messaging reports a missing carrier; no routing through `sculk-data`. |
| Resource packs | ADR-0025: creative assessed; Sculk keeps a narrow own model until creative publishes a release covering 1.21.4 to 26.3. |
| Data | Work order fixed: repositories and prepared statements first, query DSL last. |
| Research | Stale kaml, 1.21.11-floor and 5.x-name content marked; PacketEvents repository reconciled (CodeMC, not Maven Central). |
| Migration guide | Dropped the "layered sources" claim, which no decision backs. |
| Charter | Canary row moved into the platform table. |
