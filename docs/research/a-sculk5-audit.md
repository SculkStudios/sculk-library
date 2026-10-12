# Sculk 5.2.0 audit — historical evidence for the Sculk 6 rewrite

Workstream A. Audited tree: branch `sculk-6` at `876106e` (2026-08-14), version `5.2.0`, 279 commits, tags `v5.0.0`/`v5.1.0`/`v5.2.0`. All paths relative to repo root; `file:line` cites the audited tree.

Scope note: the tree contains **20** `sculk-*` directories. `CLAUDE.md`, `README.md`, `docs/introduction/modules.mdx` ("Sculk is sixteen modules") and `CHANGELOG.md` all omit `sculk-web` and `sculk-web-ktor`, which exist, build, have `.api` dumps and tests, and are listed in `settings.gradle.kts:36-37`. They shipped undocumented.

---

## 1. Module inventory

LOC = non-blank-agnostic `wc -l` over `src/main`, `src/test`, `src/testFixtures`. "API types" = `^public` lines in the committed `.api` dump (includes `$Companion`/`$DefaultImpls`/`$$serializer` noise). All modules apply `sculk.module` (`build-logic/src/main/kotlin/sculk.module.gradle.kts`), which adds `api(coroutines-core)`, `compileOnly(paper-api)`, `explicitApi()`, ktlint, `allWarningsAsErrors` (main only), `java-test-fixtures`, `maven-publish`.

| Module | Purpose | main / test / fixtures LOC | API types | Fixtures | External deps (licence) | Depends on |
|---|---|---|---|---|---|---|
| `sculk-common` | `SculkResult`, `SculkHandle`, scheduler contract, coroutine scope, cron/repeating/batch tasks, `DirectoryWatcher`, `MinecraftVersion`, stability annotations | 975 / 519 / 141 | 23 | `FakeScheduler` | api: kotlinx-coroutines (Apache-2.0); compileOnly: paper-api (GPL-3.0 w/ exceptions) | — |
| `sculk-text` | Theme, MiniMessage renderer, `LegacyCodes`, `MinecraftFont`, `SculkBundles` | 635 / 499 / 0 | 10 | — | compileOnly: adventure-api, adventure-text-minimessage (MIT); impl: kaml (Apache-2.0) | common |
| `sculk-config` | Typed YAML via kotlinx-serialization descriptors, defaults-as-file, append-only merge, validation, migrations, env substitution, revision bumps | 975 / 758 / 0 | 15 | — | api: kotlinx-serialization-core (Apache-2.0); impl: kaml (Apache-2.0) | common |
| `sculk-series` | Registry lookups (Material/Sound/Particle/…) with aliases, `validateKeys`, sculk-block helpers | 556 / 163 / 0 | 4 | — | none beyond convention | common |
| `sculk-items` | `ItemBuilder`, `SkullItemBuilder`, `ItemDescriptor` (`@Serializable`), PDC, dyes, `giveOrDrop`, 1.21.x component compat | 1041 / 767 / 0 | 12 | — | api: serialization-core; compileOnly: adventure (MIT) | common, text, series, integrations |
| `sculk-integrations` | Reflection adapters: PlaceholderAPI, Vault, LuckPerms, custom items (Nexo/Oraxen/ItemsAdder) | 374 / 203 / 0 | 6 | — | none (all reflection) | common |
| `sculk-commands` | `CommandSpec` as data, builder DSL, 17 parsers, cooldowns, help, `CommandText`, one Brigadier adapter | 951 / 370 / 0 | 17 | — | compileOnly: adventure-api | common, text |
| `sculk-gui` | Chest/container menus, click routing, pagination, animations, confirm menu | 1367 / 892 / 0 | 17 | — | compileOnly: adventure-api | common, text, items |
| `sculk-hud` | Sidebar, action bar (priority/expiry), tab list, boss bars, placeholders — one driver task | 535 / 458 / 0 | 5 | — | compileOnly: adventure-api | common, text |
| `sculk-data` | Hikari + JDBC repositories, `RowCodec`, `TableSchema`, additive `SchemaMigrator`, query DSL, Caffeine cache, Redis/Valkey cache, write-behind | 1718 / 1642 / 168 | 41 | `FakeRepository` | api: serialization-core+json; impl: HikariCP (Apache-2.0), Caffeine (Apache-2.0), sqlite-jdbc (Apache-2.0); compileOnly: mariadb-java-client (**LGPL-2.1**), postgresql (BSD-2), lettuce-core (Apache-2.0) | common, config |
| `sculk-packets-api` | Backend-neutral packet contract, `ClientBlockService`, `VirtualEntityService`, `PacketGuard`, `PacketDebugService`, ServiceLoader discovery | 872 / 374 / 76 | 27 | `FakeVirtualEntityService` | none | common |
| `sculk-packets-packetevents` | PacketEvents backend | 276 / 37 / 0 | 3 | — | compileOnly: packetevents-spigot (**GPL-3.0**) | packets-api |
| `sculk-packets-protocollib` | ProtocolLib backend | 150 / 21 / 0 | 2 | — | compileOnly: ProtocolLib (**GPL-2.0**) | packets-api |
| `sculk-visual` | Particles, sounds, timelines/sequences, packet holograms, passenger nametags | 938 / 322 / 56 | 18 | `FakeHologram` | compileOnly: adventure-api | common, series, text, packets-api |
| `sculk-discord` | Backend-neutral Discord: message tree (Components V2), slash specs, typed interactions, collectors, webhooks, `Mentions`, `ComponentId` | 2619 / 1771 / 606 | 98 | `FakeDiscordGateway` (+`FakeGuildService`), `FakeInteractions` (`InteractionRecorder`, fake interactions) | impl: kotlinx-serialization-json | common, text (for `ThemeStyle` only) |
| `sculk-discord-jda` | JDA backend (all `@SculkInternal`) | 1532 / 499 / 0 | 0 | — | compileOnly: JDA (Apache-2.0; transitives trove4j **LGPL-2.1**, jna LGPL-2.1/Apache-2.0) | discord |
| `sculk-web` | Backend-neutral embedded HTTP: routes as data, `WebRequest/Response`, `WebConfig` (loopback default, trusted proxies, body cap) | 230 / 107 / 50 | 8 | `FakeWebServer` | none | common |
| `sculk-web-ktor` | Ktor CIO backend (all `@SculkInternal`) | 202 / 292 / 0 | 0 | — | compileOnly: ktor-server-core, ktor-server-cio (Apache-2.0) | web |
| `sculk-platform` | `SculkPlugin`, `SculkPlatform` (lazy subsystems), `ServiceRegistry`, `SculkEventBus`, `PaperScheduler`, banner, rebuild warning | 909 / 585 / 0 | 7 | — | compileOnly: adventure-api; test: asm (BSD-3), MockBukkit (MIT) | every module except web/discord |
| `sculk-bom` | Generated constraints from `sculk-*` subprojects (`sculk-bom/build.gradle.kts:10-16`) | 0 | — | — | — | all |

Totals: main 16 855 LOC, test 10 279 LOC, fixtures 1 097 LOC, 684 `@Test` methods (+7 in `examples/discord-bot`).

Test-only deps: JUnit 6.1.3 (EPL-2.0), mockito-kotlin (MIT, 6 files), MockBukkit 4.110.0 (MIT, 10 files), H2 (MPL-2.0/EPL-1.0), kotlinx-coroutines-test. Catalog entries declared but unused by any module build: `snakeyaml`, `jmh-core`, `jmh-generator`, `jmh` plugin (`gradle/libs.versions.toml`). No `benchmarks/` module exists despite `CONTRIBUTING.md:392,443` and stale `.idea/modules/benchmarks`.

Licence posture (keep): nothing GPL/LGPL/proprietary is on any `api`/`implementation` edge. Bundled runtime graph is Apache-2.0/MIT only. `README.md:109-110` and `libs.versions.toml` comments state the rule explicitly.

---

## 2. Public API surface (from committed `.api` dumps)

Legend: **DSL** = entry point; **IMPL** = interface a consumer implements; **CALL** = interface a consumer calls (needs a fake). Every listed type carries `@SculkStable` unless marked `[exp]`/`[int]` (enforced by `sculk-platform/src/test/kotlin/studio/sculk/platform/StabilityMarkerTest.kt`).

