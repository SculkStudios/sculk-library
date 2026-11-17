# ADR-0029: A real client in the server suites

**Status**: Accepted · **Date**: 2026-11-17 · **Supersedes**: —

## Context

The server suites prove Sculk on real Paper, Folia and Canvas builds, but nothing in them is a
player. Menus, dialogs, the HUD, player commands and Velocity's server switching only exist for a
connected client, and the M4 gate asks for Folia inventory-ownership tests, which need clicks
arriving on a player's region thread. A mocked player would test the mock: the rule is a real
engine or a real server. MCProtocolLib (MIT, GeyserMC, released for 26.3 on 2026-10-14) is a client
protocol library that tracks each Minecraft release; ViaVersion (5.12.2, GPL-3.0) lets a newer
client join an older server.

## Decision

The suites that need a player start a headless client, `integration-tests/bot` on MCProtocolLib, in
its own JVM from inside the test plugin through `integration-tests/bot-driver`. The client speaks
the newest anchor's protocol, pinned in `gradle/servers.json` with the ViaVersion jar (by SHA-256)
that older anchors load for it. It reports what it sees and takes commands as tab-separated lines,
so each suite asserts in Kotlin on the server side. A suite opts in with `client = true` on its
server runs.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| MockBukkit players | A model of Paper, not Paper; no Folia; lags releases by weeks. |
| The client inside the server JVM | Its Netty and Adventure would meet the server's on one class path. |
| One client build per anchor | MCProtocolLib publishes only recent versions; ViaVersion already solves this for real players. |
| Fabric or vanilla clients in CI | A display, an account and minutes of start-up per run. |

## Consequences

Every suite that needs a player can now have one, on every anchor, Folia and Canvas. The client
and ViaVersion are test dependencies only: never published, never shaded into a Sculk artifact,
and ViaVersion's GPL never reaches one. A new Minecraft release needs an MCProtocolLib release
before the client can speak it; until then `checkServerMatrix` keeps the client on the previous
protocol and ViaVersion bridges. Revisit if MCProtocolLib stops tracking releases.
