# Changelog

All notable changes to Sculk Studio are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [5.2.0] — "Bridged, wired" — 2026-08-13

### Changed

- **`greedy(name)` is now `greedy(name, optional = false)`.** Source-compatible, but the old
  single-argument method is gone from the bytecode, so a consumer compiled against 5.1.0 and run
  against this needs a recompile. 5.1.0 never reached jitpack, so in practice nothing is affected —
  recorded because the API dump changed shape, not because anyone is expected to be caught by it.

### Added

- **`greedy(name, optional)`** — the trailing-string argument was the only one of the fourteen that
  could not be optional, which made the ordinary chat-command shape (`/sc` toggles, `/sc hello` sends)
  impossible to express as one node.
- **`LuckPermsIntegration.groups(uuid)` and `hasPermission(uuid, node)`** — suspending reads that load
  the user when they are not cached, so the answer is correct for someone who is offline. `groups`
  returns what LuckPerms itself would say the player has, with inheritance, contexts, temporary grants
  and negations already applied; reading `InheritanceNode`s off a user and taking their names looks
  equivalent and ignores all four. Both suspend rather than blocking, because LuckPerms may be backed
  by a remote database and a `.get()` from a join listener stalls the server tick for as long as it
  takes to answer. Method handles are now resolved from the declaring interfaces rather than from the
  implementation object, whose class is not public — invoking a method resolved that way throws
  `IllegalAccessException` on some JVMs.

## [5.1.0] — "Bridged" — 2026-08-12

### Added

- **`DiscordRole` and role reads on `GuildService`** — `role(guild, id)` and `roles(guild)`, plus
  `DiscordActor.highestColoredRole(guildRoles)`. `DiscordActor.roles` carried ids and nothing else, so
  a bridge that wanted to tint a relayed line the way Discord tints the sender's name could not: the
  colour, name and position were all discarded in translation. `roles(guild)` returns the whole list
  highest-first so a sync over thousands of members fetches once rather than per role per member.
- **`GuildService.members(guild, users)`** — bulk member lookup, batched to Discord's hundred-per-request
  limit. The single-member call in a loop is one round trip per member, which over a linked-account
  table is thousands of requests and slow enough that a reconcile can still be running when the next
  one is due.
- **`BotConfig.cacheAllMembers`** — opt into holding every member in memory for the sync-heavy case.
  Off by default; ignored with a log line when `Intent.GuildMembers` was not requested, because
  without that intent Discord never sends the members to cache.
- **`DiscordGateway.closeAwaiting(timeout)`** — closes and waits for what was already sent to leave.
  `close()` returns as soon as it has asked the backend to stop, which drops precisely the shutdown
  announcement posted immediately before it.
- **The rest of Components V2** — `Section` (up to three lines of text with one accessory), `Thumbnail`,
  `MediaGallery`, `Container.spoiler`, `SelectOption.emoji`, and `EntitySelect` over Discord's own
  users, roles and channels. A section with a thumbnail is the layout a chat bridge actually wants —
  a relayed line with the speaker's face beside it — and an embed could only fake it with the
  author-icon slot, which is one per embed and cannot repeat down a message.
- **`DiscordGateway.onMessageEdit`, `onMessageDelete` and `onMemberChange`** — a bridge that relays the
  original and ignores the edit shows Minecraft a version of the conversation that exists nowhere, and
  the edit is usually the correction. `onMemberChange` covers joins, departures and role changes, so a
  role sync reacts instead of re-reading every linked member on a timer. A bulk purge is fanned out to
  one deletion each, so the single-message path is the only one a consumer has to write.
- **`DiscordGateway.sendTyping`** — the standard affordance while a slow command runs.
- **`DiscordCommandSpec.signature()` and `signatureOf(commands)`** — a stable fingerprint of everything
  Discord is actually told, so a bot can skip the destructive, heavily rate-limited registration PUT
  when nothing changed. It excludes the executor: changing what a command does changes nothing Discord
  stores, and hashing the lambda would make every restart look like a change.
- **`OptionValue.asMentionable` and `asAttachment`, and a `mentionable(...)` option** — `attachment(...)`
  previously declared an option whose value could not be read at all.
