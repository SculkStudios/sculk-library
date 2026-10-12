# Workstream C — Folia and Canvas threading/ownership models

Research for the Sculk 6 execution-ownership model. Date: 2026-10-12. Sources are cited inline; clone
paths are relative to the scratchpad folder `c-folia-canvas/`. Source aliases:

| Alias | Path / URL |
| --- | --- |
| `[F-P]` | `folia/folia-server/paper-patches/features/0001-Region-Threading-Base.patch` (Folia @ `acf6733`, 2026-09-22) |
| `[F-M]` | `folia/folia-server/minecraft-patches/features/0001-Region-Threading-Base.patch` |
| `[F-API]` | `folia/folia-api/paper-patches/features/000{2,3,4}-*.patch` |
| `[P-api/*]` | `paper-raw/*.java` = `PaperMC/Paper@main` `paper-api/src/main/java/...` |
| `[P-srv/*]` | `paper-raw/server-*.java`, `CraftServer.java`, `PaperEventManager.java`, `PaperPluginInstanceManager.java`, `Entity.java.patch`, `PlayerList.java.patch` from `paper-server/` |
| `[C-API]` | `canvas/canvas-api/paper-patches/base/0002-Region-Threading.patch` + `canvas/canvas-api/src/main/java/io/canvasmc/canvas/**` (Canvas @ `5d69517`, 2026-10-11) |
| `[C-S]` | `canvas/canvas-server/minecraft-patches/base/0004-Region-Threading.patch` (36 005 lines) |
| `[FL]` | `folialib/` (TechnicallyCoded/FoliaLib @ `5735e84`, 2026-05-04) |
| `[MPL]` | `mpl-raw/*.java` = A248/MorePaperLib@master `src/main/java/space/arim/morepaperlib/scheduling/` |
| Docs | https://docs.papermc.io/folia/reference/overview/ , /region-logic/ , /faq/ ; https://docs.papermc.io/paper/dev/folia-support/ ; https://docs.canvasmc.io/canvas/** (source: github.com/CraftCanvasMC/Docs) |

Baseline read: `sculk-common/src/main/kotlin/studio/sculk/scheduler/SculkScheduler.kt` (12 scheduling
methods + 3 ownership checks + `runNow` inline fast path) and
`sculk-platform/src/main/kotlin/studio/sculk/platform/PaperScheduler.kt` (single code path over Paper's
Folia-compatible API; normalises delay ≤ 0; `retired` callback re-runs the task; `ServerFlavour.isFolia`
by class presence of `io.papermc.paper.threadedregions.RegionizedServer`).

---

## 1. Folia model

### 1.1 Regions and threads

| Fact | Detail | Source |
| --- | --- | --- |
| Unit of ownership | A *region* = set of **region sections**; a section = `N×N` chunks, `N = 1 << shift`. Section coord = `chunk >> shift`. Regions own whole sections, never partial ones. | docs region-logic; `[F-M]` `ThreadedRegionizer` |
| Shift | `threaded-regions.grid-exponent` in `paper-global.yml`, default **4** (16×16 chunks = 256×256 blocks per section), clamped 0..31 → `TickRegions.regionShift`. | `[F-P]` l.437 `gridExponent = 4`; `[F-M]` l.5807-5810 |
| Regionizer constants (shift 4) | `minSectionRecalcCount = max(1, 2048 >> (2*shift))` = 8; `maxDeadRegionPercent = 1/6`; `emptySectionCreateRadius = max(1, 8 >> shift)` = 1; `regionSectionMergeRadius = 1`. Effective merge radius = `max(merge, emptyCreate)` = 1 section. | `[F-M]` l.11340-11348, l.4333 |
| Invariants | (1) every chunk holder belongs to exactly one region; (2) every chunk within the merge radius of an owned holder is owned by the same region; (3) a ticking region cannot grow — new chunks around it form *transient* regions merged back after the tick; (4) states `TRANSIENT → READY → TICKING → DEAD`. | docs region-logic |
| Tick threads | `TickRegionScheduler` pool, size `threaded-regions.threads` (default -1 = auto). Each region ticks at 20 TPS with its own deadline; scheduled EDF-like via `SchedulerThreadPool`; a region is **not** pinned to a thread between ticks. Tick threads are `TickRegionScheduler.TickThreadRunner extends TickThread`. | `[F-P]` l.436; `[F-M]` l.5429; docs overview |
| Global region | One task at 20 TPS: game rules, global game time, daylight time, console commands, world border, weather, sleep skipping. Never merged/split, owns no region data. | docs overview; `[P-api/GlobalRegionScheduler]` javadoc |
| Per-region time | "Current tick" and redstone time are per region; global game time + daylight copied at region tick start. On merge, deadlines of the absorbed region are offset by `toTick - fromTick`; on split children copy the parent's tick. | docs overview |
| Region data | `RegionizedData` (NMS) is region-local; **not exposed in Folia's API** because the provider runs under critical locks. Canvas exposes it (§3). | docs overview |
| Thread checks | `TickThread.isTickThreadFor(world, chunkX, chunkZ)` = `TickRegionScheduler.getCurrentRegion()` owns `regioniser.getRegionAtUnsynchronised(chunkX, chunkZ)`; rectangle overload loops over every section in the range; returns `true` on `RegionShutdownThread`. Entity overload: removed/unplaced entities are checked against their last chunk position; players mid-config are owned by the connection's thread. | `[F-P]` l.7-250 (TickThread hunk) |
| Thread-check reasons present in Folia | `Cannot asynchronously load chunks`, `Updating block asynchronously`, `Adding block without owning region`, `Cannot getEntities asynchronously`, `Cannot teleport entity async`, `Cannot portal entity async`, `Cannot respawn entity async`, `Cannot read passengers off of the main thread`, `Cannot adjust passengers/vehicle off of the main thread`, `Cannot add effects to entities asynchronously`, `Tile entity must be owned by current region`, `Cannot tick an entity off-main`, `Asynchronous sendSystemMessage to a command block`, `Cannot queue block even data async`. No check on inventory open/close or `Player#sendMessage`. | `grep ensureTickThread [F-M]` |
| Block updates | Physics/neighbour updates are suppressed outside `isTickThreadFor(level, pos, 25)` instead of crashing. | `folia-server/minecraft-patches/features/0004-Prevent-block-updates-in-non-loaded-or-non-owned-chu.patch` |

**What owns what**