### sculk-common (`studio.sculk`, `.annotation`, `.coroutine`, `.scheduler`, `.task`, `.version`)
| Type | Key members |
|---|---|
| `SculkResult<T>` sealed: `Success(value)`, `Failure(message, cause?)` | `isSuccess/isFailure`, `getOrNull`, `getOrThrow`; companion `success`, `ok()` (shared Unit), `failure`, `catching(describe){}`; ext `map`, `flatMap`, `onSuccess`, `onFailure`, `fold`, `getOrElse`, `recover` |
| `SculkHandle` fun interface : AutoCloseable | `close()`; companion `NONE`, `all(list/vararg)` (reverse-order, suppressed exceptions) |
| `@SculkStable`, `@SculkExperimental`, `@SculkInternal` (RequiresOptIn ERROR) | — |
| `SculkScheduler` **CALL/IMPL** | `runSync/Delayed/Repeating` ×3 overloads (global, `Entity`, `Location`), `runAsync/Delayed/Repeating`, `ownsThread(entity/location)`, `ownsGlobalThread`, default `runNow` ×3 |
| `SculkCoroutineScope` : CoroutineScope, SculkHandle | `dispatchers`, `launchMain`, `launchAsync`, `withMain`, `withAsync`; default context = **async** (`SculkCoroutineScope.kt:33`) |
| `SculkDispatchers` | `main`, `async` |
| `FuturesKt.await()` | the single `CompletableFuture` carve-out |
| `SculkTasks` **DSL** | `repeating(intervalTicks, initialDelay){}`, `delayed`, `cron(expr, zone){}`, `debounce(waitMillis){}`, `throttle(intervalMillis){}` |
| `CronExpression` | `parse`, `nextAfter(ZonedDateTime)` |
| `BatchTicker<T>` | ctor(scheduler, source, batchSize, periodTicks, initialDelay, action); `runBatch` [int] |
| `MinecraftVersion` | `parse`, `toMinecraftVersion()`, comparable |
| `io.DirectoryWatcher` [int] | ctor(folder, handlers, logger, label, dispatch) |

### sculk-text (`studio.sculk.text`)
| Type | Key members |
|---|---|
| `SculkMessages` | ctor(theme, shadowArgb, suppressItalics, miniMessage, legacyCodes=true); `render(template, vararg values)`, `render(template, extra: TagResolver, …)`, `render(lines)`, `send`, `actionBar`, `title`, `renderItemText` |
| `SculkTheme` | ctor(Map<String, ThemeStyle>); `expand`, `get`, `names`, `with`, `EMPTY` |
| `ThemeStyle` sealed: `Solid(hex)`, `Gradient(hexes)`, `Shader(hex, effect)` | `open`, `close`, `swatchHex` |
| `MinecraftFont` object | `width(String, bold)`, `width(Component)`, `centre(…)`, `SPACE_WIDTH` |
| `SculkBundles` | `reload`, `languages`, `template`, `component(lang/player, key)`, `plural`, `send` |
| `LegacyCodes` object | `present`, `toMiniMessage` |

### sculk-config (`studio.sculk.config`)
| Type | Key members |
|---|---|
| `SculkConfig` **DSL** | reified `load<T>()`, `reload<T>()`, `save<T>(v)`, `violations<T>()`, `onReload<T>{}`, `migrations<T>{}` [int on serializer overload]; `reloadAll()`, `watch(dispatch)`; companion `create(dataFolder, logger)` |
| `@ConfigFile(name, revision)`, `@Comment(vararg lines)`, `@Min`, `@Max`, `@NotEmpty` (all `@SerialInfo`) | — |
| `ConfigMigrationBuilder.from(v)`, `ConfigMigrationFrom.to(v){}`, `ConfigDocument` | `get/set/remove/rename/default` |

### sculk-series (`studio.sculk.series`)
`SculkSeries` object: `material/sound/particle/enchantment/entityType/potionEffect/biome/gameMode/difficulty(key)`, `require*`, `*Keys()`, `lookup(Class, key)`, `register(Class, resolver)`, `validateKeys(...)` → `SeriesValidationReport(missing: List<SeriesMissingKey>, valid)`. `SculkBlocksKt` [exp ×7]: `SCULK_BLOCKS`, `isSculkBlock`, `sculkSensorAt/…`. Internal: `SculkRegistry` (memoises **misses** — `registry/SculkRegistry.kt:24-30`), `MappingResolver`, `VersionAdapter`.

### sculk-items (`studio.sculk.items`)
| Type | Key members |
|---|---|
| `item(Material){}`, `item(String){}: SculkResult`, `SculkMessages.item(...)`, `skull{}` **DSL** | — |
| `ItemBuilder` (open) | ctor(material, messages, base: ItemStack?); `name`, `itemName`, `lore` ×3, `amount`, `material`, `model`, `customModelData`, `enchant(key/Enchantment, lvl)`, `glint`, `unbreakable`, `damage/maxDamage/maxStackSize`, `rarity`, `food`, `hideVanillaTooltip`, `component(Valued/NonValued)`, `unsetComponent`, `pdc` ×12, `meta{}`, `build()` |
| `SkullItemBuilder` | `owner(String/UUID/OfflinePlayer)` |
| `ItemDescriptor` `@Serializable` | material, name, lore, amount, enchantments, glint, model, customModelData, unbreakable, hideVanillaTooltip, data; `toItemStack(messages)`, `ItemStack.toDescriptor()` |
| `giveOrDrop(player, stacks)` → `ItemDeliveryResult(given, dropped, fullyDelivered)` | — |
| `Dyes.nearest(rgb/hex)`, `ItemKeys.of(name)` | — |

### sculk-integrations (`studio.sculk.integrations`) — all `@SculkExperimental`
`SculkIntegrations(plugin)`: `placeholderApi()`, `vaultEconomy()`, `luckPerms()`, `customItems()` → `SculkResult`. `PlaceholderApiIntegration.parse(player, text)`. `VaultEconomyIntegration.balance/deposit/withdraw(UUID|OfflinePlayer)`. `LuckPermsIntegration.primaryGroup(uuid)` (cache-only), `suspend groups(uuid)`, `suspend hasPermission(uuid, node)`. `CustomItems.handles/resolve/prefixes/pluginNames`.

### sculk-commands (`studio.sculk.command`)
| Type | Key members |
|---|---|
| `command(name){}` **DSL** → `CommandSpec` | — |
| `CommandBuilder` | `description`, `permission`, `aliases`, `cooldown(Duration)`, `middleware{}`, `sub(name){}`, `child(spec)`, `executes{}`/`player{}`/`console{}` (suspend); args: `string`, `greedy(name, optional)`, `int/long/double(min,max)`, `boolean`, `player`, `playerName`, `uuid`, `world`, `material`, `duration`, `enum`, `choice(name, optional, typeName, options)`, `argument(name, parser, optional)` |
| `CommandSpec` data | name, aliases, description, permission, arguments, children, cooldown, middleware, executors; `child(name)`, `flatten(prefix)`, `usage`, `usageAt`, `executable` |
| `Argument<T>` data | `name`, `parser`, `optional`, `usage` |
| `ArgumentParser<T>` **IMPL** | `typeName`, `parse(String): T?`, default `suggest(prefix)` |
| `PlayerNameParser` object (the only public parser; 16 others are `@SculkInternal`) | — |
| `CommandContext` | `sender`, `player`, `messages`, `argument<T>(name)`, `argumentOrNull`, `has`, `reply` ×2, `actionBar`, `title` |
| `CommandCooldowns` | `tryUse`, `remaining`, `forget`, `clear` |
| `CommandHelp`, `CommandHelp.Entry/Line`, `HelpCommand(supplier, help, name).spec()`, `HelpText`, `CommandText` (`DEFAULT`; 7 strings) | — |
| `brigadier.BrigadierAdapter` [int] | `build(spec)` |