- **Six more `DiscordPermission` entries** — ManageChannels, ViewAuditLog, MentionEveryone,
  ManageNicknames, ManageRoles, ManageWebhooks.
- **Inbound message fidelity** — `DiscordChatMessage` gained `displayContent` (mentions resolved to
  names; the raw form put a bare `<@493…>` in front of players) and `reply`, and `DiscordActor` gained
  `username`, `nickname` and `avatarUrl` kept apart from the display `name`. Collapsing the handle and
  the display name into one field lost the only half that is stable across guilds and renames.
- **`sculk-discord` and `sculk-discord-jda`** — a backend-neutral Discord API. Messages are a
  component tree as data, slash commands are a spec mirroring the Brigadier side, and Discord's
  timing rules are types: `replyModal` exists only before acknowledgement, and deferring hands back a
  different type whose replies go to the follow-up hook. Everything suspends; there is no action to
  build and forget to queue.
- **`Mentions`** — an allow-list, `None` by default and opt-in per message. The Discord equivalent of
  the `Placeholder.unparsed` boundary, replacing the practice of editing a message body to defuse a
  ping, which misses `<@&roleId>` and corrupts the text a moderator reads as evidence.
- **`ComponentId`** — namespaced component state with Discord's 100-character budget enforced at
  build time. Over-budget fails rather than truncating: a shortened UUID still parses and then matches
  no record.
- **`awaitComponent`** — wait for a click on a message, optionally from one user, with a timeout. The
  discord.js collector JDA has no equivalent for.
- **`DiscordWebhook`** — the zero-gateway path, on serialized payloads rather than a hand-built JSON
  string.
- **`FakeDiscordGateway`** — a published test fixture that refuses what a real gateway refuses.

`sculk-discord` imports no Bukkit and no JDA, enforced by a build check that scans compiled classes.
The same API writes a standalone bot and a plugin-owned one.

- **`SculkPlugin.banner` and `bannerArt()`** — the start-up banner can now be turned off, or given a
  plugin's own art. `onEnable` is final and printed Sculk's mark unconditionally, which put the
  framework's branding in the console of anyone paying for a plugin built on it, and made a plugin
  with a banner of its own print two.

- **`ItemDescriptor` is `@Serializable`.** It is the config-shaped item type, `items/config-items`
  documents putting one in a settings class, and `sculk-config` decodes through the
  compiler-generated descriptor — so the one thing the type exists for did not compile. A plugin
  embedding one had to hand-write a `KSerializer` for a framework type, or mirror the class.
  `sculk-items` cannot depend on `sculk-config` to prove the two agree, so the round-trip is
  asserted against kaml configured exactly as `SculkConfig` configures it.

- **Custom items from Nexo, Oraxen and ItemsAdder.** Anywhere a material key is read — `item(String)`,
  `SculkMessages.item(String)`, `ItemDescriptor.material` — a value of `nexo:ruby_sword`,
  `oraxen:ruby_sword` or `itemsadder:mypack:ruby` now resolves to that plugin's item, and the rest of
  the descriptor (name, lore, amount, enchantments, persistent data) is written on top of the stack it
  hands back. `ItemBuilder` gained an optional `base: ItemStack` for that: a custom item's model and
  components *are* the item and cannot be re-expressed as a `Material`. `SculkIntegrations.customItems()`
  is the start-up presence check; `CustomItems` itself is reachable statically, because a config value
  resolves through a top-level DSL function with no plugin in scope.

  **Reached by reflection, and that is a licence decision rather than a style one.** Nexo and ItemsAdder
  ship with no licence file at all — all rights reserved by default — and Oraxen carries a custom
  proprietary one. None of them is on the compile classpath, none is bundled, and no repository was
  added. The class is loaded from the plugin's own class loader rather than through `Class.forName`,
  since Paper only lets one plugin see another's when the dependency is declared and a framework cannot
  declare a `softdepend` on behalf of the plugin embedding it. Only the reflected `Method` handles are
  cached: all three APIs return a freshly built stack, so caching one would only reintroduce the
  question of when to invalidate it, and all three register items late, asynchronously, and reload them
  on command.

  Nothing that resolved before changes meaning. `Material.matchMaterial` strips a literal `minecraft:`
  and then deletes non-word characters, so `nexo:ruby_sword` was already `NEXORUBY_SWORD` and already
  null — there is no key to take away from anything. A test pins that. Failure is now three different
  sentences, logged once per key: an unknown material, a plugin that is not installed, and a plugin that
  has no such item are three things a server owner fixes three different ways, and a GUI slot resolving
  a config item with `getOrNull()` used to show none of them.

  `SculkSeries.material` is deliberately *not* the hook. `SculkRegistry.resolve` memoises misses
  forever, so a start-up lookup against a plugin that registers its items later would cache a permanent
  negative — and it returns `Material`, which cannot represent a custom item at all.

