# ADR-0028: Adapters compiled against the newest anchor

**Status**: Accepted · **Date**: 2026-11-17 · **Supersedes**: —

## Context

ADR-0002 selects adapters per anchor by probing class and method presence, and modules compile
against the 1.21.4 floor so their bytecode links everywhere. Some features exist only above the
floor: item tooltips are hidden through `TooltipDisplay` from 1.21.5 (on 1.21.4 through
`showInTooltip` flags that later builds removed), the whole dialog API arrives in 1.21.7, and
`UNBREAKABLE` changed from a valued to a non-valued component type. Code that names those types
cannot be compiled against the floor at all. Calling each method through `MethodHandles` works for
two calls and is unreadable for a dialog builder of forty. The jar scans (`scanPaperApi`) name every
such difference, so the set of affected code is known and small.

## Decision

A library module that needs API newer than the floor puts that code in an `adapters` source set
(`sculk.paper-adapters` convention) compiled against the newest Paper anchor, and packs it into the
same jar. Main code compiles against the floor, defines an internal interface for the adapter,
probes once at startup for the classes or members the adapter needs, and only then instantiates
the adapter by name. Adapter classes are never referenced from main bytecode, so the JVM never
links them on a server that lacks their types. Every adapter is proven on the real servers of
every anchor it claims, and the fallback on every anchor it does not.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| `MethodHandles` for every newer call | Correct but unreadable at dialog scale; every signature is a string the compiler cannot check. |
| One artifact per anchor (`sculk-items-1214`, `sculk-items-263`) | Consumers would pick by server version, which is exactly the version check the capability model removes. |
| Raise the floor to 1.21.7 | Drops the hard-fork line Paper told plugins to compile against, and still leaves 26.x-only API. |
| Compile everything against the newest anchor | Linking errors on the floor surface only on real servers, after release. |

## Consequences

Adapter code is type-checked against real API, and the floor stays the compile target for
everything else. The architecture check covers the `adapters` classes too. A reference from main
code to an adapter class is a review defect; the adapter interface plus a by-name factory keeps it
impossible to do by accident. The newest anchor moving (via `updateServerMatrix`) recompiles every
adapter against it, which is the earliest place an upstream removal shows. Revisit if Paper ships a
stable API surface that spans the supported range.