### sculk-gui (`studio.sculk.gui`)
| Type | Key members |
|---|---|
| `gui(title){}`, `confirmMenu(title){}` **DSL** → `Gui` | — |
| `GuiBuilder` | `size`, `type`, `item(slot){}`, `items(slots){}`, `fill`, `border(material){}` + ext `border(material, GuiBorderStyle, except)`, `pagination{}`, `onOpen`, `onClose` |
| `GuiItemBuilder` | `material`, `name`, `lore`, `amount`, `glow`, `customModelData`, `enchantment`, `stack(ItemStack)`, `stack{}`, `describe(ItemDescriptor)`, `dynamicContent{}`, `animate(intervalTicks){}`, `interactive(Boolean)`, `onClick/onLeftClick/onRightClick/onShiftClick` |
| `GuiContext` | `player`, `session`, `slot`, `clickType`, `event`, `messages`, `reply`, `actionBar`, `title`, `close`, `open(gui)`, `nextPage`, `previousPage` |
| `GuiSession` | `state: GuiState`, `currentPage`, `setEntries`, `setPage`, `nextPage/previousPage`, `hasNext/PreviousPage`, `totalPages`, `refresh(slot)`, `refreshAll`, `renderSlot`, `openGui`, `close`, `isClosed` |
| `MenuRegistry` : SculkHandle | `open(gui, player)`, `sessionFor(player)` |
| `Gui`, `GuiItem`, `GuiAnimation(Builder)`, `GuiBorderStyle`, `GuiSlots`, `GuiState`, `PaginationBuilder/Config`, `ConfirmMenuBuilder` | — |

### sculk-hud (`studio.sculk.hud`)
`HudService` : SculkHandle — `start()`, `actionBar(player, template, priority, durationTicks, values)`, `sidebar(player, title, rows: (Player)->List<HudRow>)`, `clearSidebar`, `tabList`, `bossBar`, `clearBossBar`, `forget(player)`, `placeholders`. `Sidebar` — `show/hide/title/lines/close`. `Placeholders` — `register(name){}`, `unregister`, `resolve`, `names`. `HudRow(template, values)`. `ActionBarPriority { FEEDBACK, ACTIVITY, ALERT }`.

### sculk-data (`studio.sculk.data`)
| Type | Key members |
|---|---|
| `SculkData` : SculkHandle | reified `repository<T, ID>()`, `repository(serializer)`, `suspend transaction{}`, `dialect`, `backend`; companion `open` [int], `using(dataSource, dialect, logger)` [int] |
| `SculkRepository<T, ID>` **CALL** | `table`; suspend `find`, `findAll`, `findFirst{}`, `query{}`, `count{}`, `exists`, `save`, `saveAll`, `delete`, `deleteWhere{}`, `topBy(column, n, desc)` — all `SculkResult` |
| `Query` **DSL** | `eq/notEq/greaterThan/lessThan/atLeast/atMost/like/isIn(KProperty1, v)`, `any{}` (OR), `orderBy/orderByDescending`, `take`, `skip` |
| `Condition` sealed: `Compare`, `In`, `And`, `Or` | — |
| `@Table(name)`, `@Id`, `@Column(name)`, `@Index`, `@Json` | — |
| `TableSchema.of(descriptor)`, `ColumnSchema`, `SqlType`, `SqlDialect { SQLITE, MYSQL, POSTGRES }` (`quote`, `upsert`, `columnType`, `of`) | — |
| `cached(repo, idOf, maxSize, expire, negativeExpire)` **DSL** → `SculkCache` | `find`, `findOrCreate`, `save`, `delete`, `forget`, `peek`, `clear`, `size` |
| `RedisCache` : SculkHandle | companion `create(repo, idOf, serializer, uri, prefix, logger, ttl)`, `using(..., RedisBackend, ...)`; `find`, `findOrCreate`, `save`, `delete`, `forget`, `clear`, `available` |
| `RedisBackend` **IMPL** : SculkHandle | suspend `get/set/delete/deleteByPrefix`; `LettuceRedisBackend(uri)` |
| `WriteBehindStore` | `markDirty`, `remove`, `suspend flush`, `flushBlocking` (runBlocking), `pending`, `clear` |
| `StorageSettings(backend, file, remote: RemoteSettings, poolSize)` `@Serializable`, `UuidSerializer`, `InstantSerializer` | — |

### sculk-packets-api (`studio.sculk.packets`)
| Type | Key members |
|---|---|
| `SculkPacketServices.create(plugin, scheduler, config)` → `SculkResult<SculkPacketService>` | ServiceLoader over plugin + own loader |
| `SculkPacketService` **CALL** : SculkHandle | `backend`, `clientBlocks`, `virtualEntities`, `debug`, `listen(direction, key, priority){}`, `send(player, packet)` |
| `SculkPacketServiceProvider` **IMPL** | `backend`, `isAvailable()`, `create(plugin, scheduler)` |
| `AbstractPacketService` (for backend authors) | `clientBlockBackend()`, `virtualEntityService()` |
| `ClientBlockService` | `set/setAll/reset/resetAll/preview`, `onDig/onUse(priority){}`, `acknowledge` |
| `ClientBlockBackend` **IMPL** | `listenDig`, `listenUse`, `acknowledge` |
| `ClientBlockContext` / `BlockDigContext(action)` / `BlockUseContext(face, offHand)` | `cancel`, `acknowledge`, `intercept{}`, `runSync{}`, coords |
| `VirtualEntityService` **CALL** | `available`, `reserveEntityId`, `spawnTextDisplay`, `updateTextDisplay`, `teleport`, `mount`, `despawn`; `UnavailableVirtualEntityService` |
| `TextDisplayStyle`, `Billboard`, `PacketContext`, `PacketKey`, `PacketDirection`, `PacketPriority`, `PacketBackend`, `PacketBackendMode`, `PacketServiceConfig`, `SculkPacket`, `PacketDebugService.session{}` | — |
| `PacketGuard` [int] | `run(what){}`; `failures` |

### sculk-packets-packetevents / -protocollib
`PacketEventsPacketServiceProvider`, `PacketEventsPacket(wrapper)`, `PacketEventsEvent(event)`; `ProtocolLibPacketServiceProvider`, `ProtocolLibPacket(container)`. Registered via `META-INF/services/studio.sculk.packets.SculkPacketServiceProvider`.

### sculk-visual (`studio.sculk.visual`)
`particle(type){}`, `sound(key|Sound){}`, `timeline{ at(tick){} ; loop(n) }`, `sequence{ step{}; delay(n) }` **DSL**; `AnimationTimeline/Sequence.start(scheduler)`; `ParticleEffectDescriptor`, `SoundEffectDescriptor` (config shapes); `HologramService.create(location, lines, HologramOptions)` → `Hologram` **CALL** (`setLines`, `teleport`, `remove`); `Nametags.start(slow, fast)`, `show(player, animate){}`, `hide`, `reset`, `describe`, `broken`; `NametagStyle`, `HologramOptions`.

### sculk-platform (`studio.sculk.platform`)
| Type | Key members |
|---|---|
| `SculkPlugin` abstract : JavaPlugin | overridable `theme`, `shadow`, `builtInHelp`, `banner`, `bannerArt()`, `bannerFacts()`, `commandText()`, `setup()`, `shutdown()`; `sculk: SculkPlatform`; `+spec` (`unaryPlus`), `commands(vararg)`, `declaredCommands`, `listen(Listener)`, `keep(handle)`; `onEnable/onDisable` **final** |
| `SculkPlatform` : SculkHandle | `theme`, `scheduler`, `scope`, `messages`, `services`, `events`, `tasks`, `cooldowns`; lazy `config`, `menus`, `hud`, `bundles`, `data`, `packets: SculkResult`, `holograms`, `nametags` |
| `ServiceRegistry` | reified `register(service)`, `get`, `find`, `has`, `registered`; duplicate registration throws |
| `SculkEventBus` | reified `listen<T>(priority, ignoreCancelled, filter){}` → handle; ext `listen(Listener)` |
| `BannerArt(lines, fallback)`, `SCULK` | — |
| `PaperScheduler`, `ServerFlavour.isFolia`, `RebuildWarning`, `SculkBanner` [int] | — |