### Fixed

- **A thread, an announcement channel or a forum post is a valid target.** Sends resolved only a plain
  `TextChannel`, so every other kind of message channel reported "not visible to the bot" — sending an
  operator to check permissions on a channel whose id was perfectly correct.
- **`channelExists` says which of three things is wrong.** It answered a bare `false` for "not
  connected", "no such channel" and "the bot cannot post there" alike, which are fixed in three
  different places by three different people. It now distinguishes them, and never reports a
  disconnected gateway as a missing channel.
- **An autocomplete supplier that throws is logged.** It produced an empty list, which in the Discord
  client is indistinguishable from "nothing matches what you typed".
- **`awaitComponent` no longer leaks an entry per message id.** Removing the collector and dropping the
  empty list were two steps that could interleave with a concurrent registration; they are now one
  operation under the same lock.
- **Relayed messages keep their order, and a blip no longer drops one.** Sends were bare rest actions:
  two messages dispatched a millisecond apart raced through the rate-limit buckets, which for a chat
  bridge means an exchange that reads backwards. Sends are now serialised per channel — different
  channels stay independent, so a slow console relay cannot hold up chat — and a transient failure
  (an `IOException`, a 5xx, a rate limit) is retried once. Anything unrecognised is not retried, so a
  permissions mistake costs one dropped message rather than a burst of requests.
- **A send during a reconnect says the gateway is down.** `send` never consulted `state.usable`, and
  the dead client was cleared inside the delayed retry coroutine rather than when the disconnect was
  noticed — so for the length of the backoff a send was dispatched into a dead JDA and failed with
  whatever it threw.
- **Two disconnects a millisecond apart no longer start two reconnect loops.** The check on the retry
  job was an unguarded check-then-set reachable from a JDA event thread, a coroutine and the connect
  path at once.
- **The member cache actually caches.** `MemberCachePolicy.DEFAULT` is `VOICE.or(OWNER)`, and the
  voice-state cache flag it depends on was disabled two lines later — so the setting looked like it
  cached members and held approximately the guild owner. Now explicitly all-or-nothing, driven by
  `BotConfig.cacheAllMembers`, with the chunking filter set to match.
- **A rate-limited webhook is retried at the time Discord asked for.** A 429 carries `Retry-After`,
  and treating it as an ordinary rejection discarded the one piece of information that would have let
  the message through — during exactly the burst that caused the limit. Retried once, not in a loop.
- **The webhook fallback stops dropping content in silence.** A row of link buttons and any row at the
  top level vanished from the rendered payload with no diagnostic; link buttons now survive as
  markdown links. The unused `flags` field, which documented Components V2 support that was never
  implemented, has been removed rather than left implying it.
- **`DiscordCommandSpec.ephemeral` does something.** It was written by the builder and read by nothing:
  the router's auto-defer hardcoded `ephemeral = true`, so a command declared public still went out
  ephemeral whenever its handler was slow enough for the watchdog to acknowledge first.
- **A nested container fails where it is built.** `Container(children = listOf(Container(...)))`
  constructed happily and threw during rendering at send time, so the failure named an HTTP call
  instead of the line responsible — and only for messages that were actually sent.