| Object | Owner | Notes |
| --- | --- | --- |
| Chunk, block, tile entity, POI | Region owning the chunk's section | Section is the atomic unit; a 1-section buffer of *owned* positions around any loaded holder is guaranteed (invariant 2), but *loaded* is not guaranteed. README's "≈8 chunks" is a rule of thumb, not a contract. |
| Entity (non-player) | Region owning its current chunk | Cross-region move = remove + re-add; `EntityScheduler` is re-registered to the new region's `entitySchedulerTickList` (`[F-M]` l.2965, 3075, 15564). Cross-*world* teleport changes the NMS object but **not** the scheduler (`RemovalReason.CHANGED_DIMENSION` does not retire it, `[P-srv/Entity.java.patch]` l.1870). |
| Player | Region owning its chunk; connection packets processed by that region | Teleport-accept from a position outside the region = disconnect (`features/0010`). Passenger desync >1 chunk → teleported to root vehicle (`features/0009`). |
| World | No single owner; per-world `ThreadedRegionizer`; world-level data via `RegionizedWorldData` | `createWorld`/`unloadWorld` throw (§1.5). |
| Scoreboard | Nobody — global state, unsolved | `registerNewObjective`, `registerNewTeam`, `clearSlot`, `ScoreboardManager#getNewScoreboard`, `setPlayerBoard` throw `UnsupportedOperationException` (`[F-P]` l.4026-4062). Main-board reads work. |
| Commands | Entity sender → entity's region; console/RCON/`FeedbackForwardingSender` → global | `CraftServer#dispatchCommand` calls `TickThread.ensureTickThread(entity, "Dispatching command async")` or `RegionizedServer.ensureGlobalTickThread(...)`; other sender types → `UnsupportedOperationException("Dispatching command for " + sender)`. `dispatchCmdAsync` exists internally. `[F-P]` l.826-856 |
| Time/weather/gamerules/border | Global region | `ensureGlobalTickThread("Cannot set weather params async")` etc. |

### 1.2 The four schedulers — exact semantics

All four interfaces live in `io.papermc.paper.threadedregions.scheduler` and are part of **Paper's** API;
Paper ships emulations (`FoliaGlobalRegionScheduler`, `FoliaAsyncScheduler`, `FoliaEntityScheduler`,
`FallbackRegionScheduler`) so the same code runs on Paper. Folia replaces only `FallbackRegionScheduler`
with `FoliaRegionScheduler` and `isGlobalTickThread()` (`[F-P]` l.383-387).

