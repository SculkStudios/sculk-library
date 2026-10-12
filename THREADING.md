# Threading

Sculk 6 replaces "sync or async" with ownership. Every operation that touches the game declares
what it needs to own; the platform decides which thread that is. One semantically correct program
runs on Paper, Folia, Canvas and Velocity. Evidence: `docs/research/c-folia-canvas.md` (Folia and
Canvas source, Paper scheduler implementations), `docs/research/d-velocity.md` §3–5.

## Execution contexts

| Context | Key | Paper | Folia | Canvas | Velocity | Ownership check |
| --- | --- | --- | --- | --- | --- | --- |
| `Global` | — | `GlobalRegionScheduler` (main thread) | global region thread | global region thread | — | `isGlobalTickThread()` |
| `Region` | world + chunk | `RegionScheduler` (forwards to main) | region owning the chunk | region owning the chunk | — | `isOwnedByCurrentRegion(world, chunkX, chunkZ)` or the rectangle overload |
| `Entity` | live entity | `entity.getScheduler()` (real retire semantics) | entity's region, follows teleports | same | — | `isOwnedByCurrentRegion(entity)`, never derived from its location |
| `Async` | — | at most 16 threads per plugin, borrowed from the coroutine IO pool | same | same | plugin executor or Velocity scheduler | not on a tick thread |
| `Proxy` | — | — | — | — | plugin executor | — |
| `Current` | — | the caller's context, asserted rather than assumed | | | | |

A region context names a **chunk**; the work runs on whichever region owns that chunk when it
runs. Only the platform knows how wide its sections are, so a context never carries a section.
Batches group chunks by section, `section = chunk >> shift`, where `shift` is the executor's
`regionShift`: `threaded-regions.grid-exponent` (default 4, so 16×16 chunks), read once at startup
from Folia's and Canvas's `TickRegions.getRegionChunkShift()`. A failed read is logged as an error,
never silently defaulted. A section is wholly owned by exactly one region at any instant, which is
what makes section grouping safe. On Paper there is one owner, and every context collapses onto
the main thread except `Async` and `Proxy`.

## API

```kotlin
withEntity(player) { player.openInventory(view) }
withRegion(location) { location.block.type = Material.STONE }
withGlobal { server.dispatchCommand(server.consoleSender, "save-all") }
withAsync { repository.load(id) }
```

Each `with*` suspends until the block has run in that context and returns its value. When the
caller already owns the context, the block runs inline in the same tick. The same contexts are
available as coroutine dispatchers through `scope.execution.dispatcher(context)`, and the task
helpers (`repeating`, `delayed`, `cron`, `batches`) each take the context to run in.

## Rules

1. **Ownership checks are the only correctness oracle.** Distances such as "eight chunks" are not
   contracts.
2. **Delays are normalised at the Sculk boundary.** `delay ≤ 0` means next tick and `period < 1`
   is clamped to 1, because the platform schedulers throw for both. Async delays are wall-clock and
   allow zero.
3. **Entity work has an explicit outcome:** `EntityResult.Ran(value)` or `EntityResult.Gone`.
   `Gone` comes from a retired scheduler (Paper returns `null`), the retired callback, or an entity
   that is no longer valid when the work would start inline. The retired callback only
   completes the outcome. It never runs the original body: Paper runs it in critical code while the
   entity is being removed, where world access is forbidden. Sculk 5 ran the body there; that was a
   bug.
4. **Teleport re-enters entity ownership.** `Entity.teleportTo` calls `teleportAsync` from the
   entity's owner. Inside `withEntity`, code after the teleport resumes on the entity's dispatcher,
   never on a region captured before the call, because a cross-region or cross-world move makes
   that key stale. `teleportTo` itself returns to the caller's context; continuing on the entity at
   its destination means entering `withEntity` again. Synchronous teleport is not exposed.
5. **Platform futures re-dispatch.** `CompletableFuture.await()` inside Sculk resumes in the
   declared context. Folia completes `teleportAsync` on the destination region and Paper on the
   main thread; Sculk does not rely on either.
6. **Nothing is cached across a suspension in a region context.** Sections merge and split while a
   coroutine is suspended; resumption re-schedules through the region scheduler, so correctness
   holds, but a `Chunk` or `Block` read before the suspension is stale after it.
7. **Sculk tracks its own tasks.** Plugin disable cancels only global and async tasks; there is no
   platform API to cancel region or entity tasks. Every task Sculk schedules is tracked until it
   runs, and closing the runtime retires every task still pending, so a coroutine waiting on one is
   cancelled rather than left suspended forever. The integration suite proves it for every context.