- **Items build on every 1.21.x server, not just the newest.** `api-version: '1.21'` loads a plugin on
  the whole 1.21 line, but Paper only gained the data-component API partway through it and then
  reshaped two components again: `UNBREAKABLE` is `Valued` on 1.21.4 and `NonValued` from 1.21.5, and
  `TOOLTIP_DISPLAY` does not exist below 1.21.5. A JVM field reference carries its type descriptor, so
  the 1.21.11-compiled `UNBREAKABLE` reference did not resolve on 1.21.4 — `NoSuchFieldError`, thrown
  the first time it ran. `ItemDescriptor.toItemStack` reached it unconditionally by calling
  `unbreakable(false)` for the default, so on 1.21.4 *every* item built from config threw, and a
  plugin's rewards silently stopped being handed out. `ItemBuilder` now writes components where they
  exist and the equivalent `ItemMeta` calls where they do not; each component that changed shape is
  probed by reflection and isolated, so an old server loses one property rather than the item. The
  probes assert against the compiled API in `ItemCompatTest` — a typo in one would otherwise route
  every server down the legacy path and nothing would notice. No public signature changed.
- **A GUI slot that supplies its own stack now takes the name and lore written beside it.**
  `GuiItemBuilder.build` returned `explicitStack?.clone()` untouched, so `stack(head); name = "..."`
  silently dropped the name — and supplying a stack is the *only* way to build a player skull
  carrying a profile, or a config-backed `ItemDescriptor`. The slots that most need a name were
  exactly the ones that could not have one: every player head in every DaisyStaff staff menu reached
  a moderator as a bare "Player Head", from a block of code that reads as though it sets both.
  Nothing threw and nothing logged; it was visible only by opening the menu. Written through
  `setData(CUSTOM_NAME/LORE)` rather than `editMeta`, matching `ItemBuilder` — mixing the two APIs on
  one stack is how a name set here reads back as null from the component the rest of the library asks
  for.
- **GUI item names and lore now render through the plugin's theme.** A `Gui` is defined by `gui { }`
  long before anything knows which `SculkMessages` will open it, and items were built there and then
  — against a default renderer carrying `SculkTheme.EMPTY`. So a semantic tag in an item name reached
  the player as the literal text `<danger>`, while the GUI *title*, which `Gui.buildInventory` renders
  with the real renderer, came out themed. `GuiItem` now carries a render function instead of a
  finished `ItemStack`, and the registry's renderer is applied when the menu opens — including for
  `stack { }`, `dynamicContent`, and `refresh`. No public signature changed; every affected member is
  `@SculkInternal`.
- **Boss bars are hidden when the HUD closes.** `HudService.close()` cleared its map without hiding
  anything, so on plugin disable or `/reload` every bar stayed on every viewer's screen with nothing
  left that knew how to remove it — surviving the plugin that created it until the player reconnected.
  `forget(player)` had the same gap.

### Changed

- **`ItemBuilder`'s constructor gained a third parameter, `base: ItemStack? = null`.** Source-compatible
  for Kotlin, which resolves the default; a Java caller holding `new ItemBuilder(material, messages)`
  has to be recompiled, as the two-argument constructor no longer exists in the bytecode.
- **The startup banner names a packet backend only when one loaded**, instead of reporting `none`.
  Accurate, but most plugins use no packet features, so their owners read `none` on an otherwise
  healthy startup and ask what is broken. A plugin that depends on a backend can say so in
  `bannerFacts()`, where it knows whether the absence matters.

- **A generated config no longer stops parsing on the second boot.** A `${VAR:-}` default rendered as
  a plain scalar, and environment substitution runs over the text before it is parsed — so an unset
  variable left `password:` with nothing after it, which is a null. `storage.yml` ships that default,
  so every consumer of `sculk-data` wrote a working file on the first boot and failed to read it on
  every one after. Values containing a placeholder are now quoted when written.
- **`@Comment("a\nb")` no longer corrupts the file it documents.** Only the first line was prefixed
  with `#`, leaving the rest as bare lines that do not parse. The `\n` form has been documented since
  4.x without ever working.
