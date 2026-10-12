# Licensing

Sculk Studio is MIT licensed. This file records how optional dependencies with other licences
are kept separate so that remains true for every artifact we publish, and what consumers must know
when they bundle Sculk into their own plugins. It is a documented engineering stance, not legal
advice. Licences below were read from each project's repository on 2026-10-12 unless marked
unverified.

## Published artifacts

Every `studio.sculk:*` artifact is MIT, contains only Sculk source, and has a runtime dependency
graph limited to MIT, Apache-2.0, BSD and EPL libraries (Kotlin, kotlinx, snakeyaml-engine,
HikariCP, Caffeine, SQLite JDBC, PostgreSQL JDBC). Adventure is provided by the server and never
bundled.

Today this rule is upheld by review. The build check that enforces it (a licence report in
`check` with an allow-list that fails the build when anything else reaches a runtime classpath) is
planned for M3 and is a 6.0 release blocker (`REBUILD.md`).

## Libraries and plugins Sculk compiles against

Every upstream below is `compileOnly`. No Sculk artifact contains its bytes; each adapter loads only
when the operator has installed the upstream plugin or library.

| Upstream | Licence | Sculk artifact | Notes |
| --- | --- | --- | --- |
| Paper API | GPL-3.0, inherited from Bukkit and CraftBukkit; contributions by the authors listed in Paper's `LICENSE.md` are MIT. There is no classpath exception. | all Paper-side modules | Provided by the server. Brings Brigadier (MIT). |
| Velocity API | MIT (`api/LICENSE`); the proxy itself is GPL-3.0 | all Velocity-side modules | Provided by the proxy. Brings `velocity-brigadier`. |
| PacketEvents | GPL-3.0 | `sculk-packets-packetevents` | Published to CodeMC, not Maven Central. |
| ProtocolLib | GPL-2.0 ("only" or "or later" is not stated; unverified) | `sculk-packets-protocollib` | GPL-2.0-only and GPL-3.0 are incompatible, which alone requires two adapter artifacts. |
| WorldEdit, FastAsyncWorldEdit | GPL-3.0 | `sculk-worldedit` | Against the WorldEdit API artifacts only. |
| ViaVersion | plugin GPL-3.0; `viaversion-api` MIT | `sculk-viaversion` | Against `com.viaversion:viaversion-api` only. |
| spark | plugin GPL-3.0; `spark-api` MIT | `sculk-spark` | Against `spark-api` only; never against `spark-common` or a platform module. |
| PlaceholderAPI | GPL-3.0 | `sculk-placeholderapi` | |
| VaultUnlocked, Vault | LGPL-3.0 | `sculk-vault` | Against `VaultUnlockedAPI`, which carries both the VaultUnlocked (`net.milkbowl.vault2`) and the legacy Vault (`net.milkbowl.vault`) interfaces. Vault itself has had no commit since March 2024. |
| LuckPerms API | MIT | `sculk-luckperms` | |
| MiniPlaceholders | Apache-2.0 | `sculk-miniplaceholders` | |
| Floodgate | MIT | `sculk-floodgate` | |
| Oraxen | Oraxen License 1.0: not OSI-approved; forbids redistributing full, partial or modified versions; does not apply to code that uses Oraxen without containing it | `sculk-oraxen` | Never shaded, never vendored, never published in a test fixture. |
| Nexo | Proprietary; no licence published for its API (unverified) | `sculk-nexo` | Treated as all rights reserved: never shaded or redistributed. |
| ItemsAdder | Proprietary; `LoneDev6/API-ItemsAdder` publishes no licence (unverified beyond that) | `sculk-itemsadder` | Treated as all rights reserved: never shaded or redistributed. |
| JDA | Apache-2.0, reaches LGPL-2.1 (`trove4j`, `jna`) | `sculk-discord-jda` | Consumers add JDA through Paper's `libraries` loader or as a runtime dependency; it is never shaded. |
| MariaDB Connector/J | LGPL-2.1 | `sculk-data-mariadb` | Loaded from the server or the consumer's `libraries` entry. |
| MySQL Connector/J | GPL-2.0 with Universal FOSS Exception | `sculk-data-mysql` | Paper ships it as a server runtime library, so nothing needs bundling. |
| Lettuce | MIT (Netty 4.2 and Reactor required) | `sculk-cache-valkey`, `sculk-network-valkey` | Never shaded, because an unrelocated Netty is silently replaced by the server's copy. |

Adapter source is MIT. The commonly held positions on compiling against a GPL API without
distributing it range from "a combined work when linked at runtime" to "separate works, combined
by the operator who already holds the GPL program". Sculk takes the second position, which the
Paper ecosystem has relied on for a decade for ProtocolLib adapters in permissively licensed
libraries, and makes the consequence explicit: a consumer who shades a GPL library into their own
jar distributes a GPL-governed work, and a consumer who shades Oraxen, Nexo or ItemsAdder
redistributes code they have no licence to redistribute.

The Sculk Gradle plugin (M6) refuses to shade any of these and fails the build with the reason:
PacketEvents, ProtocolLib, WorldEdit, FastAsyncWorldEdit, ViaVersion, spark, PlaceholderAPI,
Vault, VaultUnlocked, Oraxen, Nexo, ItemsAdder, and the Paper and Velocity APIs. The MIT and
Apache-2.0 plugin APIs in the list (LuckPerms, MiniPlaceholders, Floodgate, `viaversion-api`,
`spark-api`) are refused too, because the owning plugin already provides those classes at runtime
and a second copy breaks type identity.

## Consumers

Three supported ways to ship Sculk inside a plugin, in order of preference:

1. Runtime resolution through Paper's `MavenLibraryResolver` from a Central mirror. Nothing is
   bundled, nothing is relocated.
2. Shade and relocate `studio.sculk`, `kotlin` and `kotlinx` with minimisation off. The Gradle
   plugin configures this and excludes the upstream coordinates listed above.
3. On Velocity, the optional `sculk-velocity-runtime` provider plugin, which ships in 6.0 and
   carries Kotlin, kotlinx and Sculk once per proxy. Velocity's plugin class loaders fall through
   to every other plugin's loader, so two plugins shading unrelocated Kotlin collide; plugins
   that depend on the provider avoid that, and plugins that shade and relocate (option 2) keep
   working without it. Paper gets no shared library plugin: option 1 already gives each plugin its
   own copy without collisions, and the module layout avoids static singletons so one stays
   possible.

## Supply chain

None of the following exists in the build yet. Each is planned, and each is a 6.0 release blocker:

| Item | Lands in |
| --- | --- |
| Dependency verification metadata (`gradle/verification-metadata.xml`, sha256 and PGP) | M3 |
| Dependency lockfiles | M3 |
| Licence report with an allow-list that fails `check` | M3 |
| Signed releases, CycloneDX SBOM, GitHub build-provenance attestations | M7 |
| Second-runner rebuild that diffs checksums against the published artifacts | M7 |
| Licence report published with each release | M7 |

M3 is the point where the first third-party runtime dependencies beyond kotlinx and snakeyaml-engine
arrive, so the checks must be in place before them. Release signing and attestations belong with
the reproducible release in M7 (ADR-0015).