8. **Shutdown does not schedule.** On Folia and Canvas, `onDisable` runs on the region shutdown
   thread after the tick pool has halted; scheduled work will never run, and every ownership check
   passes there. Shutdown saves therefore run synchronously in the shutdown hook, in reverse
   dependency order of resource scopes.
9. **Commands run on the sender's owner.** Player senders execute on their entity context, console
   and RCON on `Global`. The executor declares its context; Sculk 5 silently ran executors on the
   async dispatcher, which this rule forbids.
10. **Listeners run in parallel across regions.** Shared state reachable from listeners is
    thread-safe or region-partitioned. An asynchronous event is never fired from a tick thread,
    because Paper throws for it and every region thread counts as primary.
11. **Inventories are entity work.** Opening, closing and mutating a player's view runs in that
    player's entity context. The platform does not check this; Sculk does in development mode.
12. **Scoreboards differ by platform.** Folia throws on scoreboard creation and registration, so
    sidebar, below-name and nametag-team surfaces there are `Unavailable` unless the plugin installs
    `sculk-presentation-packets` and the server runs a packet backend (ADR-0023). Action bar, boss
    bar, titles and tab header and footer stay native on Folia. Canvas restores scoreboards with
    writes on `Global` and team membership on the entity. Paper allows either.

## Coroutines

Dispatchers implement `isDispatchNeeded` as "not already owned" and `dispatch` through the matching
platform scheduler. They never throw from `dispatch`: if the platform rejects a task (plugin
disabled, scheduler retired), the job is cancelled and the continuation resumes on one dedicated
`sculk-abandoned` thread, where it observes the cancellation rather than being lost. Its `finally`
blocks run there and own nothing, so cleanup after an abandoned dispatch must not touch the world.
`Job.cancel()` cancels the underlying `ScheduledTask`; cancelling a task already running is
cooperative only. `dispatch` never runs a block inline; the inline fast path is
`isDispatchNeeded = false` when already owned. Dispatchers do not implement kotlinx's internal
`Delay`: `delay(ms)` stays wall-clock and then re-dispatches, and tick-aligned waiting is
`delayTicks(n)`, built on the platform scheduler and cancelled with the coroutine. A launch after
the plugin scope is closed throws with a message naming the plugin, rather than being dropped.
`Dispatchers.Unconfined` and `Dispatchers.Default` are never used to resume a game-thread
continuation. Closing a scope cancels its coroutines first and only then closes its resources,
newest first, so no coroutine runs against a resource that is already closed. A repeating, cron or
batch task whose run throws reports the failure and keeps its schedule.

Scope hierarchy, each a `ResourceScope` with a coroutine scope attached. The plugin scope and
explicit child scopes exist today; the per-player, per-entity, per-world and per-session scopes
arrive with the modules that own those lifetimes.

| Scope | Closed when |
| --- | --- |
| `PluginScope` | Plugin disable |
| `PlayerScope` | Player disconnect (retire) |
| `EntityScope` | Entity removal, except dimension change |
| `WorldScope` | World unload |
| `RegionScope` | Explicit close; regions are transient and never owned by user code |
| `SessionScope` (menu, dialog, scene viewer) | Viewer leaves or session ends |
| `RequestScope` (network RPC) | Response, timeout or cancellation |

`GlobalScope` and `runBlocking` are forbidden in main source sets; the architecture check fails the
build on either.

## Region-safe batches

`regionBatch(items, locate) { … }` groups inputs by `(world, section)` at the executor's
`regionShift`; `SectionPosition.packed` is `(sectionZ shl 32) or (sectionX and 0xFFFFFFFF)`, the
same packing Canvas uses. Each group is scheduled once on the region owning its first chunk and
runs its items in input order. On Paper every location in a world forms one group.

Planned for `sculk-world` (M5), not yet implemented: asserting ownership of the group's chunk
rectangle with a per-chunk split and reschedule when it fails, and loading chunks with
`getChunkAtAsync` before block edits so a batch never loads synchronously.

| Guaranteed | Not guaranteed |
| --- | --- |
| Items in one group run sequentially in submission order, within one tick of one region | Ordering across groups |
| The batch result is available once every group completes | Same-tick completion across regions |
| Every item runs on a thread owning its location | Ordering relative to other plugins' tasks |
| | That the region owning a group at run time is the one that owned it at submission |

No atomicity is offered across groups. Large edits belong to WorldEdit or FAWE through
`sculk-worldedit`.

## Canvas

Canvas is detected by brand (`canvasmc:canvas`) or, for a fork with another brand, by the presence
of its region API classes; it is never treated as Folia. Its capability provider adds:

| Capability | API | Contract |
| --- | --- | --- |
| Regionized data | `CanvasRegionData.create(create = { world -> }, merge = { from, into, tickOffset -> }, split = { from, target -> })` over `Server#createRegionizedData` | `merge` and `split` run under the region lock: they must not block, suspend or throw. The façade takes non-suspending lambdas, wraps them in a guard that logs and keeps the server alive, and in development mode fails tests whose callbacks exceed a time budget. Nothing is called when a region dies. |
| Ownership by bounding box and by position plus velocity | `isOwnedByCurrentRegion(world, aabb)`, `(location, velocity, buffer)` | Exposed as additional ownership checks on the region context. |
| Async lifecycle events | teleport, post-teleport, portal, post-portal, respawn, post-respawn, world unload | Pre-events fire on the source region and must not mutate entity state; post-events fire on the destination. Mapped onto Sculk's `listen` and `observe`. |
| World lifecycle | `createWorld` on the global tick, `unloadWorldAsync` with a typed result | Exposed through `sculk-world` as a capability. |
| Tick manager | `RegionThreadingTickManager`, per-region handles | Optional capability; never required. |
| Scheduler algorithm (EDF, affinity, work stealing) | not observable | Sculk never depends on it. |

Canvas-only APIs are never required by modules that also run on Paper. Plugins depending on them
declare `canvas-supported`; everything else declares `folia-supported`.

## Velocity

There is no main thread. Event handlers, command bodies and scheduled tasks run on the plugin's
executor; some handlers run on Netty event loops, which must never block. `Proxy` maps to the
plugin executor as a coroutine dispatcher with no per-resume scheduling overhead. Suspending event
handlers compile to Velocity's continuation-based `EventTask`; awaited login-path events get a
framework timeout. Region and entity contexts are unavailable and fail at registration, not at call
time. The scope is cancelled in `ProxyShutdownEvent`, before Velocity shuts the executors down.

## Development thread checker

Planned for M7 and not built yet; `developmentMode` exists so modules can opt in as they land.
Enabled by a system property or the Gradle plugin's dev run; near-zero cost when off (one
`static final` branch per guarded call). When on, it reports:

| Violation | Detected by |
| --- | --- |
| Block, chunk or world access without owning the region | wrapped world operations in Sculk APIs |
| Entity mutation without owning the entity | entity scope operations |
| Global operations from a region thread where prohibited | global context guard |
| Synchronous teleport | not exposed; reflection-free scan of plugin bytecode in the Gradle Doctor |
| Command dispatch from the wrong owner | command dispatcher guard |
| Blocking calls on a tick runner or packet thread | `BlockHound`-style stack sampling of IO entry points in development runs only |
| Coroutines or tasks outliving their scope at shutdown | scope close audit |
| Region-data callbacks that block or exceed budget | Canvas façade timer |

The test kit provides the same checks without a server: the fake ownership runtime records each
operation's required context and fails the test with "this operation required Entity ownership but
ran in Async".

## Integration scenarios

These run on real Paper, Folia and Canvas builds in CI (`integration-tests/threading`), Folia at
grid-exponent 4 and 5. Detail and setup in `docs/research/c-folia-canvas.md` §6. The status column
says what the suite proves today; a scenario that needs a module not yet built lands with it.

| # | Scenario | Status |
| --- | --- | --- |
| T1 | Entity moves between regions during a delayed entity task: runs once, on the destination | passing |
| T2 | Disconnect during a delayed entity task: `Gone` once, body never runs, repeating tasks stop | removal passing; repeating stop pending |
| T3 | Teleport during scheduled work: continuation resumes on the destination region | passing |
| T4 | Region unload with a pending region task: actual behaviour recorded and documented; no chunk load | pending |
| T5 | Batch across four sections in two live regions and an unloaded area: ownership holds for every item | live regions passing; unloaded area pending |
| T6 | Menu opened from the wrong context: rejected by Sculk, accepted from the entity context | with `sculk-inventory` |
| T7 | Closing with pending tasks of every kind: none runs, all are retired, none stays tracked | passing |
| T8 | Canvas regionized data merge and split as players converge and separate | merge passing; split pending |
| T9 | Zero delays and periods on all platforms: no exceptions, next-tick semantics | passing |
| T10 | Asynchronous event fired from a region thread: rejected at the Sculk boundary | with `sculk-events` |
| T11 | Console dispatch from a region thread, player dispatch from global: rejected with the correct alternative | with `sculk-commands-paper` |
| T12 | Compatibility probe: refuses Folia versions with no build; recognises Canvas 26.3 | detection passing; Folia refusal pending |
| T13 | The region shift Sculk uses equals the server's grid-exponent | passing |