### sculk-web (`studio.sculk.web`)
`SculkWeb.create(config, loaders)` / `discover` / `disabled(reason)`; `SculkWebServer` **CALL** (`running`, `port`, `suspend start`, `route(method, path){}`, `static(prefix, resourceRoot)`); `SculkWebProvider` **IMPL**; `WebConfig(port, bind="127.0.0.1", maxBodyBytes, trustedProxies)`; `WebRequest` (`header`, `cookie`, `remoteAddress`); `WebResponse.json/noContent/error`; `HttpMethod`.

### sculk-discord (`studio.sculk.discord`, `.command`, `.interaction`, `.message`, `.webhook`)
| Type | Key members |
|---|---|
| `SculkDiscord.create(BotConfig, scope, loaders)`, `discover`, `disabled(reason)` | — |
| `BotConfig(token, intents, commandGuilds, cacheAllMembers)`, `Intent`, `Presence(Kind)`, `GatewayState` | — |
| `DiscordGateway` **CALL** : SculkHandle | `state`, `selfId`, `guilds`; suspend `connect`, `send`, `sendText(mentions)`, `edit`, `delete`, `react`, `channelExists`, `sendTyping`, `presence`, `registerCommands`, `awaitComponent(message, within, from)`, `closeAwaiting`; `route(router)`, `onMessage/onMessageEdit/onMessageDelete/onMemberChange{}` → handles |
| `DiscordGatewayProvider` **IMPL** | `backend`, `isAvailable`, `create(config, scope)` |
| `GuildService` **CALL** | `member`, `members(set)` (batched 100), `isPresent`, `role`, `roles`, `addRole/removeRole/setRoles`, `setNickname`, `kick/ban/unban`, `timeout/clearTimeout` |
| Ids (value classes): `DiscordId` sealed → `UserId`, `ChannelId`, `GuildId`, `RoleId`, `MessageId` | — |
| `ComponentId(namespace, parts)` (100-char budget enforced at build) | `encoded`, `part(i)` |
| `Mentions` sealed: `None`, `All`, `Allow(...)` | — |
| `DiscordChatMessage` (content, `displayContent`, reply, attachments, actor), `DeletedMessage`, `MemberChange` sealed (`Joined`/`Left`/`RolesChanged`), `DiscordRole`, `DiscordActor` (`highestColoredRole`) | — |
| `RateLimiter(acquire(n), used)` | — |
| `message{}` **DSL** → `DiscordMessage`; `MessageBuilder`: `text`, `row{}`, `container(colour|ThemeStyle, spoiler){}`, `section(accessory){}`, `gallery{}`, `divider`, `ephemeral`, `mentions`, `add` | — |
| `MessageComponent` sealed: `Text`, `Row`, `Container`, `Section`, `Divider`, `MediaGallery`; `Interactive` sealed: `Button`, `SelectMenu`, `EntitySelect`; `SectionAccessory` sealed: `Button`, `Thumbnail`; `ButtonStyle`, `SelectOption`, `EntityKind`, `MediaItem`, `rgbOf(hex)` | — |
| `discordCommand(name){}` **DSL** → `DiscordCommandSpec` (`signature()`, `signatureOf`, `at`, `flatten`, `usage`, `MAX_DEPTH`); `DiscordCommandBuilder`: `description`, `defaultPermission`, `guildOnly`, `ephemeral`, `sub{}`, `child`, `executes{}`; options `string(choices|autocomplete)`, `integer`, `number`, `boolean`, `user`, `role`, `channel`, `mentionable`, `attachment` | — |
| `Interaction` **CALL**: `actor`, `guild`, `channel`, `acknowledged`, suspend `reply(message)`, `replyModal`, `defer(ephemeral)` → `DeferredInteraction` (`respond`, `editOriginal`); `DiscordCommandContext` (`path`, `option(name): OptionValue`); `ComponentInteraction` (`componentId`, `selected`, `messageId`); `ModalInteraction` (`modalId`, `field`); ext `answer(text, ephemeral)` | — |
| `InteractionRouter(logger)` | `register(spec)`, `onComponent(ns){}`, `onModal(ns){}`, `dispatch` ×3, `resolve`, `registered` |
| `Modal`/`ModalBuilder`/`TextField`/`TextFieldStyle` | — |
| `DiscordWebhook(url, maxRetries, clock, httpClient)` : SculkHandle | suspend `send(message, username, avatar)`, `sendText(...)` |

---

## 3. Feature catalogue