- **Schema migration reports a probable rename instead of silently emptying the table.** 5.0 derives
  column names from the property verbatim where 4.5 converted them to `snake_case`. Migration is
  additive, so on a 4.5 table the new column was added beside the populated old one and every row
  read back as its Kotlin defaults, with nothing thrown and nothing logged. When new columns appear
  beside columns the entity does not model, both sets are now named in a warning.
- **Banner facts past the last line of art are no longer dropped.** `show()` iterated the art and
  read facts by index, so a plugin adding two facts of its own silently lost the framework's own
  "Started in" row.
- **Table setup no longer fails on every genuine MySQL server.** `SchemaMigrator` emitted `CREATE
  INDEX IF NOT EXISTS`, which is a MariaDB extension that SQLite and Postgres also have and **no
  MySQL release does**. One `SqlDialect.MYSQL` covers MySQL and MariaDB alike, because the MariaDB
  driver talks to both, so nothing could branch on it. The migrator now asks the database which
  indexes exist and creates only the missing ones, which needs no dialect branch at all.

### Testing

- **CI runs the data layer against real MySQL, MariaDB and PostgreSQL servers.** The `CREATE INDEX
  IF NOT EXISTS` failure above reached customers because the suite's idea of MySQL was H2's MySQL
  mode, which accepts the syntax — the embedded database is not the production database, and a green
  suite meant nothing about the engine anyone actually runs. A `Real Databases` job now starts
  `mysql:8` (never MariaDB in that slot: MariaDB accepts the broken statement and would have passed
  before the fix), `mariadb:11` and `postgres:16` as service containers and fills the URL variables
  the integration tests are gated on. Each engine runs the second-boot path where every failure in
  this layer has been: a schema that already exists, an index that is already there, a column the
  entity has gained since, and an upsert issued by an entity that predates it. The job also fails if
  any of the three suites reports itself skipped, because a gate that silently disables its own tests
  is the same hole one level up.

### Documentation

- The config, data, packets, integrations, architecture and recipe pages taught APIs that no longer
  exist — `@param:Min`, `@PrimaryKey`, `limit()`, `data.cached(delegate =)`, `commands.registerAll`,
  `packetsResult`, and a `SculkPlugin({ gui(); data() })` constructor that takes no arguments. A
  migration onto 5.0 had to be told to distrust the docs and read the `.api` dumps instead. The
  `storage.yml` sample is now the file the generator actually writes.

## [5.0.0] — "Rebuilt" — 2026-07-31

A full rebuild. 5.0 is not a migration from 4.5; it is the same ideas rewritten with the benefit of
having used them. Every module was touched, six were merged or removed, and two were added.

### Security

- **Placeholder values can no longer inject MiniMessage.** `SculkMessenger` and `SculkText` both
  substituted values into templates with `text.replace("<key>", value)` *before* parsing, in two
  separate modules. Anything that could influence a value reaching a message — a player name, an
  anvil-typed item name, a database string — could inject `<red>`, a fake staff prefix, or a
  `<click:run_command:…>` that fires as whoever *reads* the message. `SculkMessages` is now the
  only place a String becomes a Component, and every value enters through `Placeholder.unparsed`.
- **A packet handler that throws no longer kicks the player.** PacketEvents treats an exception
  escaping a listener as a malformed packet and disconnects the client; all four handler call sites
  were unguarded. `PacketGuard` wraps every one, catches `Throwable`, leaves the packet alone, and
  rate-limits logging to one trace per five seconds.
- **Config rewrites can no longer destroy data.** A rewrite now only ever *adds*: keys the data
  class does not model, and comments a server owner wrote, are preserved byte for byte.

### Added

- **`sculk-hud`** — sidebar, action bar, tab list and boss bars, all driven by one task rather than
  one per element per player. Flicker-free sidebar, action-bar priority arbitration with expiry,
  per-viewer placeholders.
- **`ServiceRegistry`** — type-keyed, explicitly not DI, reverse-order shutdown.
- **Theme system** — messages written against meaning (`<danger>`) rather than colour, expanded
  before parse so a style can be a scoped gradient. Plus `MinecraftFont`, which measures real glyph
  widths so centred text is actually centred.
- **Nametags** — multi-line displays ridden as a passenger so the client interpolates them.
- **`VirtualEntityService`** — backend-neutral spawn/update/teleport/mount/despawn for display
  entities.
