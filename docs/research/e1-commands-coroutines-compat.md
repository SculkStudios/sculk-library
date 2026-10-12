# E1 — Commands, coroutines and compatibility libraries: competitor matrix for Sculk 6

Research workstream E1 for Sculk Studio 6. Written 2026-10-12. Scope: (A) command frameworks on
Paper/Folia/Velocity, (B) coroutine integration (MCCoroutine) and dispatcher best practice,
(C) cross-version compatibility libraries. Per area: integrate / abstract / build natively / leave.

Evidence base: shallow clones at the stated tags in the E1 scratchpad (`clones/<repo>`; Windows
needed `core.longpaths=true` or half the tree silently vanished), `gh api` repo/release metadata
(2026-10-12), Maven Central `Content-Length` for jar sizes, docs.papermc.io, jd.papermc.io 26.3
javadoc, project docs. **UNVERIFIED** = secondary source or inference, not checked against a primary.
Licence flags: **LGPL** is the only copyleft found; nothing GPL/CC-NC/no-licence.

> **Audit 2026-10-12.** This report assumed a 1.21.11 floor and used Sculk 5 names (`sculk-common`,
> `sculk-series`, `sculk-visual`, `SculkScheduler`). The floor is 1.21.4 and a compatibility
> generator is built after all (ADR-0002); module names are those in `MODULES.md`. The command
> verdict is refined by ADR-0022 (platform Brigadier only, no Brigadier in the neutral module), and
> the Velocity "primitives + custom" claim in §A.3 is corrected in place.

Verdict in one table:

| Area | Decision | One-line reason |
| --- | --- | --- |
| A Commands | **Native** on Paper Brigadier (+ Velocity `BrigadierCommand`); **abstract** argument types behind a Sculk registry; **leave** all four libraries | Paper 26.3 marks the whole Bukkit command path `@ApiStatus.Obsolete`; every library either wraps NMS per version or falls back to string parsing; none offers a Kotlin-first, Folia-aware, test-without-server pipeline |
| B Coroutines | **Native** dispatchers over `SculkScheduler`; **leave** MCCoroutine, copy four of its decisions | MCCoroutine is Java 8, reflects into `SimplePluginManager`/`VelocityEventManager` internals and registers commands through the now-obsolete `PluginCommand` |
| C Compat libs | **Leave** XSeries, MorePaperLib (LGPL), FoliaLib; **port** (MIT) the ParticleDisplay shape maths into `sculk-visual`; do **not** build a compat-data generator | A 1.21.11+/Adventure framework has no 1.8–1.12 problem to solve; `sculk-series` already covers registry lookups and aliases |

---

## A. Commands

### A.1 Project table

