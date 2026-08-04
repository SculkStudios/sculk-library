# Changelog

All notable changes to Sculk Studio are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

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

### Fixed

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
