# D — Velocity 4.x API map for `sculk-velocity`

Research workstream D for Sculk Studio 6. Written 2026-10-12. Scope: enough precision to design
`sculk-velocity` as a first-class runtime (lifecycle, coroutines, events, commands, scheduler,
Adventure, permissions, servers, connection state, plugin messaging, configuration phase, typed
transport to Paper).

Source tags used below:

| Tag | Meaning |
| --- | --- |
| `[src:<path>]` | Read from a shallow clone of `PaperMC/Velocity`, branch `dev/4.0.0`, HEAD `5e641c6` (2026-10-07), `version=4.2.1-SNAPSHOT`. Paths relative to repo root; `api/…` = `api/src/main/java/com/velocitypowered/api/…`, `proxy/…` = `proxy/src/main/java/com/velocitypowered/proxy/…` |
| `[fill]` | `https://fill.papermc.io/v3/projects/velocity` (+ `/versions/<v>/builds`) |
| `[docs:<page>]` | `https://docs.papermc.io/velocity/dev/<page>/` (or `/velocity/getting-started/`) |
| `[gh:…]` | GitHub API via `gh` (compare, contents, issues) |
| `[maven]` | `https://repo.papermc.io/repository/maven-public/com/velocitypowered/velocity-api/maven-metadata.xml` |
| `[paper:…]` | `PaperMC/Paper` source or `docs.papermc.io/paper/…` |
| `[mcc:…]` | `Shynixn/MCCoroutine` sources (release 2.23.0, 2026-09-20) |
| UNVERIFIED | Not confirmed against a primary source |

---

## 1. Versions

**The directive's "Velocity 4.2" is correct.** 4.0.0 was the first 4.x release but the line has
moved on; 4.2.0 is current stable and 4.2.1-SNAPSHOT is in flight.

| Version | Fill build | Date | Channel | Commit | Note |
| --- | --- | --- | --- | --- | --- |
| 3.5.1 | 615 | 2026-07-11 | RECOMMENDED | `4498f1e` "Release 3.5.1" | last 3.x release; Java 21; Adventure 4.26.1 |
| 3.6.0-SNAPSHOT | — | — | STABLE | branch `dev/3.0.0` | Adventure 5.2.0 already; never released |
| **4.0.0** | 6 | 2026-07-14 | STABLE | `90f8905` "Release 4.0.0" | Java 25; Adventure 5.2.0 |
| 4.1.0 | 22 | 2026-08-24 | STABLE | `a078053` | |
| 4.1.1 | 24 | 2026-08-26 | STABLE | `db0a17e` | |
| 4.1.2-SNAPSHOT | 25–29 | 2026-09-01 → 09-14 | STABLE | — | never released as 4.1.2 |
| **4.2.0** | 30 | 2026-09-14 | STABLE | `c10b492` "Release 4.2.0" | **current stable**; adds MC 26.3 (protocol 777) |
| 4.2.1-SNAPSHOT | ongoing | 2026-10-06+ | STABLE | `dev/4.0.0` HEAD | docs' example dependency version |

