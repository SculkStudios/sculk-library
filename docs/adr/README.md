# Architecture Decision Records

One file per decision, numbered in the order the decision was made. A record is never edited
after acceptance; a later decision that changes it is a new record that marks the old one
`Superseded`.

| Status | Meaning |
| --- | --- |
| Proposed | Written, not yet reviewed. |
| Accepted | In force. |
| Superseded by ADR-NNNN | Replaced. Kept for history. |
| Rejected | Considered and declined, with the reason. |

Start from `0000-template.md`. Keep each record under two screens: the context and the
consequences matter more than exhaustive option surveys.

## Index

| ADR | Title | Status |
| --- | --- | --- |
| 0000 | Template | — |
| 0001 | [Target Paper, not Bukkit or Spigot](0001-paper-not-bukkit.md) | Accepted |
| 0002 | [Compatibility strategy for Paper 1.21.4 to 26.3](0002-compatibility-strategy.md) | Accepted |
| 0003 | [Build on JDK 25, publish JVM 21 bytecode](0003-java-bytecode-target.md) | Accepted |
| 0004 | [Capability system](0004-capability-system.md) | Accepted |
| 0005 | [Execution ownership](0005-execution-ownership.md) | Accepted |
| 0006 | [Canvas as a first-class platform](0006-canvas.md) | Accepted |
| 0007 | [Where Minecraft internals are permitted](0007-internals-tier.md) | Accepted |
| 0008 | [Paper plugins, Bukkit-style plugins and the Sculk lifecycle](0008-plugin-flavours-and-lifecycle.md) | Accepted |
| 0009 | [Command model](0009-command-model.md) | Accepted |
| 0010 | [DSL first, compile-time annotations second](0010-dsl-and-codegen.md) | Accepted |
| 0011 | [Configuration serialisation](0011-config-serialization.md) | Superseded by ADR-0021 |
| 0012 | [Packet backend strategy](0012-packet-backend.md) | Accepted |
| 0013 | [Resource-pack architecture](0013-resource-packs.md) | Accepted |
| 0014 | [Persistence architecture](0014-persistence.md) | Accepted |
| 0015 | [Distribution and publishing](0015-distribution-and-publishing.md) | Accepted |
| 0016 | [Documentation platform](0016-docs-platform.md) | Accepted |
| 0017 | [No Sculk 5 compatibility facade](0017-no-v5-compatibility-facade.md) | Accepted |
| 0018 | [Repository and branches](0018-repository-and-branches.md) | Superseded by ADR-0020 |
| 0019 | [Event handler semantics](0019-event-handler-semantics.md) | Accepted |
| 0020 | [Two branches, main and dev](0020-two-branches.md) | Accepted |
| 0021 | [Configuration codec on snakeyaml-engine](0021-config-codec.md) | Accepted |