| | Paper Brigadier API | CommandAPI | Incendo cloud v2 | Lamp v4 | LiteCommands |
| --- | --- | --- | --- | --- | --- |
| Version / date | `paper-api 26.3.build.157-beta` (jd) | 12.1.0 — 2026-09-26 (12.0.0 2026-07-28) | cloud 2.1.0 — 2026-07-20; cloud-minecraft 2.0.1 — 2026-09-16 | 4.0.0-rc.19 — 2026-10-07 (RC since 2024; beta.21/22 before) | v3.11.0 — 2026-06-16 |
| Licence (SPDX) | Paper: GPL-3.0 server / MIT `paper-api` (UNVERIFIED split; gh reports NOASSERTION) | MIT | MIT | MIT | Apache-2.0 |
| Maintenance | continuous | pushed 2026-10-08, 651★ | pushed 2026-10-10 / 09-25, 548★ / 111★ | pushed 2026-10-07, 357★; still "rc" | pushed 2026-08-16, 209★ |
| MC / Java | 1.20.6+ API; Java 21 | 1.20.6–26.3 (Hangar); per-version NMS modules `commandapi-paper-{1.20.5…26.3}`; Kotlin DSL targets Java 16 | Paper 1.20.6+ modern manager, legacy manager older; Java 17 (UNVERIFIED) | 1.8+ (reflection fallback); toolchain Java 8 | Java 17 since 3.11.0 (dropped 8) |
| Paper / Folia / Velocity | native / native (commands run on owning region, Folia README) / separate Velocity API | Paper+Spigot split (11.0.0) / "basic support" since 9.0.1, runtime (un)register unsupported (#686 open) / limited: primitives, bool, string/text/greedy, literals only | `cloud-paper` / no Folia-specific code (execution thread = Paper's) / `cloud-velocity` (Player, Server parsers) | `bukkit` / no Folia mentions in code / `velocity` module (#163 optional-arg bug) | `bukkit` / `litecommands-folia` scheduler ext / `velocity` |
| Kotlin | plain Java API | `commandapi-kotlin-paper` DSL, cast-based (`args["p"] as Player`, issue #544) | `cloud-kotlin-extensions`, `-coroutines`, `-coroutines-annotations` | `SuspendFunctionsSupport` (Continuation injected; see A.4) | none |
| Brigadier registration | the tree | own `CommandDispatcher<CommandSourceStack>`; on `LifecycleEvents.COMMANDS` copies root children to `event.registrar().register(node, desc)` (`PaperCommandRegistration.java:159-172`); argument types from NMS modules | `ModernPaperBrigadier` registers on `LifecycleEvents.COMMANDS` (`buildOnEnable`/`buildBootstrapped`); cloud parsers mapped to vanilla types by `BukkitBrigadierMapper.mapSimpleNMS(...)`, unmapped parsers become string nodes with cloud suggestions | `BrigadierParser` builds native nodes; bridge chosen at runtime: Paper ≥1.20.6 `ByPaperLifecycle`, Paper 1.19+ `ByPaperEvents` (deprecated `CommandRegisteredEvent`), Spigot 1.19+ `BySpigotVanillaWrapper`, else `ByReflection` (NMS dispatcher); vanilla types via `MinecraftArgumentType` reflection enum (44 constants, commodore lineage) | **none on Bukkit/Paper**: reflects `SimpleCommandMap.knownCommands`, string-parses; Brigadier only in `litecommands-fabric` |
| Size | 0 | paper-core 33 KB, **paper-shade 616 KB**, kotlin-paper 30 KB, velocity-shade 190 KB | core 468 KB, paper 56 KB, bukkit 172 KB, brigadier 64 KB, velocity 17 KB, kotlin-coroutines 29 KB, minecraft-extras 86 KB | common 335 KB, bukkit 100 KB, brigadier 25 KB, velocity 28 KB | core 503 KB, bukkit 59 KB (Panda repo, not Central) |
| Why people use it | zero deps, datapack-visible, client-side validation | argument breadth, "vanilla-feel" commands, docs, test toolkit | pipeline/extensibility, multi-platform, Kotlin coroutines | annotation ergonomics, DI, many platforms | annotation ergonomics, many platforms, IntelliJ plugin, programmatic API |
| Known complaints | requirement changes need `Player.updateCommands()`; `resource(STRUCTURE)` can kick clients | Folia runtime registration (#686); non-type-safe DSL (#544); NMS lock-step per MC version; 616 KB shade | aliases of literals not suggested (cloud-minecraft #5, intentional); exceptions swallowed on Brigadier path (#110); no compile-time annotations (#685) | registering after startup on Paper broken (#157); flag docs missing (#134); endless RC | no Brigadier → no client validation/tooltips; Panda repo only |

Sources: jd.papermc.io/paper/26.3 (`Commands`, `JavaPlugin`, `CommandExecutor`, `PluginCommand`); docs.papermc.io/paper/dev/command-api/*; github.com/CommandAPI/CommandAPI (tag 12.1.0), Incendo/cloud, Incendo/cloud-minecraft, Revxrsal/Lamp (v4), Rollczi/LiteCommands; cloud.incendo.org/minecraft/paper, /minecraft/bukkit, /core, /kotlin/coroutines; docs.commandapi.dev/velocity/intro, /test/intro, /kotlin-dsl/intro.

### A.2 State of the legacy path in Paper 26.x

| Fact | Evidence |
| --- | --- |
| `CommandExecutor`, `CommandExecutor.onCommand`, `PluginCommand`, `JavaPlugin.onCommand/onTabComplete/getCommand` carry `@ApiStatus.Obsolete(since="26.3")`; API note: "plugin developers should prefer to use the Brigadier command API"; "Basic commands are recommended" as the direct alternative. Not `@Deprecated`, not removed; `setExecutor`/`setTabCompleter` inherit class-level obsolescence. | jd.papermc.io/paper/26.3/org/bukkit/command/CommandExecutor.html, …/PluginCommand.html, …/plugin/java/JavaPlugin.html |
| Legacy `CommandRegisteredEvent` deprecated since 1.20.6, "slated for removal". | jd.papermc.io/paper/1.21.5/com/destroystokyo/paper/event/brigadier/CommandRegisteredEvent.html |
| `JavaPlugin.registerCommand(label, [description], [aliases], BasicCommand)` — 4 overloads, **no** `@Experimental`/`@Obsolete` in 26.3. Still the only non-lifecycle entry point. | jd 26.3 `JavaPlugin` |
| `Commands.register(...)`: 5 `LiteralCommandNode<CommandSourceStack>` overloads (+ `registerWithFlags` `@Internal`), 5 `BasicCommand` overloads; all return `Set<String>` of registered labels incl. namespaced variants; helpers `literal()`, `argument()`, `restricted(Predicate)` (1.21.6+), `getDispatcher()` ("prefer register methods"). | jd 26.3 `io.papermc.paper.command.brigadier.Commands` |
| Registration contract: `LifecycleEvents.COMMANDS` on `BootstrapContext` (needs `paper-plugin.yml`, commands visible to datapack functions) or on the plugin's `LifecycleEventManager`; handler re-runs on every reload, so no manual `/reload` handling. `paper-plugin.yml` has no `commands:` section. | docs.papermc.io/paper/dev/command-api/basics/registration; …/getting-started/paper-plugins |
| Requirements (`requires(Predicate<CommandSourceStack>)`) are not pushed to the client when they change → `Player.updateCommands()` (thread-safe, bandwidth-heavy). | docs …/basics/requirements |
| Execution thread: Folia — "Commands for entities/players are called on the region which owns the entity/player. Console commands are executed on the global region." Paper: main thread (UNVERIFIED from docs page; vanilla behaviour). | github.com/PaperMC/Folia README §"Thread contexts" |

Conclusion: legacy registration still compiles and runs in 26.3 but is formally obsolete; `BasicCommand` is Paper's own "string-array command" and is what MCCoroutine-style `setSuspendingExecutor` should have targeted. Sculk 6 should not emit a single `PluginCommand`.

### A.3 Argument-type coverage matrix (deep dive 1)

Legend: **N** = native Brigadier node (client validates + suggests) · **S** = string node parsed server-side by the library · **–** = absent · `*` = Paper-only module. Paper column lists the `ArgumentTypes` factory.

| Type | Paper Brigadier | CommandAPI 12.1 | cloud 2.x | Lamp 4 rc.19 | LiteCommands 3.11 |
| --- | --- | --- | --- | --- | --- |
| literal / multi-literal | N `literal()` | N `Literal`, `MultiLiteral` | N `Literal` (aliases not suggested, #5) | N `@Subcommand`, `@Values` | S `@Literal` |
| string word / quoted / greedy | N (Mojang `StringArgumentType`) | N `String`, `Text`, `GreedyString` | N (single/quoted/greedy/greedy-flag-yielding) | N (`DefaultTypeFactories`: `string()`/`greedyString()`) | S `@Quoted`, `@Join`, `@Varargs` |
| int/long/float/double (+ranges) | N (Mojang) | N + min/max ctor | N min/max | N via `@Range` | S `Number` resolvers, `@Validate` |
| bool | N | N `Boolean` | N strict/liberal | N | S |
| enum | N via `resource()`/custom | `CustomArgument`/`MultiLiteral` | S `Enum` (suggestions) | S enum factory → string node | S `Enum`, `OldEnum` |
| duration / time | N `time(minTime)` (ticks) | N `Time` | S `Duration` (no format docs) | – | S `Duration`, `Period`, `Instant`, `LocalDateTime` |
| UUID | N `uuid()` | N `UUID` | N `UUID` (mapped `uuid`) | N `UUID` if present | S `UUID` |
| key / namespaced key | N `key()`, `namespacedKey()` | N `NamespacedKey` | N `NamespacedKey` (`resource_location`) | – | S `NamespacedKey` |
| registry value / key | N `resource(RegistryKey)`, `resourceKey(RegistryKey)` | N `Advancement`, `Biome`, `Enchantment`, `EntityType`, `LootTable`, `Particle`, `PotionEffect`, `Recipe*`, `Sound`, `Function` | N `Enchantment` (`item_enchantment`), `RegistryEntryParser*`; S `Material` | – | – |
| block position / column | N `blockPosition()`, `columnBlockPosition()` | N `Location(BLOCK_POSITION)` | N `LocationParser` (`vec3`), `Location2D` (`vec2`) | N `Location` → `block_pos` | S `Location` |
| fine position | N `finePosition(center)`, `columnFinePosition()` | N `Location(PRECISE_POSITION)`, `Location2D` | (vec3 covers) | – | – |
| rotation / angle / axes | N `rotation()`, `angle()`, `axes()` | N `Rotation`, `Angle`, `Axis` | – | – | – |
| entity / player selectors | N `entity()`, `entities()`, `player()`, `players()` | N `EntitySelector.{OneEntity,ManyEntities,OnePlayer,ManyPlayers}` | N 4 selector parsers (`entity`); S `Player`, `OfflinePlayer` | N `Player`/`OfflinePlayer`/`Entity` → `entity(single,…)`, `EntitySelector<T>` + `@Single` | S `Player`, `OfflinePlayer` |
| player profile | N `playerProfiles()` | N `PlayerProfile*`, `AsyncPlayerProfile*` | – | – | – |
| item stack / predicate | N `itemStack()`, `itemPredicate()` | N `ItemStack`, `ItemStackPredicate` | N `ItemStack`, `ItemStackPredicate` | – | – |
| item type | N `resource(RegistryKey.ITEM)` | via registry args | S `Material` | – | – |
| block state / predicate | N `blockState()`, `blockInWorldPredicate()` | N `BlockState`, `BlockPredicate` | N `BlockPredicate` | – | – |
| Adventure component / style / message | N `component()`, `style()`, `signedMessage()` | N `ChatComponent*`, `Chat*` (signed), `ChatColor*` | `ComponentParser` (extras, S) | – | – (adventure ext is output only) |
| colour (named/hex) | N `namedColor()`, `hexColor()` | N `ChatColor*` | S `TextColorParser` (extras) | – | – |
| score holder / objective / criteria / team / slot | N `scoreboardDisplaySlot()`, `objectiveCriteria()` (+ vanilla types via `getDispatcher`) | N `ScoreHolder.{Single,Multiple}`, `Objective`, `ObjectiveCriteria`, `Team`, `ScoreboardSlot`, `MathOperation` | – | (reflection constants exist, unmapped) | – |
| world / dimension | N `world()` | N `World` | `KeyedWorldParser*` (`resource_location`), S `World` | N `World` → string | S `World` |
| game mode / heightmap / anchor / template | N `gameMode()`, `heightMap()`, `entityAnchor()`, `templateMirror()`, `templateRotation()` | – | – | – | – |
| NBT | – (use item/block) | N `NBTCompound` (needs NBT-API) | – | – | – |
| flags / switches | – (model as literals) | – (`CommandArgument` for nested) | N/S `CommandFlagParser` (presence/value, aliases, `-abc` chaining) | S `@Flag`, `@Switch` (permuted optional flags) | S `@Flag` |
| optional / default | via sibling `executes()` | `setOptional`, `ListArgument`, `MapArgument` | `optional()` + default value, `Either`, aggregate parsers | `@Optional`, `@Default`, `Optional<T>` | `@OptionalArg`, `Optional<T>` |
| custom type | `CustomArgumentType` (wraps native) | `CustomArgument` (wraps native) | `ArgumentParser` + `BrigadierMapping` | `ParameterType` + `ArgumentTypeFactory` | `ArgumentResolver` |

Reading: Paper already exposes 37 native types incl. registry-generic `resource()`; CommandAPI's breadth is Paper's breadth re-exported through per-version NMS; cloud maps ~12 types natively and string-parses the rest; Lamp maps 5; LiteCommands maps none. Any Sculk `ArgumentType` registry that targets `ArgumentTypes` directly beats all four on Paper. Velocity natively offers only Mojang primitives; backend-only types must be emulated or forwarded (cf. workstream D). **Corrected 2026-10-12:** an earlier version said "primitives + custom"; Velocity supports no custom argument types ("Custom plugin command argument types are not supported in Velocity", docs.papermc.io/velocity/dev/command-api). See ADR-0022.

### A.4 Execution pipeline comparison (deep dive 2)

| Concern | Paper Brigadier | CommandAPI | cloud v2 | Lamp v4 | LiteCommands |
| --- | --- | --- | --- | --- | --- |
| Permissions | `requires(src -> src.getSender().hasPermission(..))` per node; `restricted()` blocks click-event execution | `withPermission`, `withRequirement` per command/arg; `updateRequirements` resync | `Permission.anyOf/allOf`, `PredicatePermission`; evaluated by platform; `BrigadierPermissionChecker` | `@CommandPermission` per platform, `PermissionConditionChecker`, custom `CommandPermissionFactory` | `@Permission(s)`, LuckPerms ext |
| Sender constraints | `CommandSourceStack` sender vs executor; `instanceof` | `PlayerCommandExecutor` etc. executor overloads | `senderType(Class)`; `PaperSimpleSenderMapper` → `PlayerSource/ConsoleSource/EntitySource/GenericSource` | `SenderResolver`, `@NotSender`, actor types | `@Sender`, `@Context` providers |
| suspend / async | none; executor returns `int` synchronously | async suggestions (`CompletableFuture`), executors sync | `ExecutionCoordinator` simple/async/builder per step; `SuspendingExecutionHandler` = `scope.future(context){ … }` with **defaults `GlobalScope` + `EmptyCoroutineContext`**; docs: don't pair with `simpleCoordinator` | `supportSuspendFunctions()`: injects a `Continuation` whose `context = EmptyCoroutineContext` → resumes on whatever thread completes it; exceptions to `handleException`; no scope, no cancellation | `@Async` on method/arg/completion; `litecommands-folia` scheduler |
| Middleware | none | none | `CommandPreprocessor`/`Postprocessor` via `ServicePipeline`, interruptible; cloud-processors: confirmation, cooldown, requirements (MIT, no releases) | `Hooks`: `CommandExecutedHook`, `PostCommandExecutedHook`, registered/unregistered hooks, `CancelHandle`; `CommandCondition` | validators, `@Edit`, `@Bind` |
| Cooldowns / confirmation | – | – | cloud-processors | `@Cooldown` (`ThreadExecutorCooldownCondition`) | `@Cooldown` |
| Exceptions / responses | `CommandSyntaxException`; Paper prints vanilla red text | `CommandAPI.failWithString/Adventure` | `ExceptionController` (most-specific, LIFO, unwrapping); `MinecraftExceptionHandler` in extras | `ExceptionHandler`, `DefaultExceptionHandler`; `ResponseHandlers` for Supplier/CompletionStage/Optional | message system, `litecommands-adventure` |
| Localisation | none | none | `TranslatableCaption`, `RichDescription` (extras) | message strings in handler | message keys |
| Help | none (vanilla `/help` shows description) | `withHelp/withFullDescription`, generated `/help` entries | `HelpHandler` (index/multi/verbose) + `MinecraftHelp` renderer (paginated, themed) | `Help.paginate`, `ChildrenCommands`/`SiblingCommands`/`RelatedCommands` | generated usage/`@Description` |
| Test harness without server | none official; Brigadier `CommandDispatcher` is pure Java and unit-testable | `commandapi-paper-test-toolkit` (MockBukkit); only primitives/strings/literals/`IntegerRange` implemented, else `UnimplementedMethodException` | undocumented; `CommandManager` is abstract and testable with a no-op `CommandRegistrationHandler` (UNVERIFIED as supported path) | none documented | `litecommands-unit` test platform |
| Annotations | none | runtime reflection + experimental compile-time processor (`commandapi-annotations`, `annotations_spec_doc.md`) | runtime reflection (`cloud-annotations`); compile-time requested (#685, open) | runtime reflection, Kotlin reflection for defaults | runtime reflection |
| Folia awareness | region thread of sender | detects Folia (`isFoliaPresent`), skips `Bukkit.reloadData()`; runtime (un)register unsupported | none (inherits Paper thread) | none | `FoliaScheduler` for `@Async` |
| Velocity | separate API: `BrigadierCommand`/`SimpleCommand`/`RawCommand`, `CommandMeta`, `suggestAsync` | shade 190 KB; primitives only | `cloud-velocity` 17 KB | `velocity` 28 KB | `litecommands-velocity` |

### A.5 Recommendation for Sculk 6 — Commands

**Decision: native + abstract; leave CommandAPI, cloud, Lamp, LiteCommands upstream.** Sculk 5 already
has "`CommandSpec` as data, one Brigadier adapter". Keep that shape; add a Sculk argument registry
whose Paper backend returns `ArgumentTypes.*` directly (covering the whole A.3 Paper column, incl.
`resource(RegistryKey)`), a Velocity backend that maps to Mojang primitives + `SimpleCommand`
fallback, and a pure-JVM `CommandDispatcher` test backend. No library gives Kotlin-first specs,
Folia-aware suspend execution, and server-less tests at once, and every one of them costs
170–620 KB to get a subset of Paper's own types.

Copy (5):
1. **cloud's typed sender sources** (`PlayerSource`/`ConsoleSource`/`EntitySource`) over a raw `CommandSourceStack` — the sender/executor split is the #1 footgun and a sealed type fixes it at compile time.
2. **cloud's pre/post-processor pipeline** for confirmation/cooldown/requirements — keeps the core small and makes cross-cutting behaviour testable in isolation.
3. **Paper bootstrap registration** (`LifecycleEvents.COMMANDS` on `BootstrapContext`) as the default — commands become datapack-visible and reload-safe for free.
4. **CommandAPI's "test the registered tree" intent, but via Brigadier's own `CommandDispatcher`** — a spec-as-data design can execute in JUnit without MockBukkit, avoiding the toolkit's `UnimplementedMethodException` sprawl.
5. **Lamp/LiteCommands declarative validators** (`@Range`, `@Length`, `@Validate`) expressed as spec data — validation belongs to the argument, not the handler.

Avoid (5):
1. **Per-version NMS modules / reflective vanilla-type lookup** (CommandAPI `commandapi-paper-26.x`, Lamp `MinecraftArgumentType`) — Paper exposes every needed type publicly since 1.20.6; reflection only buys lock-step breakage.
2. **Registering through `PluginCommand`/`CommandMap`** (LiteCommands, Lamp `ByReflection`) — obsolete in 26.3, string-parsed, no client validation.
3. **Cast-based Kotlin DSL** (`args["x"] as T`, CommandAPI #544) — a reified `argument<T>()` handle is the whole point of Kotlin-only.
4. **Coroutine execution with ambient scope** (cloud `GlobalScope` default, Lamp `EmptyCoroutineContext` continuation) — handlers must run in the plugin's supervised scope on the sender's region dispatcher or they outlive disable and resume on random threads.
5. **Runtime reflection annotation scanning as the primary API** (all four) — it defeats `explicitApi`, `.api` dumps and ProGuard-style shrinking; keep annotations optional sugar over data.

---

## B. Coroutines

### B.1 MCCoroutine table

| Field | MCCoroutine (Shynixn) |
| --- | --- |
| Version / date | 2.23.0 — 2026-09-20 (fix #139 Paper 26.3 infinite loop in deprecated-event warning scan); 2.22.0 2025-04-18 |
| Licence | MIT (`LICENSE`, 2018-2024; gh shows NOASSERTION because of header wording) |
| Maintenance | 272★, single maintainer, release cadence ~1/yr, pushed 2026-09-20 |
| MC / Java / Kotlin | Bukkit 1.8+ (UNVERIFIED lower bound), Folia, Velocity, BungeeCord, Sponge, Minestom, Fabric; **Java 8 target**, Kotlin 1.9.25 |
| Modules / size | per platform `-api`/`-core`/`-sample`: bukkit-core 50 KB, bukkit-api 24 KB, folia-core 63 KB, folia-api 25 KB, velocity-core 38 KB |
| Dispatchers (Bukkit) | `MinecraftCoroutineDispatcher` (`isDispatchNeeded = !isPrimaryThread && plugin.isEnabled`; `scheduler.runTask`, optional `CoroutineTimings` queue), `AsyncCoroutineDispatcher` (always dispatch, `runTaskAsynchronously`) |
| Dispatchers (Folia) | `GlobalRegionDispatcher` → `globalRegionScheduler.execute`; `RegionDispatcher(world,chunkX,chunkZ)` → `regionScheduler.execute`; `EntityDispatcher(entity)` → `entity.scheduler.run(plugin, task, retired=task)`; `AsyncFoliaCoroutineDispatcher` → `asyncScheduler.runNow`; `MainDispatcher` = plugin-private single thread `MCCoroutine-<plugin>-MainThread` draining a queue every `mainDispatcherTickRateMs`. All Folia dispatchers return `isDispatchNeeded = true` (every resume hops a tick). `getRegionDispatcher`/`getEntityDispatcher` allocate a new dispatcher per call; on non-Folia they return the global dispatcher |
| Dispatcher (Velocity) | `VelocityCoroutineDispatcher`: always dispatch via `scheduler.buildTask(plugin, block).schedule()` (Velocity pool) |
| Scope | `CoroutineScope(CoroutineExceptionHandler) + SupervisorJob() + minecraftDispatcher/globalRegionDispatcher`; uncaught → `MCCoroutineExceptionEvent` (cancellable) else `SEVERE` log |
| Plugin disable | `MCCoroutineImpl` disposes session: `cancelChildren()`, `scope.cancel()`, `MainDispatcher.close()`, wake-up service dispose; `launch` after disable throws `RuntimeException("…attempted to start a new coroutine session while being disabled…")`; `shutdownStrategy` config. `MinecraftCoroutineDispatcher.dispatch` and `MainDispatcher.dispatch` **silently return** when `!plugin.isEnabled` |
| Suspending listeners | `registerSuspendingEvents(listener)` reflects `SimplePluginManager.getEventListeners` and `HandlerList.registerAll(SuspendingRegisteredListener)`; Folia overload takes `Map<Class<out Event>, (Event) -> CoroutineContext>` |
| Suspending commands | `setSuspendingExecutor` wraps `PluginCommand.setExecutor` → `plugin.launch(context){ success = … }` and returns the captured `success`/tab list — if the body suspends, the pre-suspension value (true / null) is returned. Legacy `PluginCommand` only, no Brigadier |
| Extras | `fireSuspendingEvent(event, Concurrent|Consecutive)`; `callSuspendingEvent` honours `ignoreCancelled`; optional "manipulated server heartbeat" hack (reflective `CraftScheduler.mainThreadHeartbeat`) to wake a sleeping main thread during startup — off by default |
| Known issues | #138 Folia session map keyed by `Plugin`, reloaded plugin can reuse a dead session; #137 exception event lacks `CoroutineContext`; #139 (fixed) reflective deprecated-event scan looped forever on Paper 26.3 |

Sources: clone tag 2.23.0 — `mccoroutine-folia-core/.../dispatcher/*.kt`, `impl/CoroutineSessionImpl.kt`, `service/EventServiceImpl.kt`; `mccoroutine-bukkit-core/.../dispatcher/*.kt`, `service/{Event,Command}ServiceImpl.kt`, `service/WakeUpBlockServiceImpl.kt`, `impl/MCCoroutineImpl.kt`; `mccoroutine-velocity-core/.../dispatcher/VelocityCoroutineDispatcher.kt`, `service/EventServiceImpl.kt`; shynixn.github.io/MCCoroutine/wiki/site/{faq,listener}.

### B.2 Internals (deep dive 3)

**Do suspend handlers decide cancellation before return?** Yes — exactly as far as the first
suspension point, and not one line further. `EventServiceImpl.executeEvent` launches with
`plugin.launch(dispatcher, CoroutineStart.UNDISPATCHED)` (comment: "We want to start it on the same
thread as the calling thread -> unDispatched"), so the body runs synchronously inside
`PluginManager.callEvent` until it suspends; `event.isCancelled = true` before that point behaves
like a plain listener. After the first `withContext`/`delay`, `callEvent` has already returned and
the server has acted; the FAQ states this ("cannot cancel an event once its coroutine has suspended
for the first time"). The post-suspension dispatcher is `asyncDispatcher` if
`event.isAsynchronous` else `minecraftDispatcher` (Bukkit), or the registration map's dispatcher
with `globalRegionDispatcher` fallback (Folia). Suspend detection is lazy: first invocation tries
`method.invokeSuspend`, catches `IllegalArgumentException`, caches `isSuspendMethod`.
Consequence for Sculk: a `suspend` listener API on `Cancellable` events is a trap unless the API
makes the two phases explicit.

**Folia correctness.** Region/entity dispatchers delegate to the right Paper scheduler and
`EntityDispatcher` passes the block as the *retired* callback too, so a coroutine resumes even when
the entity was removed (surprising; Sculk should fail the continuation instead). `MainDispatcher`
creates a per-plugin OS thread that is neither a region nor the global thread — any Bukkit call from
it is a Folia thread-check violation; it exists only to serialise plugin-private state.

**Plugin disable.** Clean: supervisor cancel + explicit guard that throws on post-disable launches.
Unclean: dispatchers drop work silently when the plugin is disabled instead of failing the
continuation with `CancellationException`, so `withContext(minecraftDispatcher)` during shutdown
hangs the caller forever unless the scope cancel reaches it first.

**Velocity.** Suspend handlers are made to work by reflecting into
`VelocityEventManager$MethodHandlerInfo.continuationType` and `AsyncType.ALWAYS`, then wrapping in
`EventTask.withContinuation`. This is proxy-internal API; workstream D should use Velocity's public
`EventTask.withContinuation`/`resumeWhenComplete` directly.

### B.3 Kotlin coroutine best-practice references for a tick dispatcher

| Rule | Source |
| --- | --- |
| `isDispatchNeeded` default is `true`; returning `false` resumes inline "potentially forming an event-loop"; override only as "a performance optimization", e.g. `MainCoroutineDispatcher.immediate` checks "whether we are already in the required UI thread"; must be exception-safe | kotlinlang.org/api/kotlinx.coroutines/…/-coroutine-dispatcher/is-dispatch-needed.html |
| `dispatch` must never run the block inline — "may result in StackOverflowError when dispatch is invoked repeatedly, for example when yield is called in a loop"; use `isDispatchNeeded = false` for the inline case | …/-coroutine-dispatcher/dispatch.html; kotlinlang Slack threads (slack-chats.kotlinlang.org/t/490303, /t/491071) |
| `Delay` is `@InternalCoroutinesApi` — "This an internal API and should not be used from general code"; `delay()` resolves `context[ContinuationInterceptor] as? Delay ?: DefaultDelay`; maintainers advise against implementing it (binary compatibility) | kotlinx.coroutines `common/src/Delay.kt`; slack-chats.kotlinlang.org/t/493886 |
| Therefore tick-aligned waiting needs an explicit `suspend fun delayTicks(n)` built on `suspendCancellableCoroutine` + scheduler `runDelayed` with `invokeOnCancellation { task.cancel() }`; `delay(ms)` keeps wall-clock semantics via `DefaultDelay` and then re-dispatches to the tick thread | inference from the two rows above |
| Use `CoroutineStart.UNDISPATCHED` when the pre-suspension part must run on the caller's thread (MCCoroutine's listener trick); use the default start when ordering consistency matters | is-dispatch-needed.html ("consistent dispatching behaviour") |

### B.4 Recommendation for Sculk 6 — Coroutines

**Decision: native. Leave MCCoroutine upstream.** Sculk already owns `SculkScheduler` with
abstract region overloads and a per-plugin scope; a dispatcher family is ~150 lines over it.
MCCoroutine's Java 8 target, reflective hooks (`SimplePluginManager`, `CraftScheduler`,
`VelocityEventManager`) and obsolete `PluginCommand` command path make it a liability on 26.x.

Copy (5):
1. **`UNDISPATCHED` start for suspend event handlers** — the only way the "before first suspension" phase can still cancel, and it costs nothing.
2. **Per-plugin `SupervisorJob` + `CoroutineExceptionHandler` + cancel-on-disable + throw on launch-after-disable** — gives structured concurrency with a clear failure message instead of zombie tasks.
3. **The Folia dispatcher family** (global / region(world,cx,cz) / entity / async) keyed to the server's schedulers — it maps 1:1 to Folia's thread-ownership model.
4. **`isDispatchNeeded = !isOnTargetThread` for the main/global dispatcher** — avoids a one-tick hop on every resume when already home (MCCoroutine does this on Bukkit, forgets it on Folia).
5. **`EventExecutionType.Consecutive` for plugin-owned suspend events** — lets Sculk-defined events await handlers in priority order, which Bukkit cannot express.

Avoid (5):
1. **Silently dropping dispatches when disabled** — fail the continuation with `CancellationException` so `withContext` cannot hang during shutdown.
2. **A plugin-private "main thread" dispatcher** — it is a Folia thread-check violation waiting to happen; serialise plugin state with a `Mutex`/actor on region threads instead.
3. **Implementing `Delay`** — internal API; ship `delayTicks()` and let `delay()` stay wall-clock.
4. **Reflection into `SimplePluginManager.getEventListeners`** — `PluginManager.registerEvent(Class, Listener, EventPriority, EventExecutor, Plugin, ignoreCancelled)` is public and sufficient (and Paper's `#139`-style deprecation scan is then Paper's job).
5. **Unqualified `suspend` listeners for `Cancellable` events** — expose `on<E> { }` (sync) and `onSuspend<E>` only for non-cancellable events, or a two-phase `decide { } then { }` shape, so the semantics in B.2 are in the type, not the FAQ.

---

## C. Compatibility libraries

### C.1 Project table

| | XSeries | MorePaperLib | FoliaLib |
| --- | --- | --- | --- |
| Version / date | v14.0.0 — 2026-10-09 ("Updated for v26.3.0"); 13.7.1 2026-06-24 | 0.5.3 — 2026-07-14 (repo metadata; no GitHub releases) | 0.5.1 — 2025-06-19 (0.5.2 in `build.gradle.kts`, unreleased; pushed 2026-05-03) |
| Licence | MIT | **LGPL-3.0-or-later** (flag: shading into a proprietary jar triggers §4 relinking/notice obligations; dynamic linking as a separate plugin is the safe mode) | MIT |
| Maintenance | 533★, very active, PR #421 for 26.3 tags | 42★, single maintainer, low cadence | 136★, sparse |
| MC / Java | 1.8 → 26.3 ("at least 1.13 for cross-version utilities"); Java 8 source, toolchain 21 for some modules | Spigot/Paper 1.8.8+, "latest Folia only"; Java 8 | 1.8.8+ ("strongly advises ≥1.13.2"); Java 8 |
| Paper / Folia / Velocity | Bukkit-centric; `paper-util` Adventure factory; no Folia scheduling; Velocity only in XSkull usercache fix | Spigot/Paper/Folia; no Velocity | Folia/Paper/Spigot (+legacy variants, `UNKNOWN`); no Velocity |
| Kotlin | none (Java statics/enums) | none | none |
| Repo / size | Maven Central `com.github.cryptomorin:XSeries` **855 KB** (13.7.1: 834 KB); compile dep spigot-api only | `mvn-repo.arim.space/lesser-gpl3` (not Central); 29 KB | `repo.tcoded.com/releases` (not Central); 41 KB; relocation enforced at runtime |
| Abstracts | registry enums/classes with legacy names (`XMaterial` 1,553 entries / 2,825 lines; `XSound` is now an `XModule` class since `Sound` became an interface in 1.21.3; `XTag` 3,204 lines; `XItemStack` YAML (de)serialisation 2,344 lines; `XEnchantment`, `XEntityType`, `XPotion`, `XBiome`, `XAttribute`, `XBlock`, `XGameRule`, `XItemFlag`, `XPatternType`, `XWorldBorder`, `NoteBlockMusic`); particles (`XParticle`, `ParticleDisplay` 2,229, `Particles` 2,978 lines of shape maths); reflection (`XReflection` + ~80 classes: handles, constraints, ASM `XReflectASM` proxies, `MinecraftMapping`/`MinecraftPackage`, `MinecraftCapability` for `NMSExtras`); profiles (`XSkull` + Mojang API client, cache, rate limiter, ~35 classes); legacy `ActionBar`/`Titles`/`MessageComponents` | `GracefulScheduling`: `AsynchronousScheduler`, `RegionalScheduler` (`globalRegionalScheduler()`, `regionSpecificScheduler(loc | world,cx,cz)`), `AttachedScheduler` (`entitySpecificScheduler(entity)`), `isUsingFolia()`, `cancelGlobalTasks()`; `CommandRegistration.getServerCommandMap()` (Paper API or Spigot reflection); `MorePaperLibAdventure` (Component → legacy on Spigot) | `PlatformScheduler`: `runAtEntity/runAtLocation/runNextTick/runAsync/runTimer` (+`Later` variants), `WrappedTask`, `teleportAsync`, `EntityTaskResult`; `ImplementationType` detection: `io.papermc.paper.threadedregions.RegionizedServer` → FOLIA, `PaperConfig`/`Configuration` + `isTaskConsumersSupported` → PAPER/LEGACY_PAPER, `SpigotConfig` → SPIGOT/LEGACY_SPIGOT; impl loaded via `Class.forName` |
| Why people use it | the only maintained cross-version material/sound/particle table; skull/profile handling; particle shapes | tiny, Java 8, one API for Bukkit+Folia schedulers | tiny, Folia-first, `teleportAsync` fallback, relocation guard |
| Limitations / complaints | 855 KB must be shaded; enum static-init cost; modded hybrids break enum assumptions (#239); no unit tests (#149); `XItemStack` edit API (#337) | LGPL; cannot cancel per-entity/region tasks on Folia (no API); not on Central | no Canvas/Luminol detection (Folia forks pass the `RegionizedServer` check — UNVERIFIED); API "may undergo frequent breaking changes"; standalone-plugin request (#43) |

Sources: clones (XSeries master @ v14.0.0, MorePaperLib master, FoliaLib main); github releases; mvn-repo.arim.space / repo.tcoded.com `maven-metadata.xml`; XSeries README; FoliaLib `common/src/main/java/com/tcoded/folialib/FoliaLib.java`, `util/ImplementationTestsUtil.java`.

### C.2 XSeries on a 1.21.11+-only framework (deep dive 4)

| Part | Still relevant? | Why |
| --- | --- | --- |
| `XMaterial`/`XSound`/`XEntityType`/`XBiome`/`XEnchantment`/`XPotion`/`XTag` | **No** | Their value is the legacy-name arrays (`GRASS_BLOCK("GRASS")`, `OAK_LOG("LOG")`) and `supports(major, minor)` gating back to 1.8. On 1.21.11+ every one of these is a `Registry`/`Keyed` lookup; `sculk-series` already does it with aliases. The enums are heavy because each of 1,553 constants carries legacy names, data values and `@XInfo` version metadata, all resolved in `<clinit>`; and `Sound`/`Pattern`/`Art` turning into interfaces forced the `XModule`/`XRegistry` class rewrite — exactly the churn a registry-based design avoids. |
| `XItemStack` | Partly | YAML item (de)serialisation incl. components is useful *as a reference* for `sculk-items` descriptors; the code targets `ItemMeta`, not data components, so it is not portable as-is. |
| `ParticleDisplay` + `Particles` shapes | **Yes (port the maths)** | 50+ parametric shapes (`circle`, `sphere`, `helix`, `dna`, `cube`, `tesseract`, `mandelbrot`, `lightning`, `heart`, `vortex`, `renderImage`, `rotateAround`…) — pure vector maths, MIT. Belongs in `sculk-visual` timelines as pure functions emitting positions; drop the Bukkit `Particle` plumbing. |
| `XReflection` family | **No** | Sculk is API-only; packets go through `sculk-packets-api`. Nothing in a 1.21.11+ framework needs NMS handles, ASM proxies or `MinecraftMapping`. If a single NMS touch ever becomes unavoidable, a `MethodHandle` cache is 30 lines. |
| `XSkull`/profiles | No | Paper `PlayerProfile`/`ProfileProperty` + `Bukkit.createProfile` cover it; XSeries' Mojang client exists for Spigot. |
| `XWorldBorder` | No | Packet-level per-player border — `sculk-packets-api` territory. |
| `ActionBar`/`Titles`/`MessageComponents` | No | Pre-Adventure. |

### C.3 Does anything generate cross-version compat data from API jars?

No library found that diffs `paper-api`/`spigot-api` jars to produce `Material`/`Sound`/`Particle`
rename tables; XSeries' tables are hand-maintained (commit history: "Fix CARVED_PUMPKIN material on
versions below 1.13"). Adjacent sources: misode/mcmeta (version-controlled `registries.json`,
`sounds.json` produced by the vanilla data generator `--reports`), EJD799/Minecraft-Mappings
(Fabric/Forge class mappings with `-generator` modules), Spigot's in-server `CraftLegacy` /
`UnsafeValues.fromLegacy` (1.12→1.13 only), PowerLib PR #32 (javap by hand to detect `Sound`
enum→interface). Workstream B already scans `paper-api` jars with `javap` for class presence; a
Gradle task diffing enum constants across jars with ASM would be a day of work — but YAGNI for a
framework whose floor is 1.21.11 and whose API is `Registry`/`Keyed`.

### C.4 Recommendation for Sculk 6 — Compatibility libraries

**Decision: leave all three; port the ParticleDisplay shape maths (MIT) into `sculk-visual`;
build no compat-data generator.** `SculkScheduler` already is MorePaperLib/FoliaLib for Kotlin,
and LGPL on MorePaperLib is a sellability risk for premium plugins that shade.

Copy (5):
1. **FoliaLib's capability probing by class/method presence** (`RegionizedServer`, `isTaskConsumersSupported`) rather than version-string parsing — forks and pre-releases then classify correctly without a table.
2. **FoliaLib's runtime relocation guard** — a shaded framework that warns when not relocated prevents the classic two-plugins-two-versions crash.
3. **MorePaperLib's three-scheduler split** (async / regional / attached-to-entity) as the API vocabulary — it is the minimal set that is both Folia-correct and Bukkit-expressible.
4. **XSeries `ParticleDisplay` as a serialisable particle description** — config-driven effects need a data form; keep the idea, rewrite on kotlinx-serialization.
5. **XSeries' `@XInfo(since = "26.1")` per-constant metadata** as the model for the capability engine's "first version" data — but generated from jars (workstream B), not typed by hand.

Avoid (5):
1. **Enum-backed registries** — `Sound` becoming an interface in 1.21.3 forced XSeries to rewrite `XSound`; `Registry<T>`/`TypedKey` never has that problem.
2. **Legacy-name arrays and data values** — 1.8–1.12 weight that a 1.21.11+ framework pays in jar size and class-init time for nothing.
3. **Shading 800 KB for a lookup table** — Sculk's modules are sized for `sculk-series` to stay tens of KB.
4. **Per-version NMS reflection frameworks** (`XReflection`, ASM proxies) — API-only is a non-negotiable rule and the packet layer already owns the one exception.
5. **LGPL dependencies in shaded form** — MorePaperLib's licence is fine for an open plugin but conflicts with the premium-product constraint; the 29 KB it saves is not worth a licence review.

---

## Sources (primary)

- Paper: https://docs.papermc.io/paper/dev/command-api/basics/registration · …/basics/requirements · …/basics/executors · …/arguments/minecraft · …/arguments/registry · …/arguments/paper · …/misc/basic-command · https://jd.papermc.io/paper/26.3/io/papermc/paper/command/brigadier/Commands.html · https://jd.papermc.io/paper/26.3/org/bukkit/command/CommandExecutor.html · …/PluginCommand.html · …/plugin/java/JavaPlugin.html · https://github.com/PaperMC/Folia (README) · https://docs.papermc.io/velocity/dev/command-api/
- CommandAPI: https://github.com/CommandAPI/CommandAPI (tag 12.1.0; `PaperCommandRegistration.java`, `CommandAPIPaper.java`, `commandapi-bukkit-core/.../arguments`, `commandapi-paper-core/.../arguments`) · https://docs.commandapi.dev/velocity/intro · /test/intro · /kotlin-dsl/intro · issues #544, #686, #687 · https://hangar.papermc.io/CommandAPI/CommandAPI/versions
- cloud: https://github.com/Incendo/cloud (2.1.0; `cloud-core/.../parser/standard`, `cloud-kotlin/*`) · https://github.com/Incendo/cloud-minecraft (2.0.1; `ModernPaperBrigadier.java`, `PaperBrigadierMappings.java`, `BukkitBrigadierMapper.java`) · https://cloud.incendo.org/core/ · /minecraft/paper/ · /minecraft/bukkit/ · /kotlin/coroutines/ · issues cloud#685, cloud-minecraft#5, #110 · https://github.com/Incendo/cloud-processors
- Lamp: https://github.com/Revxrsal/Lamp (branch v4 @ rc.19; `bukkit/.../brigadier/*.java`, `brigadier/.../types/DefaultTypeFactories.java`, `common/.../ktx/SuspendFunctionsSupport.kt`, `common/.../annotation`, `common/.../help/Help.java`) · issues #134, #157, #163
- LiteCommands: https://github.com/Rollczi/LiteCommands (3.11.0; `litecommands-bukkit/src/.../BukkitCommandsRegistryImpl.java`, `litecommands-core/src/.../argument`) · https://docs.rollczi.dev/
- MCCoroutine: https://github.com/Shynixn/MCCoroutine (tag 2.23.0, files listed in B.1) · https://shynixn.github.io/MCCoroutine/wiki/site/faq/ · …/listener/ · issues #137, #138, #139
- kotlinx.coroutines: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/is-dispatch-needed.html · …/dispatch.html · https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/Delay.kt
- XSeries: https://github.com/CryptoMorin/XSeries (v14.0.0; `core/.../XMaterial.java`, `particles/*`, `reflection/*`) · release notes v14.0.0 · issues #149, #239, #337 · PR #421
- MorePaperLib: https://github.com/A248/MorePaperLib · https://mvn-repo.arim.space/lesser-gpl3/space/arim/morepaperlib/morepaperlib/maven-metadata.xml
- FoliaLib: https://github.com/TechnicallyCoded/FoliaLib · https://repo.tcoded.com/releases/com/tcoded/FoliaLib/maven-metadata.xml · issues #11, #43
- Compat data: https://github.com/misode/mcmeta · https://github.com/EJD799/Minecraft-Mappings · https://github.com/albemiglio/PowerLib/pull/32
- Jar sizes: `curl -sI` Content-Length on repo1.maven.org / repo.panda-lang.org / repo.tcoded.com / mvn-repo.arim.space, 2026-10-12.