| Capability | Implementation | Verdict |
|---|---|---|
| `SculkResult` with sentence messages, `catching`, shared `ok()` | `sculk-common/.../SculkResult.kt` | **keep-behaviour** — the ergonomics are right; Sculk 6 may reshape but must keep message-is-a-sentence rule |
| `SculkHandle.all` reverse-order close with suppressed exceptions | `SculkHandle.kt:28-45` | **keep-behaviour** |
| Scheduler contract with abstract entity/location overloads, `runNow` inline fast path | `scheduler/SculkScheduler.kt` | **keep-behaviour** |
| `PaperScheduler` one code path over region API, normalises `delay<=0`, `period<1`, retired callback runs the task | `sculk-platform/.../PaperScheduler.kt:30-145` | **keep-behaviour** |
| Coroutine scope bound to plugin, main/async dispatchers over scheduler, dispatch never throws | `coroutine/SculkCoroutineScope.kt`, `SculkDispatchers.kt:30-44` | **redesign** — default dispatcher is async (`:33`), which makes command executors off-thread by default (see §7); make the default explicit |
| Cron / repeating / delayed / debounce / throttle tasks | `task/SculkTasks.kt`, `CronExpression.kt` | **keep-behaviour** (fix: `debounce`/`throttle` closures are not thread-safe `:94-116`; no entity/location variants) |
| `BatchTicker` cursor over a changing collection | `task/BatchTicker.kt` | **keep-behaviour** |
| `DirectoryWatcher` daemon thread, per-file handlers, caller-supplied dispatch | `io/DirectoryWatcher.kt` | **redesign** — no debounce: editors emit several `ENTRY_MODIFY` per save so one edit reloads 2–3× (`:41-56`); undocumented (`docs/config/hot-reload.mdx` never mentions `watch`) |
| Theme expansion before parse (named styles, gradients, shader) with per-template cache | `text/SculkTheme.kt:44-57` | **keep-behaviour** |
| Trust boundary: templates parsed, values via `Placeholder.unparsed`; placeholder/theme-name collision check | `text/SculkMessages.kt:49-63` | **keep-behaviour** (and make the docs obey it — §8) |
| Legacy `&c`/`§c` → MiniMessage on templates only, not inside tags | `text/LegacyCodes.kt` | **keep-behaviour** |
| Glyph-width measurement + centring, bold-aware per component | `text/MinecraftFont.kt` | **keep-behaviour** (ASCII-only table; CJK wrong — documented) |
| Per-player YAML bundles, two-form plural, missing key renders key | `text/SculkBundles.kt` | **keep-behaviour** |
| Config: defaults are the file, kebab-case, append-only merge, comments preserved, env `${VAR:-}` substitution, quoting of placeholders, `@Min/@Max/@NotEmpty` path-reporting, `@ConfigFile(revision)` backup+regenerate, versioned migrations on raw document, never writing a merge that does not parse | `config/SculkConfig.kt`, `ConfigMerge.kt`, `EnvSubstitution.kt`, `ConfigValidation.kt`, `ConfigMigration.kt` | **keep-behaviour** — this is the most battle-tested module (5.2 fixed three second-boot bugs here) |
| Config files with env placeholders never rewritten (secrets not baked) | `SculkConfig.kt:214-225` | **keep-behaviour** |
| Registry lookups with hyphen/underscore normalisation, version adapters, `validateKeys` report | `series/SculkSeries.kt`, `registry/SculkRegistry.kt` | **keep-behaviour**; **redesign** the forever-memoised miss (`SculkRegistry.kt:24-30`) |
| `ItemBuilder` over data components with per-version reflection probes and `ItemMeta` fallback | `items/ItemBuilder.kt`, `ItemCompat.kt`, `ItemWriters.kt` | **keep-behaviour** |
| `ItemDescriptor` `@Serializable` config shape round-tripping through kaml | `items/ItemDescriptor.kt` | **keep-behaviour** |
| Custom items `nexo:`/`oraxen:`/`itemsadder:` by reflection via owning plugin's loader, warn-once per key | `integrations/CustomItems.kt`, `items/ItemResolution.kt` | **keep-behaviour** (licence rationale `CustomItems.kt:9-14`) |
| `giveOrDrop` with disjoint given/dropped | `items/InventoryDelivery.kt` | **keep-behaviour** |
| Command tree as data, no Brigadier/Bukkit in spec; one adapter; suspend executors; middleware; cooldowns; generated permission-filtered `/help`; overridable `CommandText` | `command/CommandSpec.kt`, `brigadier/BrigadierAdapter.kt`, `HelpCommand.kt`, `CommandText.kt` | **keep-behaviour** |
| All Brigadier arguments typed as `word()`/`greedyString()`; parsing in Sculk; suggestions from lambda | `BrigadierAdapter.kt:80-81` | **redesign** — no native client-side validation for ints/players/etc.; README "real client-side completion" is suggestions only |
| Menus: inventory-keyed sessions, cancel-before-dispatch, interactive slots, drag refusal, quit cleanup, pending switch without close (cursor fix), theme applied at open | `gui/MenuRegistry.kt`, `MenuListener.kt`, `GuiSession.kt`, `GuiItem.kt` | **keep-behaviour** |
| Slot animations via `launchMain` + `delay` loops | `gui/GuiSession.kt:97-112` | **redesign** — global-region dispatch touches a player inventory; should be entity-scheduled |
| HUD: one driver task, flicker-free sidebar via team prefixes, redraw rules as pure functions, action-bar priority arbitration with expiry, placeholders as unparsed values | `hud/HudService.kt`, `Sidebar.kt`, `HudRules.kt`, `ActionBar.kt` | **keep-behaviour**; **redesign** Folia thread model (§7) |
| Sidebar replaces the player's scoreboard; `hide()` sets `mainScoreboard`, not the previous one | `hud/Sidebar.kt:49-61` | **redesign** — conflicts with other scoreboard plugins; KDoc "restores previous" is false |
| Data: Hikari pool, driver named explicitly, boot-time connectivity check, SQLite pool=1, MySQL `allowPublicKeyRetrieval` when no TLS, SQLite fallback on bad `storage.yml` | `data/SculkData.kt:77-152`, `platform/SculkPlatform.kt:107-115` | **keep-behaviour** |
| Repositories from `SerialDescriptor` (no kotlin-reflect), quoted identifiers, real upserts, additive migration with rename warning, index existence check (no dialect branch), every failure logged | `data/JdbcRepository.kt`, `RowCodec.kt`, `TableSchema.kt`, `SchemaMigrator.kt` | **keep-behaviour** |
| Query DSL: OR, IN, multi-order, take/skip, `@Column` property↔column mapping | `data/Query.kt`, `TableSchema.kt` | **keep-behaviour** |
| Caffeine cache with negative caching | `data/SculkCache.kt` | **keep-behaviour** |
| Redis/Valkey shared cache via Lettuce, degrades to repository, outage logged once | `data/RedisCache.kt` | **keep-behaviour** |
| `WriteBehindStore` coalescing dirty set, `flushBlocking` for shutdown | `data/WriteBehindStore.kt` | **keep-behaviour** |
| Packet backend discovery via ServiceLoader on plugin + own loader; two-sentence missing-backend diagnosis | `packets/SculkPacketServices.kt` | **keep-behaviour** |
| `PacketGuard`: handler exceptions swallowed, packet left alone, log rate-limited | `packets/PacketGuard.kt` | **keep-behaviour**; **fix** ProtocolLib backend is not guarded (§7) |
| Client blocks: set/setAll (chunk-section packed)/reset/preview; dig/use interception with sequence acknowledgement | `packets/HighLevelPackets.kt`, `BlockDigs.kt` | **keep-behaviour** |
| Virtual entities (text displays) behind neutral contract; `UnavailableVirtualEntityService` instead of null | `packets/VirtualEntityService.kt` | **keep-behaviour** |
| Packet debug sessions | `packets/PacketDebugService` | **drop** unless a consumer uses it — no docs beyond `packets/overview`; low value for the surface it adds |
| Holograms bucketed by chunk, text re-sent only on change | `visual/HologramService.kt` | **keep-behaviour** |
| Nametags ridden as passenger, slow/fast passes, `broken` latch | `visual/Nametags.kt` | **keep-behaviour** |
| Particles/sounds builders + config descriptors; timelines/sequences | `visual/*` | **keep-behaviour** |
| Platform: lazy subsystems, `…Opened` flags so shutdown never opens a pool, scope closed first, menu listener unconditional | `platform/SculkPlatform.kt` | **keep-behaviour** |
| `SculkPlugin`: final `onEnable/onDisable`, commands registered in `LifecycleEvents.COMMANDS` before `setup()`, `+spec`, `keep()`, banner off/own art, `commandText()` | `platform/SculkPlugin.kt` | **keep-behaviour** |
| `ServiceRegistry` type-keyed, duplicate throws, reverse close | `platform/ServiceRegistry.kt` | **keep-behaviour** |
| `SculkEventBus` typed listen with handle, copy-on-write list | `platform/SculkEventBus.kt` | **keep-behaviour** |
| Rebuild-over-running-jar warning | `platform/RebuildWarning.kt` | **keep-behaviour** |
| Integrations by reflection with `SculkResult` presence checks; LuckPerms suspend reads via `loadUser().await()` | `integrations/SculkIntegrations.kt` | **keep-behaviour**; **redesign** uncached `getMethod` per call (§7) |
| Discord: backend-neutral gateway, every call suspends + `SculkResult`, `Mentions.None` default, `ComponentId` budget, typed defer, collectors, per-channel serialised sends with one retry, reconnect owned once, webhooks with `Retry-After` | `sculk-discord/*`, `sculk-discord-jda/JdaGateway.kt`, `ChannelSends.kt`, `webhook/DiscordWebhook.kt` | **keep-behaviour** |
| Discord Components V2 (Container/Section/Thumbnail/MediaGallery/EntitySelect), nested-container rejected at build | `discord/message/MessageComponent.kt` | **keep-behaviour** |
| Webhook fallback rendering (link buttons → markdown) | `discord/webhook/WebhookRendering.kt` | **keep-behaviour** |
| Build-time ban on Bukkit/Paper/JDA references in `sculk-discord`; Bukkit/Ktor/Javalin in `sculk-web` | `sculk-discord/build.gradle.kts:44-91`, `sculk-web/build.gradle.kts:13-65` | **keep-behaviour** |
| Embedded HTTP: routes immutable after start, loopback default, trusted-proxy gated `X-Forwarded-For`, body cap, CIO (no second Netty), `LinkageError` explained | `web/SculkWeb.kt`, `web/ktor/KtorWebServer.kt` | **keep-behaviour** (and document it) |
| Stability markers enforced by class-file scan of `.api` dumps | `platform/.../StabilityMarkerTest.kt` | **keep-behaviour** |
| Real-database CI job with skip-is-failure assertion | `.github/workflows/ci.yml:49-139` | **keep-behaviour** |
| Startup banner | `platform/SculkBanner.kt` | **keep-behaviour** (now opt-out) |
| `SculkBlocksKt` sculk-sensor helpers | `series/SculkBlocks.kt` | **drop** — brand pun, experimental, unused by any module or example |

---

## 4. Behaviours and rules worth preserving (with enforcement point)

