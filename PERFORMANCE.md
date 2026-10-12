# Performance

Performance claims need numbers. This file records the rules every module follows, the
benchmarks that prove them, and the budgets a change may not exceed.

## Rules

| Rule | Why |
| --- | --- |
| Compile immutable specs once | A `CommandSpec`, `ItemSpec`, `MenuSpec` or `Template` is parsed and validated at declaration, then reused. Hot paths never re-parse. |
| Select the compatibility backend once | Runtime inspection happens at startup; hot paths call the chosen implementation directly. No reflective lookup after boot. |
| No reflection or method handles in hot loops | Handles are resolved and cached at startup or they are not used. |
| No unbounded caches | Every cache declares a maximum size or weight and an eviction policy. |
| No one task per UI element | One driver task per presentation surface; dirty tracking decides what is sent. |
| No thread per feature | Platform dispatchers and a bounded IO dispatcher. Thread creation is a reviewed decision. |
| No synchronous IO on tick threads | Database, file and network access are suspend functions that leave the tick thread. |
| Diff before sending | Inventory, HUD and scene updates compare rendered state and transmit only the changes. |
| Batch SQL and region work | Repositories batch writes; world operations partition by region and run as batches. |
| Prefer client interpolation | Display-entity animation sets interpolation parameters instead of sending a position every tick. |
| Lazy integrations | An integration initialises on first use, not at boot. |
| Invalidate, do not poll | Placeholders signal change; polling is an explicit, documented policy. |

## Micro benchmarks (JMH)

Pure-code systems with a stable input are benchmarked under `benchmarks/` and run on demand.
Each benchmark records a baseline in `benchmarks/baselines/` and CI compiles the suite on every
pull request so it cannot rot.

| Area | Benchmark |
| --- | --- |
| Text | Template parse, render with placeholders, glyph-width measurement, wrapping |
| Placeholders | Graph invalidation fan-out for 1, 10 and 100 dependents |
| Commands | Spec compilation to Paper and Velocity Brigadier trees, execution overhead per invocation, suggestion generation, test-runner interpretation |
| Config | Load, validate and migrate a 500-line file |
| Items | `ItemSpec` to `ItemStack` through data components |
| PDC | Codec round trips for every built-in type |
| Inventory | Diff of a 54-slot render with 1, 9 and 54 changes |
| Presentation | Sidebar and tab-list diff |
| Scenes | Scene graph diff and transform composition |
| Network | Frame encode and decode for small and maximum-size messages |
| Data | Row mapping for 1 and 1,000 rows |
| Cache | Get, put and stampede-protected load |

## Macro benchmarks

Real servers measured with simulated viewers. Run before each release and whenever a
presentation or scene change lands. Metrics: CPU, allocation rate, heap, p50/p95/p99 latency,
MSPT impact, packets sent.

| Scenario | Viewers |
| --- | --- |
| Animated sidebar with 10 placeholders | 100, 500 |
| Menu refresh storm | 100 |
| Particle scene with 50 emitters | 50 |
| 1,000 virtual entities | 100 |
| 1,000 commands per second | — |
| Repository saturation | — |
| Paper↔Velocity message round trips | — |

## Internals

An NMS implementation exists only beside a Paper-API implementation and, where relevant, a
packet-backend implementation of the same operation, with a benchmark comparing all of them. If
the gain is not material in a hot path, the NMS implementation is deleted.

## Budgets

| Budget | Limit |
| --- | --- |
| Framework startup overhead | Under 50 ms on a warm JVM |
| Idle framework tick cost with no active surfaces | Zero scheduled work |
| Presentation update with no dirty nodes | No packets |
| Per-viewer visual budget (configurable) | Default 2,000 particle packets per second, warned in development mode when exceeded |

Budgets are checked by tests where they can be and by the macro suite where they cannot.