- **PostgreSQL** as a first-class storage backend, alongside SQLite and MySQL/MariaDB. The Redis
  cache works against Valkey through the same client and URI.
- **`ClientBlockService.setAll`** — multi-block changes packed by chunk section.
- **Test fixtures** — `FakeScheduler` and `FakeVirtualEntityService` ship as published
  `testFixtures` so consumers test against the same fakes Sculk does.
- **Committed `.api` dumps** with `apiCheck` in CI.

### Changed

- **Kotlin-only.** Every Java-parity overload, `@JvmStatic`/`@JvmOverloads`/`@JvmName` facade and
  `CompletableFuture` bridge is gone, along with `examples/java-basic-plugin`. The one survivor is
  `CompletableFuture.await()`, for Paper APIs that hand Kotlin callers futures.
- **`sculk-config`** rebuilt on kotlinx-serialization + kaml. No `kotlin-reflect`. The defaults are
  the shipped file; validation walks the parsed tree so a violation reports `mysql.port` rather than
  `port`; comments are matched by path so nested keys keep theirs.
- **`sculk-data`** rebuilt. `RowCodec` replaces the reflective mapper. Identifiers are quoted, so an
  entity with a column called `order` or `key` works. `REPLACE INTO` is gone — it was a delete and
  insert that wiped columns outside the statement. Query gains OR, IN, multi-column ordering and
  OFFSET; `@Index` ends full scans; `topBy` sorts in SQL. Every failure is logged before it is
  returned.
- **`sculk-commands`** split into `CommandSpec` (data, no Brigadier, no Bukkit) and one adapter.
  Fixes dispatch: a node declaring both `player {}` and `console {}` previously never reached the
  console branch. Suggestions come from a lambda, so they do not go stale after a config reload.
  Generated permission-filtered `/help`.
- **`sculk-gui`** — the global `GuiRegistry` object becomes a platform-owned `MenuRegistry` keyed by
  inventory identity. The listener is registered unconditionally; previously a plugin that skipped
  `gui()` got an uncancelled, fully lootable inventory. Clicks cancel before dispatch.
- **`PaperScheduler`** is one code path instead of branching on Folia in twelve methods. Non-positive
  delays and periods are normalised; the entity scheduler's retired callback runs the task instead
  of dropping it silently.
- **Subsystems are lazy**, not opt-in flags on a builder. Shutdown never opens something that was
  never used.
- **Items** move to `ITEM_MODEL` and `TOOLTIP_DISPLAY`; `ItemFlag` is gone. `item(String)` returns a
  `SculkResult` rather than null, matching `ItemBuilder` instead of contradicting it.
- **`giveOrDrop` reports what was actually delivered.** `given` previously carried the whole input,
  so a delivery into a full inventory returned the same stack in both `given` and `dropped` and
  anything logging or charging for a reward counted items lying on the floor. The two lists are now
  disjoint, a partly-accepted stack appears in each at its own amount, and `fullyDelivered` answers
  the common case.
- **`enchant(key)` no longer throws on an unknown key.** A material key returns a failure and a
  model key is dropped; an enchantment key killed the whole item build — three answers to one typo
  in a config file. It now costs the enchantment, logs the key, and builds the item.
- **Examples** cut from 13 to 6.

### Removed

- `sculk-content` — 76 lines of pure delegation to an already-public API.
- `sculk-adventure` — merged into `sculk-text`.
- `sculk-effects` and `sculk-holograms` — merged into `sculk-visual`.
- `sculk-events` — folded into `sculk-platform`; it cannot work without a `Plugin`.
- `sculk-tasks` — folded into `sculk-common` as `studio.sculk.task`.
- `SculkRuntime` — a stable-marked interface with no implementations and no call sites.
- `SculkTaskGroup` — `SculkHandle.all()` is the same thing, done correctly.
- `SculkTextStyle` — a mutable global two plugins on one server could not share.
- The four migration guides for versions nobody outside the repo ran, and `REVIEW.md`, a snapshot
  whose accuracy decayed and which nothing regenerated.