Sources: `[fill]` version groups `4.0.0: 4.2.1-SNAPSHOT, 4.2.0, 4.1.2-SNAPSHOT, 4.1.1, 4.1.1-SNAPSHOT, 4.1.0, 4.1.0-SNAPSHOT, 4.0.0, 4.0.0-SNAPSHOT`; `[gh:PaperMC/Velocity#1884]` (open, 2026-09-15) tabulates the same and notes "there are no GitHub releases or tags" — confirmed: `gh api repos/PaperMC/Velocity/tags` and `/releases` both return `[]`. Fill reports every 4.x build, SNAPSHOT or not, as `channel: STABLE`; the release/snapshot policy is undocumented (that is what #1884 asks for).

- **Java**: "Velocity requires at least Java 25." `[docs:getting-started]`; "Velocity 4.0.x and above now requires Java 25 and above" `[docs:porting-plugins-from-velocity-1]`; `JavaLanguageVersion.of(25)` in `build.gradle.kts` at the 4.0.0 commit (commit "Toolchain and gradle bump (Java 25+)") `[gh:compare 4498f1e...90f8905]`; 3.x toolchain was 21 `[src:build.gradle.kts@dev/3.0.0]`. Javadoc is generated with `o.source = "25"` `[src:api/build.gradle.kts]`.
- **Maven**: `com.velocitypowered:velocity-api:<v>` at `https://repo.papermc.io/repository/maven-public/`. Released artifacts exist for 4.0.0, 4.1.0, 4.1.1, **4.2.0** (`velocity-api-4.2.0.pom` → HTTP 200) plus `4.2.1-SNAPSHOT` `[maven]`. Docs show `compileOnly("com.velocitypowered:velocity-api:4.2.1-SNAPSHOT")` `[docs:creating-your-first-plugin]`; pin **4.2.0** instead. Javadoc: `https://jd.papermc.io/velocity/4.2.0/` (200).
- **Annotation processor** is the *same* artifact (`annotationProcessor("com.velocitypowered:velocity-api:…")`); class `com.velocitypowered.api.plugin.ap.PluginAnnotationProcessor`, `@AutoService(Processor.class)`, registered as Gradle isolating incremental processor `[src:api/src/ap/…]`.
- **Transitive API deps** (`api(...)` in `[src:api/build.gradle.kts]`, versions `[src:gradle/libs.versions.toml]`): jspecify 1.0.1, gson 2.14.0, guava 33.7.1-jre, toml4j 0.7.2 (deprecated, "removed in Polymer"), Adventure BOM **5.2.0** (`adventure-api`, `-text-serializer-gson`, `-legacy`, `-plain`, `-text-minimessage`, `-text-logger-slf4j`, `-text-serializer-ansi`), snakeyaml 2.7, slf4j 2.0.19, **Guice 7.0.0**, checker-qual 4.2.3, `com.velocitypowered:velocity-brigadier:1.0.0-SNAPSHOT`, Configurate 4.2.0 (hocon/yaml/gson), Caffeine 3.2.4. Runtime: Netty 4.2.19.Final.
- **Protocol range**: `ProtocolVersion.MINIMUM_VERSION = MINECRAFT_1_7_2`, maximum `MINECRAFT_26_3(777)`; `MINECRAFT_1_21_11(774)`, `26_1(775)`, `26_2(776)` `[src:api/network/ProtocolVersion.java]`.

### 3.x → 4.x breaking changes that matter for a framework

`gh api compare 4498f1e...90f8905` (3.5.1 → 4.0.0) touches **one** file under `api/`: `api/build.gradle.kts`. The Java API surface is byte-identical. The break is entirely environmental:

| Change | Impact |
| --- | --- |
| Runtime Java 21 → **25** | plugin jars may target any ≤25 bytecode; our toolchain must *read* JDK-25 class files from `velocity-api` (Kotlin ≥ 2.3, see §10) |
| Adventure 4.26.1 → **5.2.0** (commits "Adventure 5 (#1774)", "Reapply …") | Adventure 5 min Java 21; removed `BuildableComponent`, `AbstractComponent`, `TranslationRegistry`, `UTF8ResourceBundleControl`, `MessageType`, `PlainComponentSerializer` (→ `PlainTextComponentSerializer`), `JSONComponentConstants` (→ `ComponentTreeConstants`), `adventure-extra-kotlin`, `adventure-text-serializer-gson-legacy-impl`; `ClickEvent` is a typed interface (`https://docs.papermc.io/adventure/migration/adventure-4.x/`). 5.1.0 added stricter `Key` namespace validation (`..` disallowed) (`KyoriPowered/adventure` releases page) |
| `adventure-platform-facet` dropped (#1843) | internal only |
| Netty 4.2.16 (now 4.2.19) | internal |

Within 4.x (`compare 90f8905...c10b492`, 4.0.0 → 4.2.0), additive only: `@Plugin.provides()` + `velocity-plugin.json` `"provides"`, `PluginDescription.getProvidedIds()/getDependency(id)`, `ServerConnection.isClientLoaded()` (`@Beta`), `PlayerClientLoadedWorldEvent` (`@Beta`), `ProtocolVersion.MINECRAFT_26_3`, javadoc on configuration events. Nothing removed. `Subscribe.order`, `EventManager.register(…, PostOrder, …)`, `CommandManager.register(alias, …)`, `Player.sendResourcePack(…)`, `clearHeaderAndFooter()` remain `@Deprecated` but present `[src:api/…]`.

---

## 2. Plugin lifecycle

### Metadata

`@Plugin` `[src:api/plugin/Plugin.java]`: `id` (pattern `[a-z][a-z0-9-_]{0,63}`), `name`, `version`, `description`, `url`, `authors: String[]`, `dependencies: Dependency[]` (`@Dependency(id, optional = false)`), `provides: String[]`. The processor writes `velocity-plugin.json` to `CLASS_OUTPUT`; a second `@Plugin` class in the same compilation yields a warning and is ignored `[src:api/src/ap/…/PluginAnnotationProcessor.java]`.

`velocity-plugin.json` `[docs:velocity-plugin-json]`, `[src:api/src/ap/…/SerializedPluginDescription.java]`: required `id`, `main` (FQCN); optional `name`, `version`, `description`, `url`, `authors[]`, `dependencies[{id, optional}]`, `provides[]`. Dependency *versions* are not implemented (`toDependencyMeta` passes `null`, "TODO Implement version matching") `[src:proxy/plugin/loader/java/JavaPluginLoader.java]`.

**Annotation processing is optional.** The loader only reads the JSON. A jar containing `paper-plugin.yml`/`plugin.yml`/`bungee.yml` and no `velocity-plugin.json` is rejected with "appears to be a Paper, Bukkit or BungeeCord plugin" `[src:JavaPluginLoader.getSerializedPluginInfo]`. For Kotlin, `annotationProcessor` configurations do not see Kotlin sources (PluginTemplateKt PR #72 switched to kapt for exactly this). **Recommendation: have the Sculk Gradle convention plugin emit `velocity-plugin.json` from the Gradle model; no kapt, no `@Plugin` annotation required.**

### Load sequence `[src:proxy/plugin/VelocityPluginManager.loadPlugins]`, `[src:proxy/VelocityServer.java]`

1. Scan `plugins/*.jar` → `loadCandidate`; a candidate claims `id` + every `provides` id; a clash with an earlier candidate is refused.
2. `PluginDependencyUtils.sortCandidates` (topological); a plugin whose non-optional dependency is not loaded is skipped with "missing dependency".
3. Per plugin: `new PluginClassLoader(jarUrl)` (parent = Velocity's loader) → `addToClassloaders()` → `loadClass(main)` → `VelocityPluginModule`.
4. `Guice.createInjector(pluginModule, commonModule)` → `injector.getInstance(mainClass)` → `container.setInstance` → `registerPlugin`. Each plugin gets its **own injector**; it is not exposed.
5. `VelocityServer.loadPlugins()` then calls `eventManager.registerInternally(container, instance)` for every plugin — the main class is auto-registered as a listener.
6. `eventManager.fire(new ProxyInitializeEvent()).join()` (line 314), then listeners bind (`ListenerBoundEvent` per listener).

Guice bindings available to a plugin constructor:

| Binding | Source |
| --- | --- |
| main class, `Scopes.SINGLETON` | `VelocityPluginModule` |
| `org.slf4j.Logger` (name = plugin id) | `VelocityPluginModule` |
| `net.kyori.adventure.text.logger.slf4j.ComponentLogger` | `VelocityPluginModule` |
| `@DataDirectory java.nio.file.Path` → `plugins/<id>` | `VelocityPluginModule` |
| `PluginDescription`, `PluginContainer` (own) | `VelocityPluginModule` |
| `java.util.concurrent.ExecutorService` (provider → `container.getExecutorService()`) | `VelocityPluginModule` |
| `ProxyServer`, `PluginManager`, `EventManager`, `CommandManager` | common module |
| `@Named("<id>") PluginContainer` for **every** loaded plugin | common module |
| not bound: `Scheduler`, `ChannelRegistrar`, `ProxyConfig` | reach via `ProxyServer` |

`[src:proxy/plugin/loader/java/VelocityPluginModule.java]`, `[src:VelocityPluginManager.loadPlugins]`. Guice 7 is **jakarta-only**: `jakarta.inject.Inject` or `com.google.inject.Inject`; `javax.inject` is not recognised (`google/guice/wiki/Guice700`). Docs use `com.google.inject.Inject` `[docs:api-basics]`.

### Lifecycle events and shutdown order

| Phase | Event | Awaited? | Notes |
| --- | --- | --- | --- |
| construction | — | — | "very little you can do safely"; `register`/`buildTask` throw `IllegalArgumentException("plugin is not registered")` because `fromInstance` has no container yet `[docs:pitfalls]`, `[src:proxy/scheduler/VelocityScheduler.buildTask]` |
| init | `event.proxy.ProxyInitializeEvent` | yes (`.join()`) | "after plugins have been loaded but before the proxy starts accepting connections" |
| bind | `ListenerBoundEvent(address, ListenerType)` | no | per MINECRAFT/QUERY listener |
| reload | `ProxyReloadEvent` | no (`fireAndForget`) | `/velocity reload`; `ServerRegisteredEvent`/`ServerUnregisteredEvent` (records) fire for replaced servers |
| shutdown 1 | `cm.shutdown()` → `ListenerCloseEvent` | — | stops accepting |
| shutdown 2 | `ProxyPreShutdownEvent` (`@Beta`) | yes, ≤ `velocity.pre-shutdown-timeout` s (default 10) | last point players are reachable (transfer them here) |
| shutdown 3 | `player.disconnect(reason)` ∀ players; wait ≤10 s for teardown futures | — | every `DisconnectEvent` completes here |
| shutdown 4 | `ProxyShutdownEvent` | yes (`.join()`) | **plugin executors are still alive here** |
| shutdown 5 | `scheduler.shutdown()` | — | cancels all tasks, shuts timer and every plugin `ExecutorService`, `awaitTermination(10 s)` each |

`[src:proxy/VelocityServer.shutdown]`, `[src:proxy/scheduler/VelocityScheduler.shutdown]`.

### Managed Kotlin base without fighting Guice

Velocity *must* construct the `main` class through Guice; the framework cannot own instantiation. Workable shape:

```kotlin
public abstract class SculkVelocityPlugin(
    protected val proxy: ProxyServer, logger: Logger,
    @DataDirectory dataDirectory: Path, container: PluginContainer,
) {
    @Subscribe internal fun bootstrap(event: ProxyInitializeEvent) { /* framework boot → onEnable() */ }
}
// consumer:
public class MyPlugin @Inject constructor(p: ProxyServer, l: Logger, @DataDirectory d: Path, c: PluginContainer)
    : SculkVelocityPlugin(p, l, d, c)
```

Why this works: `collectMethods` walks the superclass chain (`superclass != Object.class`) and resolves handlers with `MethodHandles.privateLookupIn`, so a non-public `@Subscribe` in the base class is found `[src:proxy/event/VelocityEventManager.collectMethods]`. Exactly one `@Inject` constructor (TOO_MANY_CONSTRUCTORS otherwise); no default parameter values on it (Kotlin emits a synthetic overload). The Gradle plugin writes `velocity-plugin.json` with `main` = the consumer class. Alternative — `@Inject lateinit var` field injection — works but hides the dependency order; prefer the constructor.

---

## 3. Events

### Registration and handler shapes `[src:api/event/*]`, `[src:proxy/event/VelocityEventManager.java]`

| Shape | `AsyncType` | Runs on |
| --- | --- | --- |
| `@Subscribe fun on(e: E)` (default `async = true`) | `ALWAYS` | plugin `ExecutorService` (always hops) |
| `@Subscribe(async = false) fun on(e: E)` | `NEVER` | the firing thread (often a Netty event loop) |
| `@Subscribe fun on(e: E, c: Continuation)` | `NEVER` | firing thread; you must `resume()` exactly once |
| `@Subscribe fun on(e: E): EventTask?` | `SOMETIMES` | inline unless `task.requiresAsync()` and the chain isn't already async |
| `eventManager.register(plugin, E::class.java, priority: Short, EventHandler<E>)` | `SOMETIMES` | functional; return `EventTask` via `AwaitingEventExecutor` for async |
| deprecated `register(plugin, E, PostOrder, handler)` | `ALWAYS` | |

`@Subscribe(priority: Short = 0)` — "the higher the priority, the earlier the event handler will be called"; `order: PostOrder` deprecated (`FIRST, EARLY, NORMAL, LATE, LAST`, mapped through `POST_ORDER_MAP` when `priority == 0`). Javadoc: "if any method handler targeting an event type is marked with `true`, then every handler targeting that event type will be executed asynchronously" and "This option will become `false` by default in a future release of Velocity". Handler must be non-static, non-abstract, first param = event, return `void` or `EventTask`; errors are reported at registration. `register(plugin, listener)` accepts the plugin instance **or** its `PluginContainer` (`fromInstance` short-circuits on `PluginContainer`).

`EventTask` factories: `async(Runnable)` (`requiresAsync = true`), `withContinuation(Consumer<Continuation>)` (`false`), `resumeWhenComplete(CompletableFuture<?>)`. There is **no** `resultedAsync` in 4.x source (the directive's term; UNVERIFIED whether it existed in an older build). `Continuation.resume()`/`resumeWithException(t)` — second call throws `IllegalStateException`.

### Dispatch algorithm `[src:VelocityEventManager.fire / ContinuationTask]`

1. `fire(event)`: no handlers → `completedFuture(event)` immediately (no thread hop). Otherwise if handler[0] is `ALWAYS`, the whole chain starts on that plugin's executor; else inline.
2. Handlers run **strictly sequentially** in priority order. For each: `executeAsync(event)`; `null` → next. Otherwise wrap in `ContinuationTask`; if `currentlyAsync || !task.requiresAsync()` execute inline, else dispatch to that plugin's executor and *return* (the chain continues when the continuation resumes).
3. On resume from another thread: if the resuming thread is the one that fired and the next handler isn't `ALWAYS`, continue inline; otherwise continue on the next handler's plugin executor. Once a chain has gone async it stays async.
4. Exceptions in a handler are logged ("Couldn't pass {} to {} {}") and the chain **continues**; the returned future never completes exceptionally.
5. The future completes after the last handler. `@AwaitingEvent` marks events the proxy waits on; everything else is fire-and-forget from the proxy's point of view (`fireAndForget` skips the future).

Consequence: `NEVER`/`SOMETIMES` handlers of events fired from the pipeline (`PluginMessageEvent`, `PlayerChatEvent`, cookie events, …) run on **Netty event loop threads** and must not block. The backend-side config-state plugin-message path even pauses auto-read on the backend channel until the event completes `[src:proxy/connection/backend/ConfigSessionHandler.handle(PluginMessagePacket)]`.

### Results

`ResultedEvent<R extends Result>`: `getResult()`, `setResult(R)` (non-null), `Result.isAllowed()`. Built-ins `GenericResult.allowed()/denied()`, `ComponentResult.allowed()/denied(Component)`. No `isCancelled` anywhere. "Cancellation" is always `setResult(denied-ish)` and only on resulted events.

### Event table (package `com.velocitypowered.api.event.*`)

| Event | Awaited | Result type / mutation | Notes |
| --- | --- | --- | --- |
| `connection.ConnectionHandshakeEvent(InboundConnection, HandshakeIntent)` | no | — | fired async, not waited |
| `connection.PreLoginEvent(InboundConnection, username, uuid?)` | yes | `PreLoginComponentResult.allowed()/forceOnlineMode()/forceOfflineMode()/denied(Component)` | cast connection to `LoginPhaseConnection` for login plugin messages; `getUniqueId()` null < 1.19.2 |
| `player.GameProfileRequestEvent(InboundConnection, GameProfile, onlineMode)` | yes | `setGameProfile(GameProfile?)` | skins/custom profiles |
| `permission.PermissionsSetupEvent(PermissionSubject, PermissionProvider)` | yes | `setProvider(PermissionProvider?)` | once per subject; player default `ALWAYS_UNDEFINED` (⇒ `hasPermission` false), console `ALWAYS_TRUE` `[src:proxy/connection/client/ConnectedPlayer.DEFAULT_PERMISSIONS, console/VelocityConsole]` |
| `connection.LoginEvent(Player, serverIdHash?)` | yes | `ComponentResult` | authenticated, before first server |
| `connection.PostLoginEvent(Player)` | yes | — | fully initialised |
| `player.PlayerChooseInitialServerEvent(Player, RegisteredServer?)` | yes | `setInitialServer(RegisteredServer?)` | |
| `player.ServerPreConnectEvent(Player, original, previous?)` | yes | `ServerResult.allowed(server)/denied()` | denied ⇒ `Status.CONNECTION_CANCELLED` |
| `player.ServerConnectedEvent(Player, server, previous?)` | yes ("deprecated behaviour, likely removed in Polymer") | — | |
| `player.ServerPostConnectEvent(Player, previous?)` | no | — | `getCurrentServer()` valid |
| `player.PlayerClientLoadedWorldEvent(Player)` `@Beta` | no | — | once per `ServerConnection`, client-driven, no timeout |
| `player.KickedFromServerEvent(Player, server, reason?, duringServerConnect, result)` | yes | sealed `ServerKickResult`: `DisconnectPlayer.create(c)`, `RedirectPlayer.create(server[, msg])`, `Notify.create(msg)` | |
| `connection.DisconnectEvent(Player, LoginStatus)` | only at shutdown | — | `LoginStatus`: `SUCCESSFUL_LOGIN, CONFLICTING_LOGIN, CANCELLED_BY_USER, CANCELLED_BY_PROXY, CANCELLED_BY_USER_BEFORE_COMPLETE, PRE_SERVER_JOIN` |
| `command.CommandExecuteEvent(CommandSource, command, InvocationInfo)` | yes | `CommandResult.allowed()/denied()/forwardToServer([newCmd])/command(newCmd)` | `InvocationInfo(SignedState, Source)`; denying `SIGNED_WITH_ARGS` **kicks** the player |
| `command.PostCommandInvocationEvent` | no | — | `CommandResult` enum `EXECUTED/FORWARDED/SYNTAX_ERROR/EXCEPTION` |
| `command.PlayerAvailableCommandsEvent(Player, RootCommandNode<?>)` | yes | mutate root node | the 1.13+ client command tree |
| `player.TabCompleteEvent(Player, partial, List<String>)` | yes | mutable list | ≤ 1.12.2 only |
| `player.PlayerChatEvent(Player, message)` | yes | `ChatResult.allowed()/denied()/message(s)`; `setResult` deprecated | 1.19.1+: denying kicks (signed chat) |
| `connection.PluginMessageEvent(source, target, ChannelIdentifier, byte[])` | yes | `ForwardResult.forward()/handled()` | source `Player` **or** `ServerConnection`; only for registered channels (§7) |
| `player.PlayerChannelRegisterEvent / PlayerChannelUnregisterEvent` | no | — | client `minecraft:register` |
| `player.PlayerClientBrandEvent`, `PlayerModInfoEvent`, `PlayerSettingsChangedEvent` | no | — | settings fires repeatedly |
| `player.PlayerResourcePackStatusEvent(Player, packId?, Status, ResourcePackInfo?)` | yes | `setOverwriteKick` (< 1.17 only) | `Status`: `SUCCESSFUL, DECLINED, FAILED_DOWNLOAD, ACCEPTED, DOWNLOADED, INVALID_URL, FAILED_RELOAD, DISCARDED` |
| `player.ServerResourcePackSendEvent / ServerResourcePackRemoveEvent` | yes | `GenericResult`; `setProvidedResourcePack` | backend-originated packs |
| `player.ServerLoginPluginMessageEvent(ServerConnection, id, contents, seq)` | yes | `ResponseResult.unknown()/reply(byte[])` | backend login plugin messages |
| `proxy.ProxyPingEvent(InboundConnection, ServerPing)` | yes | `setPing`, `GenericResult` (deny closes) | also fired for 1.19.1+ server-data MOTD |
| `query.ProxyQueryEvent` | yes | `setResponse` | GS4 |
| `connection.PreTransferEvent(Player, InetSocketAddress)` `@Experimental` | yes | `TransferResult.allowed()/denied()/transferTo(addr)` | both plugin and backend transfers |
| `player.CookieStoreEvent / CookieRequestEvent / CookieReceiveEvent` | yes | `ForwardResult.forward()/handled()/key(Key)/data(byte[])` | §9 |
| `player.configuration.PlayerConfigurationEvent(Player, ServerConnection)` | yes, **unbounded** | — | the hook to hold a player in CONFIGURATION |
| `…PlayerEnterConfigurationEvent` | yes, ≤ 5 s | — | not fired for initial config |
| `…PlayerEnteredConfigurationEvent` | no | — | `getProtocolState() == CONFIGURATION` guaranteed |
| `…PlayerFinishConfigurationEvent` | yes, ≤ 5 s (`completeOnTimeout`) | — | |
| `…PlayerFinishedConfigurationEvent` | no | — | state is `PLAY` |
| `proxy.ProxyInitializeEvent`, `ProxyShutdownEvent` | yes | — | §2 |
| `proxy.ProxyPreShutdownEvent` `@Beta` | yes ≤ 10 s | — | §2 |
| `proxy.ListenerBoundEvent`, `ListenerCloseEvent`, `ProxyReloadEvent` | no | — | |
| `proxy.server.ServerRegisteredEvent`, `ServerUnregisteredEvent` (records) | no | — | |

All `[src:api/event/**]`; timeouts from `[src:proxy/connection/client/ClientConfigSessionHandler.handleBackendFinishUpdate]`, `[src:proxy/VelocityServer.shutdown]`.

---

## 4. Commands `[src:api/command/*]`, `[src:proxy/command/VelocityCommandManager.java]`, `[docs:command-api]`

| Type | Shape | Arguments | Suggestions | Permission |
| --- | --- | --- | --- | --- |
| `BrigadierCommand(LiteralArgumentBuilder<CommandSource> \| LiteralCommandNode<CommandSource>)` | Brigadier tree | typed `ArgumentType`s from `velocity-brigadier` only ("custom plugin argument types are not supported") | per-node `.suggests`, tooltips via `VelocityBrigadierMessage.tooltip(Component)` (1.13+) | `.requires(source -> …)`; return `BrigadierCommand.FORWARD` (`0xF6287429`) to hand the command to the backend |
| `SimpleCommand : InvocableCommand<SimpleCommand.Invocation>` | `execute(inv)`; `inv.source()`, `inv.alias()`, `inv.arguments(): String[]` (never null) | split | `suggest(inv): List<String>` / `suggestAsync(inv): CompletableFuture<List<String>>` | `hasPermission(inv)`; `false` ⇒ execution **and tab completion forwarded to the backend** |
| `RawCommand : InvocableCommand<RawCommand.Invocation>` | `inv.arguments(): String` raw | none | same | same |

`Command` is a `sealed interface permits BrigadierCommand, InvocableCommand`; `InvocableCommand` sealed over `RawCommand, SimpleCommand`. Javadoc warning: "Modifying the command tree … during permission checking and suggestions provision results in undefined behavior, which may include deadlocks" (the manager holds a `ReentrantReadWriteLock`: write on register, read on parse/suggest).

`CommandManager`: `metaBuilder(alias)` / `metaBuilder(BrigadierCommand)` → `CommandMeta.Builder.aliases(...)`, `.hint(CommandNode)` (non-executable, for client hints), `.plugin(Object)`, `.build()`; `register(CommandMeta, Command)`; `unregister(alias | meta)`; `getCommandMeta(alias)`; `executeAsync(source, line)` (fires `CommandExecuteEvent`) and `executeImmediatelyAsync` (no event) → `CompletableFuture<Boolean>`; `offerSuggestions` / `offerBrigadierSuggestions`; `getAliases()`, `hasCommand(alias[, source])`. Aliases are case-insensitive; registering a taken alias throws `IllegalArgumentException`. Always set `.plugin(this)` — it decides the **executor** below.

`CommandSource extends Audience, PermissionSubject` with defaults `sendRichMessage(miniMessage[, TagResolver...])` (uses `MiniMessage.miniMessage()`; the source is passed as the pointered target) and `sendPlainMessage`. `ConsoleCommandSource` is a marker. `Player` is a `CommandSource`.

**What the client sees**: for 1.13+ Velocity merges its own tree into the backend's `AvailableCommandsPacket` (`CommandGraphInjector`) and fires `PlayerAvailableCommandsEvent` before sending `[src:proxy/connection/backend/BackendPlaySessionHandler.handle(AvailableCommandsPacket)]`; `requires` predicates filter per source. Commands the source cannot run (`hasPermission` false / `requires` false) are not in the proxy tree and fall through to the backend.

**Execution thread** `[src:VelocityCommandManager.executeAsync/executeImmediatelyAsync/getAsyncExecutor/figureAsyncExecutorForParsing]`: `CommandExecuteEvent` fires (normal event rules); parsing runs on Velocity's own executor if the current thread is a Netty `FastThreadLocalThread` ("we *never* want to block the Netty event loop"), else directly; the command body runs on the **registrant plugin's `ExecutorService`** (`CommandMeta.getPlugin()`; `VelocityVirtualPlugin.INSTANCE` if unset). Syntax errors are sent red to the source; other exceptions wrap in `RuntimeException` and complete the future exceptionally; `PostCommandInvocationEvent` always fires.

---

## 5. Scheduler and threading `[src:api/scheduler/*]`, `[src:proxy/scheduler/*]`, `[docs:scheduler-api]`

`Scheduler.buildTask(plugin, Runnable | Consumer<ScheduledTask>)` → `TaskBuilder.delay(long, TimeUnit | Duration)`, `.repeat(…)`, `.clearDelay()`, `.clearRepeat()`, `.schedule()` → `ScheduledTask { plugin(); status(): SCHEDULED | CANCELLED | FINISHED; cancel() }`; `tasksByPlugin(plugin)`. `cancel()` interrupts a running task thread. `buildTask` requires a registered plugin (or its `PluginContainer`).

Implementation: one daemon timer thread `"Velocity Task Scheduler Timer"` (`ScheduledExecutorService`, `schedule` / `scheduleAtFixedRate`, millisecond units) that **hands each run to the plugin's cached `ExecutorService`** (`Executors.newCachedThreadPool`, daemon, `"<plugin name> - Task Executor #%d"`, created lazily per container). Exceptions are logged per task. Repeat intervals are wall-clock, no ticks. "On Velocity, there is no main thread" `[docs:scheduler-api]`.

The same per-plugin pool runs: `ALWAYS` event handlers, async event tasks, command bodies, scheduler tasks, and is the injected `ExecutorService`. It is unbounded (cached) — blocking I/O is tolerable there; it is **not** on the Netty event loops. Never block: Netty event loop threads (`FastThreadLocalThread`) — i.e. `NEVER`/`SOMETIMES` handlers, Brigadier `requires` predicates when invoked by the pipeline, `Continuation` callbacks resumed on the event loop. Login-path awaited events (`PreLogin`, `GameProfileRequest`, `PermissionsSetup`, `Login`, `PostLogin`, `ChooseInitialServer`) delay the player's login for as long as they run.

**Coroutine mapping (recommendation)**: `val proxyDispatcher = container.executorService.asCoroutineDispatcher()` (one hop, no scheduler allocation per resume — MCCoroutine instead calls `scheduler.buildTask(...).schedule()` for *every* dispatch with `isDispatchNeeded = true`, which registers each resume in the `tasksByPlugin` multimap `[mcc:dispatcher/VelocityCoroutineDispatcher.kt]`). `delay()` can use kotlinx's default delay (no tick alignment exists to preserve). Scope = `SupervisorJob() + proxyDispatcher + CoroutineExceptionHandler`, cancelled in `ProxyShutdownEvent` **before** `scheduler.shutdown()` closes the pool (dispatching after shutdown throws `RejectedExecutionException`).

---

## 6. Players, servers, permissions `[src:api/proxy/**]`, `[src:api/permission/*]`, `[src:api/util/*]`

| Type | Members that matter |
| --- | --- |
| `proxy.ProxyServer : Audience` | `getPlayer(String \| UUID)`, `getAllPlayers()`, `getPlayerCount()`, `getServer(name)`, `getAllServers()`, `matchPlayer/matchServer(prefix)`, `registerServer(ServerInfo)`, `unregisterServer`, `createRawRegisteredServer(ServerInfo)` (not in map), `getConsoleCommandSource()`, `getPluginManager/EventManager/CommandManager/Scheduler/ChannelRegistrar()`, `getBoundAddress()`, `getConfiguration(): ProxyConfig`, `getVersion(): ProxyVersion(name, vendor, version)`, `createResourcePackBuilder(url)`, `shutdown([Component])`, `isShuttingDown()`, `closeListeners()`. Audience = every player + console (`VelocityServer implements ForwardingAudience`) |
| `proxy.InboundConnection` | `getRemoteAddress()`, `getVirtualHost(): Optional<InetSocketAddress>` (normalised), `getRawVirtualHost()`, `isActive()`, `getProtocolVersion()`, `getProtocolState(): HANDSHAKE/STATUS/LOGIN/CONFIGURATION/PLAY`, `getHandshakeIntent(): STATUS/LOGIN/TRANSFER`, `getSessionId(): UUID` (stable per client connection; not Mojang's 26.2 play-session id) |
| `proxy.LoginPhaseConnection : InboundConnection, KeyIdentifiable` | `sendLoginPluginMessage(ChannelIdentifier, byte[], MessageConsumer)` — login holds until all responses arrive |
| `proxy.Player : CommandSource, InboundConnection, ChannelMessageSource, ChannelMessageSink, Identified, HoverEventSource, Keyed, KeyIdentifiable, Sound.Emitter, PlayerHeadObjectContents.SkinSource` | `getUsername()`, `getUniqueId()`, `getGameProfile()`, `get/setGameProfileProperties`, `getEffectiveLocale()?`/`setEffectiveLocale` (feeds `GlobalTranslator`), `getPlayerSettings()`, `hasSentPlayerSettings()`, `getModInfo(): Optional<ModInfo>`, `getClientBrand()?`, `getPing()`, `isOnlineMode()`, `getCurrentServer(): Optional<ServerConnection>`, `createConnectionRequest(RegisteredServer)`, `disconnect(Component)`, `spoofChatInput(String)`, `getTabList()`, player-list header/footer, `sendResourcePackOffer(ResourcePackInfo)`, `getAppliedResourcePacks()/getPendingResourcePacks()`, `sendPluginMessage(id, byte[] \| PluginMessageEncoder)` (**to the client only**), chat completions, `transferToHost(InetSocketAddress)`, `storeCookie(Key, byte[])`, `requestCookie(Key)`, `setServerLinks(List<ServerLink>)` |
| `proxy.ConnectionRequestBuilder` | `connect(): CompletableFuture<Result>` (no client messaging), `connectWithIndication(): CompletableFuture<Boolean>`, `fireAndForget()`; `Result.getStatus(): SUCCESS/ALREADY_CONNECTED/CONNECTION_IN_PROGRESS/CONNECTION_CANCELLED/SERVER_DISCONNECTED`, `getReasonComponent()`, `getAttemptedConnection()` |
| `proxy.ServerConnection : ChannelMessageSource, ChannelMessageSink` | `getServer()`, `getPreviousServer()`, `getServerInfo()`, `getPlayer()`, `isClientLoaded()` `@Beta`, `sendPluginMessage(...)` (→ backend, via this player) |
| `proxy.server.RegisteredServer : ChannelMessageSink, Audience` | `getServerInfo()`, `getPlayersConnected()` (proxy-local view), `ping([PingOptions])`, `sendPluginMessage(...)` (via *any* connected player; `false` if none). Audience = its players on this proxy; "do not interact with the server in any way" |
| `proxy.server.ServerInfo(name, InetSocketAddress)` | immutable, `Comparable` |
| `proxy.server.PingOptions.builder().version(ProtocolVersion).timeout(Duration)` (`0` = config read-timeout, `<0` = none) `.virtualHost(String)` | |
| `proxy.player.PlayerSettings` | `getLocale()`, `getViewDistance()`, `getChatMode(): SHOWN/COMMANDS_ONLY/HIDDEN`, `hasChatColors()`, `getSkinParts()`, `getMainHand()`, `isClientListingAllowed()`, `isTextFilteringEnabled()`, `getParticleStatus()` |
| `proxy.player.ResourcePackInfo` | `getId(): UUID`, `getUrl()`, `getPrompt()?`, `getShouldForce()`, `getHash()?` (SHA-1), `getOrigin()/getOriginalOrigin(): DOWNSTREAM_SERVER/PLUGIN_ON_PROXY`, `asBuilder([url])`; `Builder.setId/setShouldForce/setHash/setPrompt/build` |
| `util.ModInfo(type, List<Mod(id, version)>)`; `ModInfo.DEFAULT = ("FML", [])` | id/version < 128 chars |
| `util.ServerLink.serverLink(Component, url)` / `serverLink(Type, url)`; `Type`: `BUG_REPORT, COMMUNITY_GUIDELINES, SUPPORT, STATUS, FEEDBACK, COMMUNITY, WEBSITE, FORUMS, NEWS, ANNOUNCEMENTS` | backend packets may override |
| `proxy.config.ProxyConfig` | `isOnlineMode()`, `getServers(): Map<String,String>` (toml, not live), `getAttemptConnectionOrder()`, `getForcedHosts()`, `getMotd()`, `getShowMaxPlayers()`, `getCompressionThreshold/Level()`, `getLoginRatelimit()`, `getConnectTimeout()`, `getReadTimeout()`, command/tab-complete ratelimit + kick thresholds, query settings, `isAnnounceForge()`, `getFavicon()`. No forwarding mode/secret exposed |
| `permission.PermissionSubject` | `hasPermission(String)` (= `getPermissionValue(..).asBoolean()`), `getPermissionValue(String): Tristate`, `getPermissionChecker(): PermissionChecker` (Adventure) |
| `permission.PermissionFunction` (`ALWAYS_TRUE/FALSE/UNDEFINED`), `PermissionProvider.createFunction(subject)` | installed once per subject via `PermissionsSetupEvent.setProvider`; `Tristate.TRUE/FALSE/UNDEFINED`, `asBoolean()` (UNDEFINED → false), `toAdventureTriState()` |

Adventure ops on `Player` that are **no-ops**: `playSound(Sound)`, `playSound(Sound, x, y, z)`, `openBook`, `showDialog`, `closeDialog`; `playSound(Sound, Emitter)` / `stopSound` only 1.19.3+ `[src:api/proxy/Player.java]`, `[docs:pitfalls]`.

---

## 7. Plugin messaging

### Velocity API `[src:api/proxy/messages/*]`, `[docs:plugin-messaging]`

- `ChannelIdentifier.getId()`. `MinecraftChannelIdentifier.create(ns, name)` (ns `[a-z0-9_.-]`, name `[a-z0-9/._-]`), `.from("ns:name")`, `.from(Key)`, `.forDefaultNamespace(name)` (discouraged), `.asKey()`. `LegacyChannelIdentifier(name)` for ≤ 1.12; the registrar also maps its rewritten modern form `[src:proxy/util/VelocityChannelRegistrar.register]`.
- `ChannelRegistrar.register(ChannelIdentifier...)` / `unregister(...)` via `proxy.getChannelRegistrar()`. **`PluginMessageEvent` fires only for registered channels**; unregistered channels are passed through untouched (or queued while the FML/login handshake is incomplete) `[src:proxy/connection/client/ClientPlaySessionHandler.handle(PluginMessagePacket)]`.
- Sending: `ChannelMessageSink.sendPluginMessage(id, byte[])` / `(id, PluginMessageEncoder)` (`@ApiStatus.Experimental`, `ByteArrayDataOutput` lambda). Sinks: `Player` (→ client only), `ServerConnection` (→ that player's backend), `RegisteredServer` (→ backend via any connected player; `false` when none).

### Trust semantics

`PluginMessageEvent(source, target, id, data)`: client → proxy has `source is Player`, `target is ServerConnection`; backend → proxy has `source is ServerConnection`, `target is Player`. Default result is `forward()`. After the event completes, an allowed message is **re-encoded from the copied bytes and written to the target on its event loop** `[src:ClientPlaySessionHandler / BackendPlaySessionHandler.handle(PluginMessagePacket)]`. In CONFIGURATION state only the backend → client direction fires the event (with auto-read paused) `[src:ConfigSessionHandler]`; client → proxy config-state payloads are not event-dispatched (UNVERIFIED beyond grep of `ClientConfigSessionHandler`, which has no `PluginMessagePacket` handler for the event).

The classic spoofing hole: a client can emit *any* channel, including yours. If the proxy forwards it, Paper receives it on that player's connection and `ServerCommonPacketListenerImpl` calls `messenger.dispatchIncomingMessage(paperConnection(), identifier, data)` for every custom payload — **the backend cannot tell proxy-origin from client-origin** `[paper:paper-server/patches/sources/net/minecraft/server/network/ServerCommonPacketListenerImpl.java.patch]`. Velocity's own mitigations: the `bungeecord:main`/`BungeeCord` channel from clients is swallowed (`isBungeeCordMessage` → `return true`, never forwarded) and backend-side it is served by `BungeeCordMessageResponder` only when `bungee-plugin-message-channel` is enabled `[src:ClientPlaySessionHandler]`, `[src:proxy/connection/backend/BungeeCordMessageResponder.process]`. Docs: "set the result to `ForwardResult.handled()` unless the client should take part … unconditionally after the identifier check and before the source check" and on backends "only listen for plugin messages if a trusted proxy is the only sender" `[docs:plugin-messaging]`.

### Size limits

| Hop | Limit | Source |
| --- | --- | --- |
| client → proxy (serverbound) payload | **32 767 B** (`velocity.max-plugin-message-payload-size.serverbound`) | `[src:proxy/protocol/packet/PluginMessagePacket.getPayloadLimit]` |
| backend → proxy / proxy → client (clientbound) payload | 1 048 576 B (`…clientbound`) | same |
| proxy → backend | written as serverbound; `ProtocolUtils.writeByteArray` refuses `> Short.MAX_VALUE` for non-Forge connections | `[src:proxy/protocol/ProtocolUtils.java:706]` |
| Paper `Messenger.MAX_MESSAGE_SIZE` | 1 048 576 | `[paper:paper-api/…/plugin/messaging/Messenger.java]` |
| Paper `Messenger.MAX_CHANNEL_SIZE` | `Short.MAX_VALUE` (`paper.maxCustomChannelName`) | same |
| channel id string | ≤ 64 KiB UTF-8 (`DEFAULT_MAX_STRING_SIZE`) | `[src:ProtocolUtils.java:145]` |

Design floor: **≤ 32 KiB per frame in both directions**, chunk above that.

### Paper side `[paper:docs paper/dev/plugin-messaging]`, `[paper:StandardMessenger.java]`

`Messenger.registerIncomingPluginChannel(plugin, channel, PluginMessageListener)`; `registerOutgoingPluginChannel(plugin, channel)` must precede `player.sendPluginMessage(plugin, channel, bytes)`. Channels are validated as `NamespacedKey` (lowercase, `[a-z0-9/._-]`, `BungeeCord` grandfathered → `bungeecord:main`); `isReservedChannel` = `minecraft:register`/`minecraft:unregister` (javadoc: all `minecraft:*` except `minecraft:brand`); `ReservedChannelException` on reserved; duplicate listener → `IllegalArgumentException`. `PluginMessageListener.onPluginMessageReceived(channel, Player, byte[])`; Paper also has an experimental `dispatchIncomingMessage(PlayerConnection, …)` + `PluginMessageBridge` path for CONFIGURATION-phase payloads. Thread: dispatched from the packet listener on the server (main/region) thread — UNVERIFIED for Folia regions; verify in workstream for Paper.

### Recommended `sculk-network` design (typed transport, proxy ↔ Paper)

| Concern | Default |
| --- | --- |
| Channel | one `MinecraftChannelIdentifier.create("sculk", "net")` per protocol family (`sculk:net`, `sculk:net-cfg` for configuration phase). Proxy registers it; Paper registers incoming + outgoing |
| Origin gate (proxy) | listener returns `handled()` **first** for `sculk:*`, then `if (source !is ServerConnection) return` — client frames never reach a backend and never reach handlers |
| Origin gate (Paper) | config `network.behind-proxy = true` required to enable listeners at all; frames failing auth are dropped and counted, never thrown |
| Frame | magic `0x534B` ("SK"), `version: u8`, `flags: u8` (chunked, compressed, signed), `messageId: u16` (registry of `@Serializable` types), `requestId: varint`, `seq: varint` (per-connection monotonic; TCP ordering makes this sufficient replay protection), `len: varint`, payload (kotlinx-serialization CBOR or protobuf — binary, schema-evolvable), optional 16-byte truncated HMAC-SHA256 over header+payload |
| Key | reuse Velocity's `forwarding.secret` file (Velocity already does `HmacSHA256` over `velocity:player_info` with it `[src:proxy/connection/PlayerDataForwarding.java]`); Paper side already has the same secret for modern forwarding. Opt-in, default **on** when both sides are in modern-forwarding mode |
| Size | frame ≤ 32 000 B; chunk flag + `(chunkIndex, chunkCount)` above; reassembly bounded (max 1 MiB, timeout 10 s) |
| Carrier | `ServerConnection.sendPluginMessage` for player-scoped messages; `RegisteredServer.sendPluginMessage` for server-scoped (returns `false` with zero players → surface as `SculkResult.Failure(NoCarrier)`; offer a Redis/Valkey fallback via `sculk-data` for empty servers) |
| Threading | proxy handler marked `async = false` + `EventTask.withContinuation` → decode on the event loop is cheap, dispatch to coroutine scope; Paper handler hops to the owning region/main as the Paper runtime dictates |
| Observability | per-channel counters: accepted, rejected-origin, rejected-auth, oversize, decode-failure |

---

## 8. Adventure on Velocity

- Bundled **Adventure 5.2.0** (`api`, `gson`, `legacy`, `plain`, **`minimessage`**, `text-logger-slf4j`, `ansi`) exposed as `api` deps; plugins must not shade Adventure `[src:api/build.gradle.kts]`. `ComponentLogger` is injectable (§2).
- `ProxyServer` is a `ForwardingAudience` of all players + console `[src:proxy/VelocityServer.audiences]`; `RegisteredServer` audience = players on that server from this proxy; `Player` supports chat, action bar, titles, boss bars, tab header/footer, resource packs; sounds only 1.19.3+ with an emitter; books/dialogs unsupported `[docs:pitfalls]`.
- `CommandSource.sendRichMessage` uses `MiniMessage.miniMessage()` with the source as pointer target — the same trust boundary Sculk already enforces (templates parsed, values via `Placeholder.unparsed`) applies unchanged.
- Translation: `Player.getEffectiveLocale()` drives `GlobalTranslator`; `TranslationRegistry` no longer exists in Adventure 5 — `sculk-text`'s bundle loader must use the 5.x `Translator`/`TranslationStore` API. **Cross-workstream risk:** `sculk-text` is shared with the Paper runtime; it must compile against Adventure 5 for Velocity 4. Whether Paper 1.21.11/26.x ships Adventure 5 is UNVERIFIED here (workstream for Paper to confirm).

---

## 9. Configuration phase and modern connection `[src:api/network/ProtocolState.java]`, `[src:api/event/player/configuration/*]`, `[src:proxy/connection/client/ClientConfigSessionHandler.java]`

- `InboundConnection.getProtocolState()` and `getHandshakeIntent()`; `ProtocolState.CONFIGURATION` is entered on login (1.20.2+) and on every server switch; the PLAY→CONFIGURATION re-entry is backend-driven (`StartUpdatePacket`), preceded by `PlayerEnterConfigurationEvent` (≤ 5 s, not for the initial configuration) and followed by `PlayerEnteredConfigurationEvent`.
- `PlayerConfigurationEvent(player, server)` is the only **unbounded** hold: for 1.20.5+ backends it is fired when the client answers the known-packs request and the response is delayed until the future completes; for 1.20.2–1.20.4 at configuration end. Then `PlayerFinishConfigurationEvent` (≤ 5 s) → `FinishedUpdatePacket` → `PlayerFinishedConfigurationEvent`. Use `PlayerConfigurationEvent` to send resource packs / cookies / server links before PLAY.
- **Cookies**: `Player.storeCookie(Key, byte[])`, `requestCookie(Key)`; data arrives via `CookieReceiveEvent` (`ForwardResult.handled()` keeps it on the proxy; `key(...)`/`data(...)` rewrite). Backend-initiated store/request pass through `CookieStoreEvent`/`CookieRequestEvent` (both intercepted in CONFIGURATION and PLAY) `[src:ConfigSessionHandler, ConnectedPlayer]`.
- **Transfers**: `Player.transferToHost(addr)` and backend `TransferPacket` both fire `PreTransferEvent` (`transferTo(addr)` rewrites); incoming transfers show `HandshakeIntent.TRANSFER`.
- **Server links**: `Player.setServerLinks(List<ServerLink>)`; backend `ClientboundServerLinksPacket` is forwarded during configuration.
- No "code of conduct" API exists in `velocity-api` 4.2.x (grep of `api/` finds nothing).
- `InboundConnection.getSessionId()` is Velocity's per-connection UUID, stable across login events and backend connections for one client session.

---

## 10. Kotlin pitfalls

| Pitfall | Fact | Mitigation |
| --- | --- | --- |
| Guice 7 is jakarta-only | `javax.inject.Inject` is ignored → MISSING_CONSTRUCTOR (`google/guice/wiki/Guice700`) | use `jakarta.inject.Inject` (or `com.google.inject.Inject`); one `@Inject` constructor; no default args on it |
| `object`/companion as plugin main | Guice must instantiate; private constructor fails | plugin main is a `class`; framework singletons live behind it |
| Annotation processor vs Kotlin | `annotationProcessor` sees only Java sources; the processor is `javax.annotation.processing`, so KSP cannot run it | generate `velocity-plugin.json` from Gradle; drop `@Plugin` |
| JDK 25 class files | `velocity-api` 4.x is compiled by a Java 25 toolchain; Kotlin < 2.3 caps `jvmTarget` at 24 and warns on a 25 toolchain (Kotlin 2.3.0 release notes: "Kotlin/JVM: Support for Java 25") | Kotlin **≥ 2.3**, Gradle toolchain 25 for `sculk-velocity`; emit `jvmTarget = 21` or `25` as the module decides |
| Shading kotlin-stdlib | `PluginClassLoader.loadClass0` falls through to **every other plugin's loader** `[src:proxy/plugin/PluginClassLoader.java]`; two plugins shading unrelocated stdlib → first loaded wins; relocation makes Kotlin types in our public API unusable | ship a **provider plugin** (`sculk-velocity-runtime`: kotlin-stdlib, kotlinx-coroutines, kotlinx-serialization, sculk modules), consumers declare `@Dependency(id = "sculk")`. Precedent: `4drian3d/MCKotlin` (`mckotlin-velocity`, Velocity 3.4+, Kotlin 2.0.21; 4.x support UNVERIFIED) and the archived (2026-03-20) `VelocityPowered/velocity-language-kotlin` |
| Coroutine dispatch | nothing is "main"; event loops must not block; `runBlocking` on an event loop deadlocks the connection | `executorService.asCoroutineDispatcher()`; suspend handlers via `withContinuation`; `CoroutineStart.UNDISPATCHED` only when the prefix is non-blocking |
| MCCoroutine (`mccoroutine-velocity-*` 2.23.0) | dispatcher = `scheduler.buildTask(...).schedule()` per resume with `isDispatchNeeded = true` (allocation + multimap churn); built against `velocity-api 3.1.1`; reflects into `VelocityServer.logger` to filter startup warnings; requires injecting `SuspendingPluginContainer` and calling `initialize(this)`; suspend listeners use deprecated `PostOrder`; `launch(UNDISPATCHED)` in `registerSuspend` runs the handler prefix on the firing (event loop) thread `[mcc:…]` | do not depend on it; implement the bridge natively (~150 lines) |
| Event handler visibility | Kotlin members are `final`/`public` by default; `private` handlers are fine (private lookup) but `internal` names are mangled — irrelevant to reflection, fine | prefer `internal`/`private` handlers in the base class |

---

## 11. Draft `sculk-velocity` runtime contract

### Lifecycle phases → Velocity

| Sculk phase | Trigger | Allowed work |
| --- | --- | --- |
| `Constructing` | Guice constructor | capture injected `ProxyServer`, `Logger`, `ComponentLogger`, `@DataDirectory Path`, `PluginContainer`; create `SculkProxyPlatform` shell; **no registration** |
| `Loading` | start of base-class `@Subscribe(priority = Short.MAX_VALUE) ProxyInitializeEvent` | config load/migration, `ServiceRegistry` population, coroutine scope creation (`SupervisorJob + executor dispatcher`) |
| `Enabling` → user `onEnable()` | same handler, after framework boot | register listeners (`eventManager.register(container, …)`), commands, channels, tasks |
| `Running` | after `ProxyInitializeEvent` completes / `ListenerBoundEvent` | normal |
| `Reloading` | `ProxyReloadEvent` | re-read config; re-resolve servers |
| `Draining` | `ProxyPreShutdownEvent` | `onPreShutdown()` with live players (transfer/kick) — budget < 10 s |
| `Disabling` | `ProxyShutdownEvent` | `onDisable()`, flush data, **cancel coroutine scope**, unregister listeners/commands/channels — executors still alive |
| `Terminated` | after `scheduler.shutdown()` | nothing runs |

### `ExecutionContext.Proxy`

- No main thread, no regions. `SculkScheduler` on Velocity: `runLater/repeat` → `Scheduler.buildTask` (wall-clock `Duration`, no ticks → tick-based API must be expressed in `Duration` and documented as approximate); `runSync` has **no meaning**; the region overloads throw `UnsupportedOperationException` at **registration** (not call) time so a Paper-only consumer fails fast.
- Dispatcher taxonomy: `Dispatchers.Proxy` (plugin executor), `Dispatchers.Connection(player)` — optional pinned dispatcher on that player's Netty event loop for packet-ordering-sensitive work (UNVERIFIED whether `MinecraftConnection.eventLoop()` is reachable through API; it is **not** public API → likely omit).
- Events: `suspend` listeners compile to `AwaitingEventExecutor` returning `EventTask.withContinuation`; awaited events get a framework timeout (default 5 s for login-path events) that logs and resumes; results map to `SculkResult` where a `ResultedEvent` exists, else to `Unit`.

### `CommandSpec` compiler for Velocity

Emit one `BrigadierCommand` per spec (never `SimpleCommand`): literal root per alias via `CommandMeta.Builder.aliases` + `.plugin(container)`; arguments restricted to vanilla `ArgumentType`s shipped by `velocity-brigadier` (string, int, long, float, double, bool; Sculk-typed args parse from `StringArgumentType` and validate in `executes`); `requires` = permission + `CommandSource` kind (`Player`/`ConsoleCommandSource`); suggestions via `.suggests` returning `CompletableFuture` from a coroutine; executes returns `SINGLE_SUCCESS`, or `BrigadierCommand.FORWARD` when the spec marks "fall through to backend"; help generated from the spec, not from Brigadier usage; register in `Enabling`, unregister by `CommandMeta` in `Disabling`.

### Testability without a proxy

| Fake | Stands in for | Notes |
| --- | --- | --- |
| `FakeProxyServer` | `ProxyServer` | in-memory players/servers, `ForwardingAudience`, records `shutdown` |
| `FakeEventBus` | `EventManager` | implements the dispatch algorithm (priority order, `EventTask`/`Continuation`, async hop via a test executor) so suspend-listener semantics are testable |
| `FakeCommandManager` | `CommandManager` | real `CommandDispatcher<CommandSource>` from `velocity-brigadier` so the compiled tree is exercised |
| `FakeScheduler` (reuse `sculk-common`'s) | `Scheduler` | deterministic time |
| `FakeChannelRegistrar` + `FakeChannelMessageSink` | messaging | captures frames; replays as `PluginMessageEvent` with chosen `source` type → spoofing tests |
| `FakePlayer`, `FakeServerConnection`, `FakeRegisteredServer` | players/servers | `createConnectionRequest` returns scripted `Result` |
| `FakePermissionSubject` | permissions | `Tristate` table |

Everything a consumer *calls* is an interface in `com.velocitypowered.api` except `BrigadierCommand`, `ServerInfo`, `PingOptions`, `ModInfo`, `ServerLink` (final classes, constructible). Events are final classes with public constructors — fakes can fire real event objects.

---

## 12. Open items

1. UNVERIFIED: Adventure version Paper 1.21.11 / 26.x ships (decides whether `sculk-text` targets Adventure 5 everywhere).
2. UNVERIFIED: thread on which Paper dispatches `PluginMessageListener` under Folia.
3. UNVERIFIED: `MCKotlin` 4.x compatibility; irrelevant if we ship our own provider plugin.
4. Policy gap: PaperMC has not documented release vs SNAPSHOT for 4.x (`#1884` open) — pin `4.2.0` and bump deliberately.
5. Docs lag: `event-api` page still describes 3.0.0 semantics; source is authoritative.
