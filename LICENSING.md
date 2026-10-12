# Licensing

Sculk Studio is MIT licensed. This file records how optional dependencies with other licences
are kept separate so that remains true for every artifact we publish, and what consumers must know
when they bundle Sculk into their own plugins. It is a documented engineering stance, not legal
advice.

## Published artifacts

Every `studio.sculk:*` artifact is MIT, contains only Sculk source, and has a runtime dependency
graph limited to MIT, Apache-2.0, BSD and EPL libraries (Kotlin, kotlinx, Adventure, HikariCP,
Caffeine, SQLite JDBC, PostgreSQL JDBC). The build fails if anything else reaches a runtime
classpath; the licence report runs in `check` with an allow-list.

## Adapters for GPL and LGPL libraries

| Library | Licence | Sculk artifact | How it is kept separate |
| --- | --- | --- | --- |
| PacketEvents | GPL-3.0 | `sculk-packets-packetevents` | `compileOnly`. The adapter ships no PacketEvents bytes and only loads when the operator has installed the PacketEvents plugin. |
| ProtocolLib | GPL-2.0 | `sculk-packets-protocollib` | Same. GPL-2.0 and GPL-3.0 are mutually incompatible, which alone requires two adapter artifacts. |
| JDA | Apache-2.0, reaches LGPL-2.1 (`trove4j`, `jna`) | `sculk-discord-jda` | `compileOnly`. Consumers add JDA through Paper's `libraries` loader or as a runtime dependency; it is never shaded. |
| MariaDB Connector/J | LGPL-2.1 | `sculk-data-mariadb` | `compileOnly`; the driver is loaded from the server or the consumer's `libraries` entry. |
| MySQL Connector/J | GPL-2.0 with Universal FOSS Exception | `sculk-data-mysql` | `compileOnly`; Paper ships it as a server runtime library, so nothing needs bundling. |
| Lettuce | MIT (Netty 4.2 and Reactor required) | `sculk-cache-valkey` | `compileOnly`; never shaded, because an unrelocated Netty is silently replaced by the server's copy. |
| Paper API, Velocity API | GPL-3.0 with classpath exception, MIT | all platform modules | `compileOnly`, provided by the server. |

Adapter source is MIT. The commonly held positions on compiling against a GPL API without
distributing it range from "a combined work when linked at runtime" to "separate works, combined
by the operator who already holds the GPL program". Sculk takes the second position, which the
Paper ecosystem has relied on for a decade for ProtocolLib adapters in permissively licensed
libraries, and makes the consequence explicit: a consumer who shades a GPL library into their own
jar distributes a GPL-governed work. The Sculk Gradle plugin refuses to shade PacketEvents or
ProtocolLib and fails the build with that explanation.

## Reflection-only integrations

LuckPerms (MIT), Vault (LGPL-3.0), PlaceholderAPI (GPL-3.0), MiniPlaceholders (MIT), Nexo, Oraxen
and ItemsAdder (proprietary) are reached through their public API with `compileOnly` dependencies
where a public artifact exists and through the owning plugin's class loader otherwise. Nothing from
them is bundled.

## Consumers

Three supported ways to ship Sculk inside a plugin, in order of preference:

1. Runtime resolution through Paper's `MavenLibraryResolver` from a Central mirror. Nothing is
   bundled, nothing is relocated.
2. Shade and relocate `studio.sculk`, `kotlin` and `kotlinx` with minimisation off. The Gradle
   plugin configures this and excludes the GPL adapters' upstream coordinates.
3. A shared library plugin providing one copy of Sculk to many plugins. Planned, not in 6.0; the
   module layout avoids static singletons so it stays possible.

## Supply chain

Dependency versions are locked and verified with checksums and PGP signatures in
`gradle/verification-metadata.xml`. Releases are signed, ship a CycloneDX SBOM and carry GitHub
build-provenance attestations. The licence report is published with each release.