| Scheduler | Obtain | Methods | Delay/period validation | Where it runs on Paper / Folia | Source |
| --- | --- | --- | --- | --- | --- |
| `GlobalRegionScheduler` | `Bukkit.getGlobalRegionScheduler()` | `execute(Plugin, Runnable)`, `run(Plugin, Consumer<ScheduledTask>)`, `runDelayed(Plugin, Consumer, long delayTicks)`, `runAtFixedRate(Plugin, Consumer, long initialDelayTicks, long periodTicks)`, `cancelTasks(Plugin)` | `run` = `runDelayed(…, 1)`; `execute` = `run`; `runDelayed(delay ≤ 0)` → `IllegalArgumentException("Delay ticks may not be <= 0")`; `runAtFixedRate` throws for `initialDelay ≤ 0` or `period ≤ 0`; disabled plugin → `IllegalPluginAccessException` | Paper: main thread. Folia/Canvas: global region thread. Exceptions in tasks are logged at WARNING, not propagated. | `[P-srv/server-FoliaGlobalRegionScheduler]` l.41-96 |
| `RegionScheduler` | `Bukkit.getRegionScheduler()` | `execute(Plugin, World, cx, cz, Runnable)` / `execute(Plugin, Location, Runnable)`, `run`, `runDelayed`, `runAtFixedRate` (same shapes, world+chunk or `Location`). **No `cancelTasks`.** | Same `≤ 0` throws. `execute`/`run` enqueue to `RegionizedServer.taskQueue.queueTickTaskQueue(world, cx, cz, …)` → runs on the *next tick* of the owning region, creating the region if needed and holding `TicketType.REGION_SCHEDULER_API_HOLD` at `MAX_TICKET_LEVEL` for the chunk (keeps a holder, does **not** load the chunk). Javadoc: "entirely inappropriate … for entities". | Paper: `FallbackRegionScheduler` forwards everything to the global scheduler. Folia: `FoliaRegionScheduler`, per-region `Scheduler` with `merge/split` callbacks. | `[P-srv/server-FallbackRegionScheduler]`; `[F-M]` FoliaRegionScheduler hunk; `[P-api/RegionScheduler]` |
| `EntityScheduler` | `entity.getScheduler()` | `boolean execute(Plugin, Runnable run, @Nullable Runnable retired, long delay)`; `@Nullable ScheduledTask run(Plugin, Consumer<ScheduledTask>, @Nullable Runnable retired)`; `runDelayed(…, retired, long delayTicks)`; `runAtFixedRate(…, retired, long initialDelayTicks, long periodTicks)`. **No `cancelTasks`.** | `execute`: delay `< 1` treated as 1, returns `false` if retired. `run` = `runDelayed(…, 1)`; `runDelayed`/`runAtFixedRate` throw `IllegalArgumentException` for `≤ 0`; return **`null` if the scheduler is retired** (entity gone). Thread-safe to call from anywhere. | Paper: emulated on main thread with the same retire semantics (Paper's `EntityScheduler` class is the real implementation, not a stub). Folia/Canvas: ticked in the owning region's tick after plugin region tasks. | `[P-api/EntityScheduler]`; `[P-srv/server-EntityScheduler]` l.149-213 |
| `AsyncScheduler` | `Bukkit.getAsyncScheduler()` | `runNow(Plugin, Consumer)`, `runDelayed(Plugin, Consumer, long delay, TimeUnit)`, `runAtFixedRate(Plugin, Consumer, long initialDelay, long period, TimeUnit)`, `cancelTasks(Plugin)` | `delay < 0` → IAE (**0 is allowed**); `period ≤ 0` → IAE. Pool: `ThreadPoolExecutor(core = max(4, cpus/2), max = MAX_VALUE)` + one timer thread. | Identical on all three. | `[P-srv/server-FoliaAsyncScheduler]` l.26-119 |

`ScheduledTask` (`[P-api/ScheduledTask]`): `getOwningPlugin()`, `isRepeatingTask()`, `cancel(): CancelledState`,
`getExecutionState()`, `isCancelled()`. `CancelledState` = `CANCELLED_BY_CALLER`, `CANCELLED_ALREADY`, `RUNNING`,
`ALREADY_EXECUTED`, `NEXT_RUNS_CANCELLED`, `NEXT_RUNS_CANCELLED_ALREADY`. `ExecutionState` = `IDLE`, `RUNNING`,
`FINISHED`, `CANCELLED`, `CANCELLED_RUNNING`. Cancelling a running one-shot task does nothing (`RUNNING`).

**Retired callback semantics** (`[P-srv/server-EntityScheduler]` l.104-145, 180-213):

- `retire()` is called from `Entity.setRemoved(reason)` for **non-players when `reason != CHANGED_DIMENSION`**, and
  from `PlayerList.remove(player)` on disconnect (`[P-srv/Entity.java.patch]` l.1870-1892; `PlayerList.java.patch` l.363).
  Server shutdown → players removed → retire fires during shutdown.
- On retire every pending one-shot and repeating task gets its `retired` callback **once**, synchronously, on the
  thread performing the removal (the owning region). "Either run or retired is invoked, never both."
- Javadoc: retired runs "in critical code" — must not remove entities, load chunks/worlds, or modify ticket levels.
  Sculk 5's `PaperScheduler.retired(task) = task.run()` violates this when the task touches world state.
- `executeTick()`: if a task queues a teleport synchronously, remaining tasks are deferred to the next tick
  (`if (!TickThread.isTickThreadFor(thisEntity)) break;`). Tasks therefore never observe the entity mid-move.

**Teleport across regions** (`[F-M]` l.14843-15107 `Entity.teleportAsync`, `placeInAsync`; docs overview):
`Entity#teleportAsync(Location[, TeleportCause][, TeleportFlag...]): CompletableFuture<Boolean>` must be called on the
entity's owning region (`ensureTickThread "Cannot teleport entity async"`). Same-region teleports snap in place.
Cross-region/cross-world: transform step → entity removed from the source region → async placement through
`RegionizedTaskQueue` in the destination (chunk loaded with `TELEPORT_FLAG_LOAD_CHUNK`) → future completes.
Portal teleports add an async exit-portal search/create step. Internal flags: `TELEPORT_FLAG_LOAD_CHUNK = 1L<<0`,
`TELEPORT_FLAG_TELEPORT_PASSENGERS = 1L<<1`, `TELEPORT_FLAG_UNMOUNT = 1L<<2`. Completion callback thread is the
destination region's tick thread (observed in `placeInAsync`; UNVERIFIED as a documented guarantee — test T3).

**Shutdown** (`[F-M]` `RegionShutdownThread`; docs overview): a dedicated `RegionShutdownThread extends TickThread`
(1) halts `TickRegions.getScheduler()` with a 60 s wait, (2) `MinecraftServer.stopServer()` (disables plugins, closes
connections but does not remove players), (3) halts chunk systems, (4) completes pending simple teleports at their
destination and returns pending portal teleports to the source chunk, (5) saves regions/players. Consequences:
pending global/region/entity tasks are **never run** after the halt; `onDisable` runs on the shutdown thread, where
`TickThread.isShutdownThread()` makes every ownership check return `true`, so disable-time saves may touch any
entity or world. Plugin disable cancels only `BukkitScheduler`, `GlobalRegionScheduler` and `AsyncScheduler` tasks
(`[P-srv/PaperPluginInstanceManager]` l.263-278); there is no API to cancel a plugin's region or entity tasks.

### 1.3 Ownership-check API (Paper API, implemented by all three)

`org.bukkit.Server` / static mirrors on `org.bukkit.Bukkit` (`[P-api/Server]` l.2709-2800):

```
boolean isOwnedByCurrentRegion(World world, io.papermc.paper.math.Position position)
boolean isOwnedByCurrentRegion(World world, Position position, int squareRadiusChunks)   // Chebyshev radius
boolean isOwnedByCurrentRegion(Location location)
boolean isOwnedByCurrentRegion(Location location, int squareRadiusChunks)
default boolean isOwnedByCurrentRegion(Block block)
boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ)
boolean isOwnedByCurrentRegion(World world, int chunkX, int chunkZ, int squareRadiusChunks)
boolean isOwnedByCurrentRegion(World world, int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ)
boolean isOwnedByCurrentRegion(Entity entity)   // "the only appropriate method of checking for ownership of an entity"
boolean isGlobalTickThread()
boolean isPrimaryThread()
double[] getRegionTPS(Location) / (Chunk) / (World, int, int)   // Folia-only API (folia-api 0004); null if no region
```

Paper: `isPrimaryThread()` and `isGlobalTickThread()` both return `TickThread.isTickThread()` (main thread), and
every `isOwnedByCurrentRegion` is true on the main thread (`[P-srv/CraftServer]` l.366, 2299).
Folia: `isGlobalTickThread()` = `RegionizedServer.isGlobalTickThread()` (`INSTANCE.tickHandle ==
TickRegionScheduler.getCurrentTickingTask()`); `isPrimaryThread()` is **true on every region thread and the
global thread** (all are `TickThread`s). `getTPS()`/`getAverageTickTime()` throw `UnsupportedOperationException("Not
on any region")` when called off a region thread (`[F-P]` l.894, 919).

### 1.4 Event dispatch threading

- `PaperEventManager.callEvent` is unchanged by Folia: `event.isAsynchronous() && isPrimaryThread()` → throws "may only
  be triggered asynchronously"; `!isAsynchronous() && !isPrimaryThread() && !isStopping()` → throws "may only be
  triggered synchronously" (`[P-srv/PaperEventManager]` l.37-41). Because every region thread is primary on Folia,
  **firing an `async=true` event from a region thread throws**, and firing a sync event from `AsyncScheduler` throws.
- Events involving one entity fire on that entity's region; events on a target entity fire on the target's region;
  console commands on the global region. Events fire **in parallel across regions**; listener state must be
  thread-safe (README "Thread contexts for API"). The `async` modifier is "deprecated — all events fired from regions
  or the global region are considered synchronous".
- Cancelling an event from a different thread than the one firing it is meaningless on all platforms; Folia adds no
  deferred-cancel API ("Proper asynchronous events" is still *planned* in README).

### 1.5 APIs that throw `UnsupportedOperationException` on Folia (`[F-P]`/`[F-M]` grep)

| API | Behaviour |
| --- | --- |
| `BukkitScheduler` (`Bukkit.getScheduler()`) | Object exists; `CraftScheduler.handle` throws → every `runTask*`/`scheduleSync*` call throws. Interface `@Deprecated` by folia-api 0002. |
| `Entity#teleport(...)` (all sync overloads, also `Player`) | `"Must use teleportAsync while in region threading"` (`CraftEntity` l.2225, `CraftPlayer` l.3149, NMS `Entity`, `ServerPlayer`, `PlayerList`). README: "will NEVER UNDER ANY CIRCUMSTANCE come back". |
| `Server#createWorld`, `Server#unloadWorld` | `"not implemented properly yet"` |
| Scoreboard: `registerNewObjective`, `registerNewTeam`, `clearSlot`, `ScoreboardManager#getNewScoreboard`, `setPlayerBoard` | throw |
| `World#getTickableTileEntityCount` | throw |
| `Server#getTPS`/`getAverageTickTime` off-region | `"Not on any region"` |
| `dispatchCommand` with a non-entity, non-console sender | `"Dispatching command for …"` |
| `MCUtil.ensureMain`, `SparksFly.executeMainThreadTasks`, `MinecraftServer` main-thread task API, `ServerChunkCache` sync paths, `ServerLevel` legacy paths | internal throws (plugins hit these via NMS only) |
| Timings | force-disabled (folia-api 0001) |
| Vanilla commands disabled | bossbar, clone, data, datapack, debug, function, item, loot, reload, return, ride, rotate, schedule, scoreboard, spectate, spreadplayers, tag, team, teammsg, tick, trigger, perf, save-all, restart (docs FAQ; ride/rotate restored in 1.21.11 per Canvas docs). |
| Also broken per README | portal/respawn/login API, world load/unload, `/reload`. |

### 1.6 `folia-supported` and runtime detection

- `plugin.yml`/`paper-plugin.yml` `folia-supported: true` → `PluginMeta#isFoliaSupported()`; unmarked plugins are
  refused: "Could not load plugin 'X' as it is not marked as supporting Folia!" (`[F-API]` 0003; Canvas docs plugins page).
- Detection options: (a) `io.papermc.paper.ServerBuildInfo.buildInfo().isBrandCompatible(Key.key("papermc", "folia"))`
  — the PaperMC-recommended way (docs folia-support; `[P-api/ServerBuildInfo]` l.40-48);
  (b) `Class.forName("io.papermc.paper.threadedregions.RegionizedServer")` — used by Sculk 5, FoliaLib and MorePaperLib;
  true on Canvas too. Sculk 6 should use (a) with (b) as fallback, and treat "is a region-threaded server" as the
  flavour, not "is Folia".

## 2. Folia availability (fill API v3, 2026-10-12)

`https://fill.papermc.io/v3/projects/folia` lists exactly: **26.2** (group 26.2), **26.1.2**, **1.21.11, 1.21.8,
1.21.6, 1.21.5, 1.21.4**, **1.20.6, 1.20.4, 1.20.2, 1.20.1**, **1.19.4**. Nothing else has builds (no 1.21.1/1.21.3
— `dev/` branches only; no 1.21.7/9/10; no 26.1.1; **no 26.3**).

| Version | Builds | Dates | Channel |
| --- | --- | --- | --- |
| 26.2 | 6 (ids 1,3-7) | 2026-07-28 → 2026-08-25 | all **BETA** |
| 26.1.2 | 3 (6-8) | 2026-05-06 | STABLE |
| 1.21.11 | 14 | 2025-12-16 → 2026-02-22 | 1-2 ALPHA, rest STABLE |
| 1.21.8 | 5 (2-6) | 2025-08-10 → 2025-09-30 | STABLE |

Paper for comparison: 26.3 has 87 builds since 2026-09-15 (STABLE), 26.2 first build 2026-06-16. Folia lags Paper by
~6 weeks per version and skips point releases; repo activity is ~1-4 commits/month (GitHub `PaperMC/Folia`, 82 open
issues, default branch `ver/26.2.x`). Maven: `dev.folia:folia-api:<ver>-R0.1-SNAPSHOT` from
`https://repo.papermc.io/repository/maven-public/`.

**Rule: never invent a Folia version.** Sculk's compatibility matrix must be generated from the fill API
(`/v3/projects/folia` → versions → `/versions/{v}/builds` channel), not from Paper's version list; a Paper version
without a Folia build gets "Paper only" in docs and in the compatibility probe.

## 3. Canvas model

### 3.1 Identity, versions, risk

| Fact | Detail | Source |
| --- | --- | --- |
| What it is | "Fork of Folia" that **upstreams from Paper directly** and bundles the Folia region-threading patch as its own base patch (`canvas-server/minecraft-patches/base/0004-Region-Threading.patch`, 36 005 lines, + `0005-Canvas-RegionizedWorldData`). ~80 region-threading fixes; some upstreamed to Folia (PRs #419-#426). | README; docs fixes/intro |
| Current builds | API `https://canvasmc.io/api/v2/builds?project=canvas`: **26.3** builds 976-978 (2026-10-11, `channelName: stable`), 26.2 builds 882-962 (2026-07-26 → 09-29), 26.1.2 builds 723-876, 1.21.11 builds 605-794, 1.21.8 builds 416-603. 437 builds total, none `isExperimental`. Canvas shipped 26.3 **before Folia has any 26.3 build**. | `canvas-api-json/all.json` |
| LTS policy | Latest version fully supported; previous major gets backports until the next major. Docs page still names 26.2 as "current" — docs lag the builds. | docs /canvas/lts/ |
| "V3" | **UNVERIFIED** — no "V3" term appears in docs, README, or branch names (`ver/26.2`, `pre-merger/26.2`, `dev/new-region-profiler`, `dev/extended-view`). Treat as a community label for the 26.x line until confirmed. | GitHub branches |
| Java | **Java 25+** required (README; docs installation; project-setup uses toolchain 25). | |
| Maven | repo `https://maven.canvasmc.io/public` (project-setup, userdev) — older API-overview page says `/snapshots`; coords `io.canvasmc.canvas:canvas-api:26.1.2.build.+` (Gradle dynamic) or `1.21.11-R0.1-SNAPSHOT` style. Javadoc base `https://maven.canvasmc.io/javadoc/releases/io/canvasmc/canvas/canvas-api`. Dev bundle via `io.canvasmc.weaver.userdev` 2.4.5 — internals only, not needed for API plugins. | docs project-setup, userdev; `/api/v2/projects` |
| plugin.yml | `folia-supported: true` **or** `canvas-supported: true` (`PluginDescriptionFile.CANVAS_SUPPORTED_KEY`). Use `canvas-supported` only when depending on Canvas-only API. No bypass for unmarked plugins. | `[C-API]` 0004; docs api/overview |
| Detection | `ServerBuildInfo.BRAND_CANVAS_ID = Key.key("canvasmc", "canvas")` added next to `BRAND_FOLIA_ID` (`[C-API]` 0001-Rebrand l.42-46): `buildInfo().brandId().equals(Key.key("canvasmc","canvas"))`. Class fallback: `io.canvasmc.canvas.region.RegionTickData`. Whether `isBrandCompatible("papermc:folia")` returns true on Canvas is UNVERIFIED (server impl not in the public patch tree). | |
| Horizon | Separate CanvasMC project: "MIXIN wrapper for PaperMC servers and forks", a Paperclip-replacement launcher jar dropped **next to** the server jar; MIT; supports MC > 1.20.6. **Not required for Canvas** — no reference in Canvas's build files or patches; Canvas ships as an ordinary paperclip jar from Jenkins. Only relevant if Sculk ever wants mixins. | Horizon README; `grep -i horizon canvas/` |
| License | Server GPL-3.0 (inherits Paper server). `canvas-api` licence not separately stated → **flag**: compile-only use is the safe assumption; do not bundle. | README, repo `LICENSE` |
| Activity / risk | Pushed 2026-10-11; 374 stars, 109 forks, 3 open issues; commits daily in Aug 2026, 6 in Sep, 3 on 11 Oct. Team: 4 devs + 2 advisors (LTS page). Risks: docs lag (overview says 1.21.11, LTS says 26.2; `/helpful/compat` page 404; broken Javadoc link for `WorldUnloadAsyncEvent`); small team; `canvas-supported` lock-in; ahead-of-upstream divergence; Java 25 floor. | GitHub API |

### 3.2 Regionized data API (`[C-API]` `io.canvasmc.canvas.region.RegionTickData`)

```
<T> IRegionizedData<T> Server#createRegionizedData(BiFunction<RegionTickData, World, T> supplier,
                                                   IRegionizedData.IRegionizedCallback<T> callback)   // any time, any thread
<T> T Server#getLocalRegionizedData(IRegionizedData<T>)        // throws IllegalStateException off-region (incl. global tick)
<T> T RegionTickData#getOrCreateFromIRegionizedData(IRegionizedData<T>)

interface IRegionizedData<T>  (@ApiStatus.NonExtendable)  { T createValue(RegionTickData); IRegionizedCallback<T> getCallback(); }
interface IRegionizedCallback<T> {
  void merge(T from, T into, long fromTickOffset);   // deadlineTo = deadlineFrom + fromTickOffset; `from` is dead afterwards
  void split(T from, int chunkToRegionShift, Long2ReferenceOpenHashMap<T> regionToData, ReferenceOpenHashSet<T> dataSet);
  default long getRegionSectionCoordinates(int chunkX, int chunkZ, int chunkToRegionShift)  // ((z>>s) << 32) | ((x>>s) & 0xFFFFFFFFL)
}
```

Rules (Javadoc + docs region-data): callbacks run **under the region lock** — non-blocking, quick, must not throw
("server left in an unrecoverable state"). Nothing is called when a region dies (e.g. world unload). `split` gets no
tick offset because children start at the parent's tick. Fastutil types leak into the API (`Long2ReferenceOpenHashMap`,
`ReferenceOpenHashSet`).

### 3.3 Regionizer and tick data

`World#getRegionizer(): WorldRegionizer` (`[C-API]` 0002 World.java):
`getWorld()`, `computeForAllChunkRegions(Consumer<ChunkRegion>)` (**synchronized**),
`computeForAllChunkRegionsUnsynchronized(...)`, `getSectionShift()`, `getRegionSectionMergeRadius()`,
`getEmptySectionCreateRadius()`, `getMaxDeadRegionPercent()`, `@Nullable getRegionAtUnsynchronised(cx, cz)`,
`@Nullable getRegionAtSynchronised(cx, cz)`. `ChunkRegion`: `hasNoAliveSections()`, `getDeadSectionPercent()`,
`long[] getOwnedPackedChunkPositions()` (`(z & 0xFFFFFFFFL) << 32 | (x & 0xFFFFFFFFL)`), `getState()`
(`TRANSIENT/READY/TICKING/DEAD`), `getTickData()`, `getRegionizer()`, `getId()` (unique per region object; split
discards the object). `RegionTickData`: `getRegion()`, `getWorld()`, `getTickManager(): RegionHandle`,
`getMSPT/getTPS/getUtilization(Frame)` (`_5_SECONDS … _15_MINUTES`), `getCurrentTick()`,
`propagateSaveAllTicket(boolean flush): CompletableFuture<Void>` ("DO NOT BLOCK"). Also
`Server#getRegionAverageTickTimes(Location)` and Folia's `getRegionTPS` overloads.

Lock-free vs locked: `*Unsynchronised` reads are what the server uses on its own tick path (safe only from the owning
thread or for best-effort snapshots); `*Synchronised` takes the regionizer lock and must not be called from merge/split
callbacks. Sculk's partitioner should use `getSectionShift()` only (cheap, immutable).

### 3.4 Async lifecycle events (`canvas-api/src/main/java/io/canvasmc/canvas/event/**`)

| Event | Cancellable | Fields | Thread |
| --- | --- | --- | --- |
| `EntityTeleportAsyncEvent extends EntityEvent` | yes | `getFrom()`, `getTo()`, `setTo(Location)` (cloned), `getCause(): PlayerTeleportEvent.TeleportCause`; nested `enum TeleportType { SAME_REGION, CROSS_REGION, CROSS_WORLD }` (declared, no accessor) | owning region of the entity, before removal; **must not mutate entity state** |
| `EntityPostTeleportAsyncEvent` | no | `getFrom()`, `getTo()`, `getCause()` | destination region, immediately after placement; mutation allowed |
| `EntityPortalAsyncEvent` | yes | `getFrom(): World`, `getTo(): World`, `setTo(World)`, `getPortalType()` | owning region, pre |
| `EntityPostPortalAsyncEvent` | no | `getFrom()`, `getTo()`, `getPortalType()` | destination region |
| `PlayerRespawnAsyncEvent extends AbstractRespawnEvent` | no (inherits) | `setRespawnLocation(Location)` + respawn flags/reason | UNVERIFIED |
| `PlayerPostRespawnAsyncEvent` | no | respawn info | destination region |
| `event.world.WorldUnloadAsyncEvent extends WorldEvent` | yes | — | global tick (unload runs there) |
| `PlayerSaveEvent extends PlayerEvent` | no | `isQuit()` | owning region |
| `PlayerViewEndCreditsEvent` | result-based | `willShowCredits()`, `setResult(Result)`, `willVanillaShowCredits()` | global thread (credits respawn packet is redirected there — docs behavior-fixes) |

Changing teleport preconditions in a pre-event (bounds ±30 M X/Z, ±20 M Y; passenger/vehicle rules by flags; destination
has no unload ticket; entity alive, not removed, not sleeping) → `Entity.TeleportValidationException extends
IllegalStateException` ("Teleport validation failed. Did an event change the entity state?") (`[C-S]` l.27930-27960).
Teleport during login → `IllegalStateException("Cannot teleport async during login in region threading, please schedule
this to the next tick")`; during logout events → `IllegalStateException` (`[C-S]` l.27902, 27910). All sync
`Entity#teleport` overloads carry `@io.canvasmc.canvas.Unsupported` (`[C-API]` Entity.java hunk).

### 3.5 Other Canvas additions / restorations

| Area | Canvas | Source |
| --- | --- | --- |
| Ownership checks | `+ isOwnedByCurrentRegion(World, BoundingBox aabb)`; `+ isOwnedByCurrentRegion(Location position, Vector velocity, int buffer)` (position advanced by velocity, chunk buffer). Implementation (DeepWiki, UNVERIFIED in checkout): `TickThread.isTickThreadFor(Level, AABB)` / `(Level, Vec3, Vec3, int)` → rectangle check. | `[C-API]` Server.java l.2817-2838 |
| Tick manager | `Server#getRegionThreadingTickRateManager(): RegionThreadingTickManager` — `getTickRate()/setTickRate(float)` (server-wide, any thread), `getHandleAt(Chunk)` (NPE if no region), `getGlobalRegionHandle()`, `sendUpdateToAllPlayers()`; `RegionHandle.pause()/play()/walk()/sprint(int)` queued to next region tick; `doesRunGameElements()/isSprinting()` throw ISE off-owner. Bukkit `ServerTickManager` is `@Unsupported` (throws). | `RegionThreadingTickManager.java`; docs tick-rate |
| World lifecycle | `createWorld` on **global tick thread** (except startup). `Server#unloadWorldAsync(World|String, boolean save, Consumer<WorldUnloadResult>)` on global tick; result enum `SUCCESS, FAIL_PLAYERS_JOINING, FAIL_PLAYERS_PRESENT, FAIL_ALREADY_UNLOADING, FAIL_IS_OVERWORLD, FAIL_UNLOAD_EVENT, FAIL_IS_SHUTDOWN, FAIL_UNKNOWN`. Unload propagates an `UnloadTicket`; regions finish teleports, send players back, save, deschedule; global tick finalises. Bukkit `unloadWorld` still throws. | `WorldUnloadResult.java`; docs world-loading |
| Scoreboards | Restored (Folia: throws). SWMR: **all writes on the global tick** (register/unregister teams & objectives, team properties, display slots, saves) — enforced; team membership changes on the **entity's** context; reads from any thread; `setPlayerBoard`/`Player#setScoreboard` on the player's region. `/scoreboard players operation` fails across regions. No new API. | docs fixes/scoreboards; DeepWiki |
| Commands | Restored: bossbar, dialog, loot, spectate, spreadplayers, tag, tick (reimplemented), waypoint, save-all, scoreboard, trigger, team, teammsg, data (storage disabled 26.1+), clone; `tp` keeps yaw/pitch. Canvas-side execution framework `io.canvasmc.canvas.threadedregions.command.*`. Dispatch rules identical to Folia (entity → entity scheduler; console/RCON → global). | docs fixes/command-fixes, api/scheduling |
| `@io.canvasmc.canvas.Unsupported(reason)` | Runtime-retained marker on `BukkitScheduler`, `ServerTickManager`, `Server#restart`, all sync `Entity#teleport`, a `Datapack` method, `OfflinePlayer#getRespawnLocation` (→ `getRespawnLocationAsync(boolean loadLocationAndValidate): CompletableFuture<Optional<Location>>`). Sculk can scan for it reflectively in a Canvas probe. | `Unsupported.java`; `[C-API]` 0002 |
| Ender pearls / end credits / max players | Pearls persisted server-wide in `pearls.dat`; credits respawn finaliser on global thread; max-player off-by-one fixed. | docs behavior-fixes |
| Schedulers (tick runners) | `threaded-regions.scheduler` = `EDF` (Folia's, `SchedulerThreadPool`), `WORK_STEALING` (NUMA-aware; "known to cause issues"), `AFFINITY` (Canvas default: `TickRegionScheduler.DEFAULT_SCHEDULER = RegistryEntries.AFFINITY`, `[C-S]` l.1256). Affinity options: `enableMidTickTasks`, `enableWorkStealing`, `runTasksBufferMillis` (0.1), `stealThresholdMillis` (3), `tickRegionAffinity[]`, `enableAffinitySchedulerCpuAffinity` (Linux). **Not observable from the API**; plugins only see `RegionTickData` metrics. "Beta scheduler" (Folia branch) is removed/deprecated. | docs scheduler/*; `[C-S]` l.1227-1352 |
| Plugin compatibility mode | Search index mentions a config "forcing one thread to fire events at a time"; page is **404** and no such key exists in `[C-S]`. **UNVERIFIED / likely removed.** | search result |
| Spark region profiling, region TPS bars (`/regionbar`), `/tick` per region, `/worlddistance` | admin-side only | docs guides/canvas-commands |

## 4. Paper vs Folia vs Canvas — operations Sculk cares about

"Owner" = thread that must run it. P = Paper main thread. Folia/Canvas cells differ only where noted.

| Operation | Paper | Folia | Canvas |
| --- | --- | --- | --- |
| Open/close inventory for player | main | player's region (`EntityScheduler`). Not thread-checked — races silently if done from global/other region (Canvas docs rule 7). | same |
| Modify block | main | region owning the chunk, chunk loaded (`"Updating block asynchronously"`, `"Adding block without owning region"`); physics suppressed outside radius-25 ownership | same |
| Spawn entity | main | region owning the spawn location (`World#spawn` → chunk ownership check) | same |
| Teleport | `teleport` or `teleportAsync` | `teleportAsync` only, from the entity's region; sync throws | same; sync `@Unsupported`; pre/post async events; throws during login/logout |
| Dispatch command | main (`AsyncCatcher`) | entity sender → entity's region; console/RCON → global; else UOE | same |
| Read another player's data (`getLocation`, inventory) | main | **undefined unless `isOwnedByCurrentRegion(otherPlayer)`**; schedule on that player's `EntityScheduler`, pass a snapshot back | same; `PlayerSaveEvent` for persistence hooks |
| Scoreboard/team edits | main | create/register/display-slot throw; main-board reads only | restored: writes on **global tick**, membership on entity's region |
| World load/unload | main | both throw | `createWorld` on global tick; `unloadWorldAsync(…, callback)` on global tick |
| Chunk load (sync `getChunkAt`) | main | throws off-region (`"Cannot asynchronously load chunks"`); use `getChunkAtAsync` → future completes on owning region (UNVERIFIED thread; test T5) | same |
| Broadcast message | any (packets) | `Player#sendMessage`/`Audience` from any thread works (no check; Netty queues) — official guarantee UNVERIFIED | same |
| PDC access | main for entity/chunk holders | entity PDC → entity's region; chunk PDC → chunk's region and loaded; `ItemStack` PDC is plain data (any thread) | same |
| Cancel event from async context | impossible (sync events fire on main; async events are not cancellable from elsewhere) | same + `async=true` events cannot be fired from region threads at all | same; teleport/portal/respawn pre-events cancellable **only** on the firing region thread |
| "Main thread" check | `isPrimaryThread()` | `isGlobalTickThread()`; `isPrimaryThread()` is true on every tick thread | same |
| Plugin disable cleanup | Bukkit+global+async tasks cancelled | global+async cancelled; region/entity tasks not cancellable | same |

## 5. Draft Sculk 6 threading contract

### 5.1 `ExecutionContext` kinds → platform primitive

| Kind | Key | Paper | Folia | Canvas | Owns-check |
| --- | --- | --- | --- | --- | --- |
| `Global` | — | `GlobalRegionScheduler` (= main) | `GlobalRegionScheduler` | same | `isGlobalTickThread()` (Folia/Canvas), `isPrimaryThread()` (Paper) — exactly Sculk 5's `ownsGlobalThread()` |
| `Region` | `(world, chunkX, chunkZ)` canonicalised to the **section** `(world, cx >> shift, cz >> shift)` | `RegionScheduler` (forwards to global) | `RegionScheduler.run(plugin, world, cx, cz, …)` | same; `+ BoundingBox`/velocity checks | `isOwnedByCurrentRegion(world, cx, cz)` / rectangle overload for ranges |
| `Entity` | `Entity` (UUID + live ref) | `entity.getScheduler()` (retire semantics are real on Paper too) | same, follows teleports incl. cross-world | same | `isOwnedByCurrentRegion(entity)` — never derive from `entity.location` |
| `Async` | — | `AsyncScheduler` / `Dispatchers.IO` | same | same | never touches Bukkit API except documented thread-safe calls (`sendMessage`, `teleportAsync` is **not** one) |
| `Proxy` | — | Velocity `Scheduler` (no main thread; all handlers may be concurrent) — see workstream D | — | — | n/a |
| `RegionData<T>` (Canvas only) | `IRegionizedData<T>` | absent → capability `null` | absent | `getLocalRegionizedData` on owning thread | `isOwnedByCurrentRegion` of the data's region |

Rules the contract must state (each traces to a finding above):

1. `delay ≤ 0` means next tick and `period < 1` is clamped to 1 at the Sculk boundary (platform throws IAE) — keep
   Sculk 5's normalisation. Async delays use real time (`TimeUnit`), 0 allowed.
2. Entity work carries an explicit outcome: `Ran | EntityGone`. `EntityGone` is produced from a `null`
   `ScheduledTask` or from the `retired` callback; the retired callback itself must only complete the deferred —
   **never run the original body** (it is "critical code" and the entity is gone). Replace
   `PaperScheduler.retired(task) = task.run()` with completion-only.
3. No `cancelTasks` exists for region/entity tasks → Sculk keeps its own registry of live `ScheduledTask`s per
   plugin and cancels them in `onDisable` (platform cancels global+async itself).
4. Shutdown: after `RegionShutdownThread` halts the pool no scheduled task runs; `onDisable` is on the shutdown thread
   where all ownership checks pass → Sculk's save-on-disable path must be synchronous and not schedule.
5. Commands: Sculk's `CommandSpec` executor dispatches on the sender's owner (entity scheduler for entities; global
   for console); command callbacks already run there, so `runNow` inline is correct.
6. Events: Sculk event listeners are invoked in parallel across regions; shared state in `ServiceRegistry` must be
   thread-safe or region-partitioned; a Sculk `async` event type must never be fired from a region thread.
7. Scoreboards/HUD: on Folia the sidebar must be packet-only (`sculk-hud` already is); on Canvas Bukkit scoreboards
   are legal but writes go through `Global`.
8. Ownership checks are the only correctness oracle; distance heuristics ("≈160 blocks", "8 chunks") are not.

### 5.2 Ownership-aware coroutine dispatchers

- `SculkDispatcher(kind, key)`: `isDispatchNeeded(ctx) = !owns(key)`; `dispatch(ctx, block) = scheduler.run(kind, key) { block.run() }`.
  Inline resumption when already owned mirrors Sculk 5's `runNow` and keeps packet loops in one tick.
- Resumption after `teleportAsync().await()`: the continuation must resume on **`Entity(entity)`**, never on the
  `Region` dispatcher captured before the call — the region key is stale after a cross-region/cross-world move. Rule:
  `suspend fun Entity.teleport(...)` returns on the entity dispatcher; any `Region`-keyed `withContext` around it is
  an error the API should make impossible (e.g. `teleport` is only exposed on an `EntityScope` receiver).
- Re-check after every suspension point in `Region` context: ownership is by *section*, and a section can merge/split
  while suspended; the dispatcher re-schedules via `RegionScheduler` on resume, so correctness holds by construction,
  but code must not cache `World#getChunkAt` results across a suspension.
- `Unconfined`/`Dispatchers.Default` resumption of a region continuation is forbidden; wrap platform futures
  (`CompletableFuture.await()`) so completion re-dispatches to the declared kind (Folia completes `teleportAsync` on the
  destination thread, Paper on main — never assume).
- Structured cancellation: `Job.cancel()` → `ScheduledTask.cancel()`; a `RUNNING` result means the body is executing
  and cancellation is cooperative only.

### 5.3 Region-safe batch partitioner

- Input `Collection<Location>` → group by `(world, sectionKey)` with `sectionKey = ((cz >> s).toLong() shl 32) or ((cx >> s).toLong() and 0xFFFFFFFF)`
  — identical to Canvas's `getRegionSectionCoordinates`. `s`: Canvas `World#getRegionizer().getSectionShift()`;
  Folia: read `threaded-regions.grid-exponent` from `paper-global.yml` (default 4), optionally confirmed by
  reflection on `io.papermc.paper.threadedregions.TickRegions.getRegionChunkShift()` (UNVERIFIED stable name);
  Paper: one group per world (or a single `Global` group).
- Why sections: a section is wholly owned by exactly one region at any instant, so a section-group scheduled with
  `RegionScheduler.run(world, anyChunkInSection)` is executed by the region that owns the **entire** group. Inside the
  task, assert `isOwnedByCurrentRegion(world, minCX, minCZ, maxCX, maxCZ)`; on failure (wrong shift) split the group
  per chunk and reschedule. Chunks may still be unloaded — block edits need `getChunkAtAsync` first.
- Guarantees it can make: items within one group run sequentially in submission order inside one tick of one region;
  the whole batch completes via a join (`awaitAll` of per-group deferreds) on `Global` or `Async`. Guarantees it
  cannot make: cross-group ordering, same-tick completion across regions, ordering relative to other plugins' tasks,
  that merged regions coalesce two groups into one tick, or that a group's region is the same region that owned it
  at submission time.

## 6. Test scenarios (automatable against real Folia/Canvas jars)

| # | Scenario | Setup | Assert |
| --- | --- | --- | --- |
| T1 | Entity moves between regions during a delayed entity task | Two players ≥ 2 sections apart (shift 4 → ≥ 512 blocks) create two regions; schedule `Entity` task with 40-tick delay; `teleportAsync` the mob to the other region at tick 10 | Task runs once, on the destination region (`isOwnedByCurrentRegion(entity)` true inside), `EntityGone` not produced |
| T2 | Disconnect during delayed entity op | Schedule 100-tick `Entity` task on a player; disconnect at tick 20 | `retired` fires exactly once on the player's region, outcome `EntityGone`, body never runs; repeating variant stops |
| T3 | Teleport during scheduled region work | `Region` task reads a block at L1 while `teleportAsync` moves the player to L2 in another region | Region task unaffected; continuation after `teleportAsync().await()` is on the destination region; Canvas: `EntityTeleportAsyncEvent` on source region, `EntityPostTeleportAsyncEvent` on destination |
| T4 | Region unload | Players leave; chunks unload; a repeating `Region` task is pending at that location | Task keeps running (REGION_SCHEDULER_API_HOLD keeps a holder) or is documented to stop — record actual; chunk is **not** loaded by the task |
| T5 | Multi-region world ops | Partitioner batch covering 4 sections across 2 live regions + unloaded area | Every item executes with `isOwnedByCurrentRegion(rect)` true; unloaded items load via `getChunkAtAsync`; join completes; no `"Updating block asynchronously"` |
| T6 | Player inventory from wrong context | Open a menu from `Global` and from another region vs from `Entity(player)` | Only the entity-context path is accepted by Sculk (contract test); server-side nothing throws — document the race |
| T7 | Shutdown with pending tasks | Schedule global/region/entity/async tasks 5 s out; `stop` | None run after halt; `onDisable` save succeeds (ownership checks pass on shutdown thread); async tasks are cancelled |
| T8 | Canvas regionized data merge/split under movement | Register `IRegionizedData<Counter>`; two players converge (merge) then separate (split) | `merge` offset preserves relative deadlines; `split` distributes by `regionToData`; callbacks never block (wall-clock assert < 1 ms) |
| T9 | Delay-zero normalisation | `runSyncDelayed(0)`, `runSyncRepeating(0, 0)` on all three | No IAE; runs next tick / every tick |
| T10 | Async event from region thread | Fire a Sculk `async=true` event from an entity task | Rejected at Sculk boundary before Paper's `IllegalStateException` |
| T11 | Command dispatch owner | Dispatch as console from a region task; as player from global | Both rejected by Sculk with a routed alternative (platform would throw UOE) |
| T12 | Version probe | Run compat probe against fill API list | Probe refuses unknown Folia versions; Canvas 26.3 recognised via `brandId` |

## 7. Competitor handling

**FoliaLib** (`[FL]`, v0.5.2 in build, last release 0.5.1 2025-06-19, Java shaded + relocation required)

- Detection: ordered `ImplementationType` class probes — `FOLIA` on `io.papermc.paper.threadedregions.RegionizedServer`;
  Paper/Spigot by config classes + reflection test for `runTask(Plugin, Consumer)`. Canvas is detected as Folia (fine).
- Abstraction: `PlatformScheduler` with `runNextTick`/`runLater`/`runTimer` (**mapped to the global region on Folia**),
  `runAtLocation*`, `runAtEntity*` (+`WithFallback`), `isOwnedByCurrentRegion` overloads, `teleportAsync` wrapper;
  `EntityTaskResult { SUCCESS, ENTITY_RETIRED, SCHEDULER_RETIRED }`; `ensureValidDuration` clamps ≤ 0 to 1 and warns once.
- What it gets wrong / leaves out: `runAtEntity` without fallback passes `retired = null` (silent drop); "sync" means
  *global region* on Folia, so entity/location work scheduled with `runNextTick` runs on the wrong thread without error;
  Paper/Spigot implementations use `BukkitScheduler`, not Paper's native region API, so entity tasks on Paper have no
  retire semantics and `isOwnedByCurrentRegion(entity) = isPrimaryThread()`; `CompletableFuture`-shaped API; no
  cancellation registry for region/entity tasks; no coroutine/ownership-aware dispatch; no Canvas awareness, no
  regionized data, no batch partitioning, no shutdown handling.

**MorePaperLib** (`[MPL]`, LGPL-3.0 — **licence flag for a sellable product**; last tag 0.4.4, pom 0.5.4-SNAPSHOT,
pushed 2026-07-14, compiles against `folia-api 1.19.4-R0.1`)

- Detection: `StandardFoliaDetection` — same `RegionizedServer` class probe.
- `GracefulScheduling`: `asyncScheduler()`, `globalRegionalScheduler()`, `regionSpecificScheduler(Location|world,cx,cz)`,
  `entitySpecificScheduler(Entity): AttachedScheduler` (`run/runDelayed/runAtFixedRate(command, alternateIfRemoved, …)`),
  `isOnGlobalRegionThread()`, `cancelGlobalTasks()`. `ScheduledTask` mirrors Paper's `ExecutionState`.
- What it gets wrong / leaves out: on Paper everything maps to `BukkitScheduler` via reflection and
  `RegionalScheduler.asAttachedScheduler()` **ignores `alternateIfRemoved` entirely**, so the entity-removed path can
  never fire on Paper — behaviour diverges between platforms; no `isOwnedByCurrentRegion` wrappers; no delay
  normalisation (IAE leaks); no Canvas awareness; no batch/partitioning; not Kotlin-friendly (Consumer overloads).

**Takeaway for Sculk 6:** both libraries stop at "pick a scheduler". Neither models ownership as a value, carries
entity-gone outcomes, re-dispatches after teleport, partitions by section, tracks cancellation for region/entity tasks,
or probes Canvas. Sculk 5's `PaperScheduler` single-code-path approach (use Paper's native Folia API on Paper) is
already better than both on the Paper side; the gaps are the retired-callback misuse, the missing cancellation
registry, and the absence of coroutine dispatchers and section math.