| Rule | Where stated | Where enforced |
|---|---|---|
| Templates trusted, values `Placeholder.unparsed`; never `replace()` a value into a template | `CLAUDE.md` rule 3; `SculkMessages.kt:6-9`; `LegacyCodes.kt:14-18`; `HudService.kt:153-161` | code review only; `SculkMessagesTest` pins behaviour. **Not enforced in docs** (§8) |
| `SculkResult` for fallible I/O and user input; throw only for wiring bugs; never a `SculkResult` that cannot fail | `CLAUDE.md` rule 4; `SculkResult.kt:4-12`; `CommandContext.kt:22` (`argument` throws on undeclared name) | convention |
| Every failure logged before being returned (data layer) | `JdbcRepository.kt:30-45` | `SchemaAndLoggingTest.kt` |
| Scheduler region overloads abstract; implementation must think about regions | `SculkScheduler.kt:7-10` | compiler (`abstract`) |
| `delay<=0` = next tick, `period<1` floored, retired callback runs the task | `PaperScheduler.kt:17-27,145` | `PaperSchedulerTest.kt` (MockBukkit) |
| `CoroutineDispatcher.dispatch` must never throw (runs inline on rejection) | `SculkDispatchers.kt:30-44` | code |
| Every interface a consumer *calls* ships a fake; fakes refuse what they cannot model | `CLAUDE.md`; `FakeRepository.kt:17-22`; `FakeDiscordGateway.kt:20-23`; `FakeWebServer.kt:5-9` | `java-test-fixtures` on every module; published |
| Real engines over mocks (SQLite, H2 MySQL+PG modes, real MySQL/MariaDB/PG in CI) | `sculk-data/build.gradle.kts:24-32`; `SqlEngineContract.kt:4-17` | `ci.yml` `databases` job + "Assert no engine was skipped" |
| Committed `.api` dumps, `apiCheck` in CI, `@SculkInternal` as non-public marker | `build.gradle.kts:16-32` | `ci.yml:30` |
| Every published type carries a stability marker | `CLAUDE.md` rule 6 | `StabilityMarkerTest.kt` (ASM over dumps) |
| `-opt-in=SculkInternal` once in build-logic, `allWarningsAsErrors` for main, ktlint 140 cols | `sculk.module.gradle.kts:34-41`; `.editorconfig` | Gradle |
| `sculk-text` ⇏ `sculk-config` (kaml used directly) | `sculk-text/build.gradle.kts:14-17`; `DirectoryWatcher.kt:6-8` | build graph only (no test) |
| `sculk-visual` ⇏ packet backend | `sculk-visual/build.gradle.kts:12-14`; `VirtualEntityService.kt:40-43` | build graph only |
| `sculk-discord` has no Bukkit/Paper/JDA; `sculk-web` has no Bukkit/Paper/Ktor/Javalin | build scripts | `forbiddenReferences` task scanning compiled classes, wired to `check` |
| GPL/LGPL/proprietary deps `compileOnly` and confined to one backend module; nothing bundled | `README.md:109-110`; `libs.versions.toml` comments; `CustomItems.kt:9-14` | review; BOM generated from subprojects |
| Kotlin-only API: no `@Jvm*`, no `CompletableFuture` except `await()` | `CLAUDE.md` rule 1; `Futures.kt` | `grep` in release checklist; **PR template contradicts it** (`.github/pull_request_template.md:26-27` still demands Java compat and "no generics") |
| Config rewrite may only append; never write a merge that fails to parse | `ConfigMerge.kt:8-29`; `SculkConfig.kt:218-226` | `RewriteSafetyTest`, `ConfigMergeSequenceTest`, `ConfigMergeCommentsTest` |
| Schema migration additive only; rename warned | `SchemaMigrator.kt:4-11,20-48` | `SqlEngineContract` on every engine |
| Menus: cancel first then dispatch; listener always registered; sessions keyed by inventory | `MenuListener.kt:12-14`; `SculkPlatform.kt:160-163`; `MenuRegistry.kt:13-21` | `MenuListenerTest`, `MenuRoutingTest` (MockBukkit) |
| HUD is one task regardless of player count | `HudService.kt:115-116` | `HudServiceTest` asserts `taskCount` |
| Subsystems lazy; shutdown never opens one | `SculkPlatform.kt:30-41,166-176` | `opened` set asserted in tests |
| Backend discovery searches the plugin's loader, not TCCL | `SculkPacketServices.kt:16-32`; `SculkDiscord.kt:18-23`; `SculkWeb.kt:176-205` | `SculkPacketServicesTest`, `SculkDiscordTest`, `SculkWebDiscoveryTest` |
| Test names are sentences | `CLAUDE.md` | convention, consistently followed |
| Comments say why + consequence of the alternative | `CLAUDE.md` | convention, consistently followed — the KDoc is the best changelog the project has |

---

## 5. Integrations

