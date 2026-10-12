# ADR-0001: Target Paper, not Bukkit or Spigot

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Paper hard-forked from Spigot at 1.21.4 and told plugin authors to compile against Paper API from
then on. Every capability Sculk 6 is built around is Paper API: the Folia-compatible schedulers,
Brigadier lifecycle commands, data components, typed menu views, dialogs, registry modification,
datapack discovery, the connection and configuration-phase API, post-effects and Adventure as the
native text model. Folia and Canvas are Paper forks. Spigot has none of these, and since 26.1 Paper
no longer remaps Spigot-mapped internals at all (`docs/research/b-paper-capability-map.md` §3).

## Decision

Sculk 6 targets Paper and its forks (Folia, Canvas) on the server side and Velocity on the proxy
side. It does not run on Spigot or CraftBukkit, and no abstraction is lowered to accommodate them.
Bukkit API types that Paper still uses (`Player`, `Location`, `World`, events) are used directly
where Paper offers nothing better; Paper replacements are preferred wherever they exist (`Key`
over names, Adventure over legacy strings, `teleportAsync` over `teleport`).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Bukkit lowest common denominator | Loses every feature above and the region-threading model; the directive rules it out. |
| Spigot compatibility shims | Paper's own hard fork means the gap only widens; shims would be permanent cost for a shrinking audience. |

## Consequences

Plugin metadata targets `paper-plugin.yml` or `plugin.yml` with `folia-supported`. Documentation
says "Paper" everywhere. A Spigot server fails at startup with a message naming the requirement
rather than at the first missing method.