| Integration | Wiring | Presence check | When absent |
|---|---|---|---|
| PlaceholderAPI | `Class.forName("me.clip…PlaceholderAPI").getMethod("setPlaceholders")`, Method cached in `Reflect` (`SculkIntegrations.kt:62-75,92-99`) | `pluginManager.getPlugin("PlaceholderAPI")?.isEnabled` | `placeholderApi()` → `SculkResult.failure`; `parse()` returns text unchanged on any throw |
| Vault | `Bukkit.servicesManager.getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"))`; per-call `economy.javaClass.getMethod(...)` **uncached** (`:109,117`) | plugin enabled + provider registered | failure naming "no economy provider" |
| LuckPerms | `Class.forName` on 6 API interfaces (`:224-229`, re-resolved per access — `get()` properties), `getMethod` per call; `loadUser` future awaited via `CompletableFuture.await()` (`:205-210`) | plugin enabled | failure; `NoClassDefFoundError` impossible since nothing links | 
| Nexo / Oraxen / ItemsAdder | `owner.javaClass.classLoader.loadClass(className)` (owning plugin's loader), Method cached (`CustomItems.kt:118-131`) | `Bukkit.getServer().pluginManager.getPlugin(name)?.isEnabled`, guarded for no-server | three distinct failure sentences; warn-once per key |
| PacketEvents | `compileOnly`; `META-INF/services` provider; `Class.forName(..., false, loader)` + `PacketEvents.getAPI() != null` (`PacketEventsPacketServiceProvider.kt:62-67`) | both class and initialised API | `SculkPlatform.packets` is `Failure`; holograms/nametags fall back to `UnavailableVirtualEntityService`; banner omits "Packets" row |
| ProtocolLib | same shape (`ProtocolLibPacketServiceProvider.kt:33-40`); PacketEvents preferred when both present (`SculkPacketServices.kt:64-67`) | class + `getProtocolManager() != null` | as above |
| JDA | `compileOnly`; ServiceLoader provider `JdaGatewayProvider` (`@SculkInternal`); `Class.forName("net.dv8tion.jda.api.JDABuilder", false, loader)` (`JdaGatewayProvider.kt:26-27`); consumer supplies JDA via `libraries:` or runtime dep | class presence | `SculkDiscord.create` fails with the `libraries:` hint; `SculkDiscord.disabled(reason)` gateway refuses every call by name |
| Ktor | `compileOnly` core+CIO; ServiceLoader `KtorWebProvider`; `LinkageError` explained (`KtorWebServer.kt:62-75`) | class presence | `SculkWeb.create` fails; `SculkWeb.disabled(reason)` |
| Folia | `Class.forName("io.papermc.paper.threadedregions.RegionizedServer")` once (`PaperScheduler.kt:155-157`) | — | Paper path: `isPrimaryThread` for `ownsGlobalThread` |

No integration uses `java.lang.invoke.MethodHandle` despite `CHANGELOG.md:29` saying "method handles"; it is `java.lang.reflect.Method`.

---

## 6. Tests

| Module | `@Test` | Tooling |
|---|---|---|
| common | 41 | JUnit, coroutines-test, mockito (`SculkCoroutineScopeTest`); `FakeSchedulerTest` tests the fixture itself |
| text | 47 | real MiniMessage/Adventure |
| config | 39 | real kaml against temp dirs; `GeneratedFileFormatTest`, `RewriteSafetyTest` |
| series | 17 | mockito |
| items | 51 | **MockBukkit** (4 files); kaml round-trip; `ItemCompatTest` pins reflection probes against compiled API |
| integrations | 12 | mockito |
| commands | 32 | pure spec tests; no Brigadier test (adapter untested except via examples compiling) |
| gui | 51 | **MockBukkit** (4 files) + `FakeScheduler` |
| hud | 39 | `FakeScheduler`, mockito players |
| data | 66 | SQLite (real), H2 MySQL + PG modes (real), stub `RedisBackend`; env-gated `MySqlIntegrationTest` (2), `MariaDbIntegrationTest`, `PostgresIntegrationTest` via `@EnabledIfEnvironmentVariable` |
| packets-api | 20 | `FakeScheduler`, mockito; `PacketGuardTest` |
| packets-packetevents / -protocollib | 3 / 2 | provider availability only; no packet round-trip |
| visual | 19 | `FakeScheduler`, `FakeVirtualEntityService` |
| platform | 37 | **MockBukkit** (`PaperSchedulerTest`, `SculkEventBusTest`); `StabilityMarkerTest` (ASM); banner, registry |
| web / web-ktor | 12 / 15 | real Ktor CIO bound to port 0 (`KtorWebServerTest`), discovery |
| discord / discord-jda | 146 / 35 | fakes; JDA rendering/commands against real JDA types, no network |
| examples/discord-bot | 7 | `FakeDiscordGateway` |

Total 684 (+7). No JMH benchmarks exist (catalog entries only). No end-to-end server test; `examples/basic-plugin` has a `runServer` task as the manual gate (`examples/basic-plugin/build.gradle.kts:24-31`).

CI (`.github/workflows/ci.yml`): `build` job = compile + ktlint + all embedded tests + `apiCheck`; `databases` job = `mysql:8`, `mariadb:11`, `postgres:16` service containers, runs `:sculk-data:test`, then fails if any of the three integration suites is missing or skipped; `docs` job = `bun run build` (MDX compiles, links resolve — snippets are **not** compiled). `docs.yml` deploys to Cloudflare Workers on `main`. `discord.yml` posts pushes to a webhook via a pinned third-party action.

---

## 7. Known problems

**Markers.** Zero `TODO`/`FIXME`/`HACK`/`@Deprecated` in source. `@SculkExperimental`: 12 sites, all in `sculk-integrations` (5) and `series/SculkBlocks.kt` (7). `docs/advanced/api-stability.mdx:16-17` claims `AnimationTimeline` is experimental; it is stable.

**Trust-boundary violations in the project's own words.** 15 docs snippets interpolate player-controlled or data-controlled values into templates: `docs/core/events.mdx:15` (`"<green>Welcome, <white>${event.player.name}"`), `gui/overview.mdx:78`, `items/overview.mdx:67`, `visual/nametags.mdx:11-12`, `recipes/economy-plugin.mdx:120`, `data/overview.mdx:163,168,184`, `introduction/overview.mdx:39`, `commands/subcommands.mdx:77`, `advanced/folia.mdx:56`, `advanced/coroutines.mdx:16,30`, `advanced/performance.mdx:19`, `core/scheduler.mdx:94`; plus KDoc `SculkEventBus.kt:24`, `GuiItem.kt:219`, `SculkCoroutineScope.kt:16`, and `examples/server-menu/.../ServerMenuPlugin.kt:46-47`. The injection hole CHANGELOG 5.0 closed in code is taught by the docs.

**Command executors run off-thread by default.** `BrigadierAdapter.dispatch` does `scope.launch { executor(context) }` (`BrigadierAdapter.kt:134`), and the scope's default dispatcher is `dispatchers.async` (`SculkCoroutineScope.kt:33`). Any Bukkit mutation inside `player {}` is a thread violation on Paper and Folia alike; `CommandContext` offers no sync hop; docs say only "launched in the plugin scope" (`commands/overview.mdx:169`). `MenuRegistry.open` survives because it hops via `runNow(player)`. Also `catch (error: Exception)` swallows `CancellationException` and uses `printStackTrace()` (`:139-144`).

**PacketGuard coverage.** `PacketGuard.kt:11-13` says "every handler call site in every backend goes through here"; only the PacketEvents backend does (`PacketEventsPacketServiceProvider.kt:95,130,160,183,190`). `ProtocolLibPacketServiceProvider.kt:55-63` invokes handlers raw.

**Folia gaps** (region-correct: `PaperScheduler`, `MenuRegistry.open/applyPendingSwitch`, `ClientBlockService` via `runNow(player|location)`):
- `HudService.refresh` is one global-region task that iterates every online player and mutates their scoreboard / sends packets (`HudService.kt:131-150`); `Sidebar` writes teams/scores from that thread.
- `HologramService.reconcile` and `Nametags.refresh` read `player.location` for all players from the global task (`HologramService.kt:69-77`, `Nametags.kt:52-54`).
- `GuiSession.startAnimations` renders inventory slots from `launchMain` (global) (`GuiSession.kt:97-112`).
- `SculkTasks` has no entity/location variants; `repeating`/`delayed` are global-only.
- No example `paper-plugin.yml` declares `folia-supported: true` though `docs/advanced/folia.mdx:28-33` says it is required; Folia support is therefore unproven on a real Folia boot.
- `ServerFlavour.isFolia` is only used for `ownsGlobalThread` (`PaperScheduler.kt:105`).

**Blocking on the calling thread.** `SculkPlatform.data` lazy opens Hikari and does `connection.isValid(5)` on first access (`SculkData.kt:107`), i.e. up to 5 s on main if first touched from `setup()`; `SculkData.repository()` runs DDL synchronously on first call (`SculkData.kt:32-36`); `SculkConfig.load/reload` and `SculkBundles.reload` do synchronous file IO wherever called (`reload()` from a command executor lands on the async dispatcher, which is fine, but `reloadAll()` from `watch()` with the default `Runnable::run` runs on the watcher thread and then notifies listeners there — `SculkConfig.kt:181-182`); `WriteBehindStore.flushBlocking` is `runBlocking` (documented shutdown-only, `WriteBehindStore.kt:77`). No `GlobalScope` anywhere.

**Reflection in hot paths.** `VaultEconomyIntegration.balance/call` resolve `getMethod` per call (`SculkIntegrations.kt:109,117`); `LuckPermsIntegration` re-runs `Class.forName` ×6 and `getMethod` on every `groups`/`hasPermission` (`:158-229`) — only `Reflect.method` users are cached. PlaceholderAPI (per sidebar line per player per 5 ticks) is cached. `ItemCompat` probes are `lazy` (good).

**Design weaknesses revealed by CHANGELOG "Fixed"/"Security" entries.**
| Version | Entry | Lesson |
|---|---|---|
| 5.0 | Two modules substituted values into templates with `replace()` | one renderer, one entry point (`CHANGELOG.md:277-282`) |
| 5.0 | Unguarded packet handlers kicked players | guard at every call site — still incomplete (ProtocolLib) |
| 5.0 | Config rewrite destroyed unknown keys and owner comments | append-only merge |
| 5.0 | Global `GuiRegistry` object + opt-in listener → lootable inventories | instance registry, unconditional listener |
| 5.0 | `player{}`+`console{}` never reached console | executor selection bug class |
| 5.0 | `REPLACE INTO` wiped columns outside the statement | real upserts |
| 5.2 | `${VAR:-}` default rendered unquoted → null on second boot | quote placeholders |
| 5.2 | `@Comment("a\nb")` corrupted the file "documented since 4.x without ever working" | docs promised untested behaviour |
| 5.2 | `CREATE INDEX IF NOT EXISTS` fails on MySQL; H2 MySQL mode passed | embedded engine ≠ production engine → real-DB CI |
| 5.2 | Column naming change (snake_case → verbatim) emptied reads silently | additive migration cannot express renames |
| 5.2 | GUI items rendered against `SculkTheme.EMPTY`; `stack()` dropped name/lore | render late, at open |
| 5.2 | Boss bars survived `close()`/`forget()` | close must undo what show did |
| 5.2 | `UNBREAKABLE` `NoSuchFieldError` on 1.21.4 → every config item failed | probe, don't compare versions |
| 5.2 | `DiscordCommandSpec.ephemeral` written, never read; nested Container failed at send; member cache cached ~1 member; two reconnect loops; sends raced buckets | untested wiring between spec and backend |
| 5.2 | Docs taught `@param:Min`, `@PrimaryKey`, `limit()`, `data.cached(delegate=)`, `commands.registerAll`, `packetsResult`, `SculkPlugin({...})` | docs snippets are not compiled |
| post-5.2 | `CommandText` taken by the adapter since written and "nothing ever passed one" (`SculkPlugin.kt:72-78`); menu switch never reopened (#93); cursor jump (#95) | parameters with no caller; no server-side GUI test |

**Stale project text.** `CONTRIBUTING.md:369-393` lists 4.x modules (`sculk-adventure`, `sculk-events`, `sculk-content`, `benchmarks/`); `.github/pull_request_template.md:26-27` requires Java builders and forbids generics; `GuiSession.kt:7` KDoc cites `Gui.openFor` (removed in 5.0); `Sidebar.hide` KDoc "restores the player's previous scoreboard" sets `mainScoreboard` (`Sidebar.kt:56-61`); `docs/README.md` is the Starlight starter boilerplate.

**Other.** Thread-safety: `SculkTasks.debounce/throttle` closures (`SculkTasks.kt:94-116`), `HudService.task/tick`, `KtorWebServer.running` are unsynchronised. `SculkRegistry` memoises misses forever (`SculkRegistry.kt:24-30`) — documented as the reason custom items bypass it. `HelpCommand` is appended to `declared` *after* `setup()` but the `COMMANDS` handler reads the list at lifecycle time, so ordering works only because the event fires after `onEnable` (`SculkPlugin.kt:131-150`). `SculkMessages.render` `require`s a placeholder name not collide with a theme name — a runtime throw from user data shape, not a `SculkResult` (`SculkMessages.kt:52-54`).

---

## 8. Docs inventory

Site: Astro 7 + Starlight 0.41, Bun, Cloudflare Workers (`docs/wrangler.toml`). Sidebar is **hand-maintained** in `docs/astro.config.mjs:26-171` (every entry explicit; no autogenerate). 62 pages:

| Section | Pages |
|---|---|
| Introduction | overview, modules, architecture |
| Getting Started | installation, first-plugin |
| Core | core/scheduler, core/events, tasks/overview, core/testing |
| Text | overview, theme, placeholders, font, localisation |
| Commands | overview, subcommands |
| Menus | gui/overview, pagination, animations |
| HUD | overview, sidebar, action-bar, placeholders |
| Config | overview, hot-reload |
| Data | overview, query, caching, backends |
| Items | overview, components, persistent-data, config-items |
| Visual | particles-sounds, timelines, holograms, nametags |
| Discord | overview, messages, commands, interactions, guilds |
| Series | overview, registries |
| Packets | overview, client-blocks, virtual-entities, packetevents, protocollib, performance |
| Platform | platform/services, integrations/overview |
| Recipes | economy-plugin, server-menu, staff-tools |
| Advanced | whats-new-in-5, coroutines, folia, performance, api-stability, troubleshooting |
| (root) | index |

Not in the sidebar / not documented: `sculk-web`, `sculk-web-ktor`, `SculkConfig.watch()`, `BatchTicker`, `WriteBehindStore`, `PacketDebugService`, `RebuildWarning`, `hud-showcase` and `visual-showcase` examples (no recipe pages).

Docs promises the code does not keep:
| Page | Claim | Reality |
|---|---|---|
| `introduction/modules.mdx:6` | "sixteen modules" | 20 directories; web modules absent from table |
| `integrations/overview.mdx:9-20` | `sculk.integrations.placeholderApi()`, `player.sendMini(...)` | `SculkPlatform` has no `integrations` property; `SculkIntegrations(plugin)` is constructed by hand; `sendMini` does not exist |
| `config/hot-reload.mdx:8,32` | `sculk.config.reload()`, `onReload { }` untyped; "`configVersion` field" | API is `reload<T>()`/`reloadAll()`, `onReload<T>{}`; versioning is `@ConfigFile(revision)` + `config-version` key written by migrations |
| `advanced/api-stability.mdx:10,16,59` | `command(...)` returns `CommandBuilder`; `AnimationTimeline` experimental; `SculkCommandBridge` | returns `CommandSpec`; stable; type does not exist |
| `advanced/folia.mdx:6` | "scheduler adapters for Folia and Canvas" | one adapter; nothing Canvas-specific; no example declares `folia-supported` |
| `commands/overview.mdx:169` | handlers "launched in the plugin scope" | scope default is async; docs never say executors leave the main thread |
| `README.md:17-18` | "real client-side completion" | all arguments are Brigadier strings; completion is server-supplied suggestions |
| `core/testing.mdx` | lists `FakeScheduler`, `FakeVirtualEntityService` only | `FakeRepository`, `FakeHologram`, `FakeDiscordGateway`, `FakeInteractions`, `FakeWebServer` also ship |
| `CHANGELOG.md:29` | "method handles" | `java.lang.reflect.Method` |
| 15 snippets (§7) | string interpolation into templates | violates the rule the same site documents on `text/placeholders` |

CI builds the docs but does not compile snippets, which is how every row above shipped.

---

## 9. Verdict

### What Sculk 5 got right
- **Data-first cores.** `CommandSpec`, `DiscordMessage`, `DiscordCommandSpec`, `Gui`, `ItemDescriptor`, `TableSchema`, `Query/Condition` are plain data with one adapter each; 684 tests run with no server because of it (`CommandSpec.kt:14-20`).
- **One renderer, one boundary.** `SculkMessages` is the only String→Component path, values always `Placeholder.unparsed`, legacy codes rewritten on templates only (`SculkMessages.kt:49-63`, `LegacyCodes.kt:14-18`).
- **Failures are sentences and are logged.** `SculkResult.catching("describe")`, three-way diagnoses (`CustomItems.kt:68-71`, `SculkPacketServices.kt:84-95`, `SculkDiscord.kt:55-71`, `KtorWebServer.kt:62-75`).
- **Backend neutrality enforced by the build**, not by discipline (`forbiddenReferences` in discord/web; `VirtualEntityService` in packets-api; `.api` dumps + `StabilityMarkerTest`).
- **Licence hygiene is structural**: GPL/LGPL/proprietary only ever `compileOnly`, one backend per module, reflection for unlicensed plugins, BOM generated.
- **Config append-only merge, env placeholders never baked, revision backups** — the module with the most second-boot scars and the most tests per line (`ConfigMerge.kt:8-29`).
- **Real engines in CI with skip-as-failure** (`ci.yml:49-139`) after H2 lied once.
- **Fakes as a product** (`java-test-fixtures` on 7 modules; `FakeRepository` refuses to guess without `columnsOf`).
- **Platform lifecycle**: lazy subsystems, `…Opened` flags, scope closed first, final `onEnable/onDisable`, commands registered in the lifecycle event before `setup()`.
- **KDoc that records why and what the alternative cost** — the most reliable history in the repo.

### What Sculk 5 got wrong
- **Threading is implicit.** The scope defaults to async (`SculkCoroutineScope.kt:33`), so every command executor runs off-thread with no signal in the API or docs; HUD, holograms, nametags and GUI animations run player work from the global region task. Folia support is asserted in docs, never booted (no `folia-supported` anywhere).
- **The trust boundary lived in code, not in culture.** 15 docs snippets and three KDoc examples interpolate values into templates — the exact hole CHANGELOG 5.0 calls a security fix.
- **Guards were partial.** `PacketGuard` claims every backend; ProtocolLib is unguarded (`ProtocolLibPacketServiceProvider.kt:55-63`).
- **Docs were never compiled**, so they drifted on every release (CHANGELOG 5.2 "Documentation"; §8 table); sidebar is hand-maintained; two modules shipped with no page, no CHANGELOG line and no mention in `CLAUDE.md`.
- **Reflection adapters are untyped and partly uncached** (Vault/LuckPerms `getMethod` per call); experimental forever.
- **Brigadier integration is shallow**: every argument is a string, so clients get no native validation and suggestions are the only "completion".
- **Scoreboard ownership**: `Sidebar` replaces the player's scoreboard and cannot restore it.
- **Repo hygiene lagged the code**: `CONTRIBUTING.md`, PR template, `docs/README.md`, `.idea` benchmark module, unused catalog entries (`snakeyaml`, `jmh`), `benchmarks` command that does not exist.
- **Residual mutable-global style** where it was cheap: `SculkSeries`/`CustomItems`/`LuckPermsIntegration` objects; `SculkRegistry` memoises misses; non-thread-safe `debounce/throttle`.
- **Scale**: `sculk-discord` is 2 619 main + 1 532 backend LOC with a 1 134-line `.api` — the largest surface in the framework for a module most plugins never load.
