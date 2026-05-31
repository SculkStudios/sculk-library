# Sculk Library — Engineering Review (4.x)

A review of the library for use as a shared foundation across many plugins, from integrating it
into DaisyVotes. Overall it's in good shape: coroutine-native, HikariCP-pooled data layer, clean
GUI/command DSLs, `@SculkStable`/`@SculkInternal` API hygiene, and decent test coverage. The items
below are prioritized; severities are **[bug]**, **[gap]**, **[polish]**.

Legend: ✅ already fixed this pass · 🔴 high priority · 🟡 medium · ⚪ low.

---

## Already fixed this pass
- ✅ **[bug] ORM enum fields crashed on read.** `OrmMapper` stored enum values as the raw object and
  read them back as `String`, so `callBy` threw `ClassCastException`. Now enums persist as their
  `name` (TEXT) and are parsed back via `enumConstants`. (`sculk-data/.../orm/OrmMapper.kt`)
- ✅ **[polish] Item text was italic by default.** `parseItemText` now applies
  `decorationIfAbsent(ITALIC, false)` so names/lore render upright unless explicitly italicised.
  (`sculk-items/.../ItemText.kt`)
- ✅ **[polish] Generated configs were noisy.** `YamlMapper` now omits null/empty fields in nested
  objects, renders empty collections compactly (`[]`/`{}`), and stops long-string line-wrapping.
  Configs are now clean and editable. (`sculk-config/.../yaml/YamlMapper.kt`)

---

## Correctness / robustness

- 🔴 **[gap] No schema auto-migration in the ORM.** `JdbcRepository` runs `CREATE TABLE IF NOT
  EXISTS` once, but when an entity gains a field later, the column is never added — reads of the new
  column fail and writes silently drop it. DaisyVotes had to hand-roll `ALTER TABLE` in a
  `MigrationService`. **Proposal:** on init, read existing columns (`PRAGMA table_info(t)` for
  SQLite, `information_schema.columns` for MySQL) and `ALTER TABLE … ADD COLUMN` any missing mapped
  columns (with sensible defaults). This removes per-plugin migration boilerplate entirely.
- 🔴 **[gap] ORM only supports scalars + UUID + String + (now) enums.** `List`/`Map`/nested data
  classes/`Instant`/`BigDecimal` fall through to `TEXT` + raw `setObject`, which is driver-dependent
  and usually breaks. **Proposal:** (a) built-in `Instant`/`LocalDateTime` handling; (b) opt-in JSON
  columns for complex types via kotlinx.serialization or a registerable `TypeAdapter<T>`. Many
  plugins want a `List<String>` or a small embedded object on an entity.
- 🟡 **[bug] `GuiSession.refresh(slot)` ignores dynamic content.** It re-renders `item.stack` (the
  static fallback), not `item.resolveStack(player)`, so refreshing a `dynamicContent {}` slot shows
  the wrong item. Use `resolveStack(player)` there.
- ⚪ **[polish] Caffeine cache has no negative caching.** `find` only caches non-null results, so
  repeated lookups of an absent id always hit the DB. Optional; add a tombstone if hot.

## Data layer — features that pay off across plugins

- 🔴 **[gap] No indexes.** Queries on non-PK columns full-scan. Add an `@Index` annotation
  (`@Index` on a param, or `@Table(indexes=[…])`) → `CREATE INDEX IF NOT EXISTS`. Critical for
  leaderboards and "find all rows for player X" patterns.
- 🔴 **[gap] Repository is missing everyday operations.** Plugins repeatedly reach for
  `findAll().filter { … }` (DaisyVotes does this for per-player site votes and leaderboards), which
  loads the whole table. Add:
  - `count(block)` and `deleteWhere(block)`
  - `findFirst(block)` / `single` returning one row
  - pagination `offset` (have `limit`)
  - `IN` and `OR` in `QueryBuilder` (currently AND-only)
  - a one-liner `findAllBy(Entity::prop, value)`
- 🟡 **[polish] `SculkCache.findTopBy` loads the whole table and sorts in memory** every call. Back
  it with SQL `ORDER BY … LIMIT` (the query builder already supports both) and optionally cache the
  small result for a TTL — ideal for leaderboards.
- 🟡 **[gap] No first-class "owned-by-player" helper.** A `PlayerOwnedRepository` (index on a
  `playerUuid` column + `allFor(uuid)`/`deleteFor(uuid)`) would cover a huge fraction of plugin use
  and steer people away from `findAll().filter`.

## Config

- 🟡 **[gap] `@Comment` only documents top-level keys.** Nested data-class fields and list elements
  can't be annotated, so complex configs (e.g. reward entries) are uncommented inside. Support
  `@Comment` on nested fields and emit inline comments in `buildCommentedYaml`.
- ⚪ **[polish] Existing files aren't re-cleaned.** `writeDefaults` merges raw loaded values
  (bypassing `toMap`), so a file written by an older version keeps its old formatting until
  regenerated. Optional: normalize on load.

## GUI / commands

- 🟡 **[gap] No built-in text input.** Anvil-input (or sign-input) helper for "type a value" flows
  is a near-universal need; pairs well with the existing `confirmMenu`.
- 🟡 **[gap] No auto paginated-list builder.** `setEntries` + manual prev/next is good, but a
  `paginatedList(entries) { renderItem(...) }` helper that wires arrows/among slots automatically
  would cut boilerplate (leaderboards, shops, kits all want it).
- ⚪ **[polish] Auto `help` for command trees.** Generate a themed usage list from the registered
  `CommandNode` tree (DaisyVotes hand-wrote `/vote help` and `/daisyvotes`). A default
  `sculk help` / no-arg overview would be a nice freebie.

## Performance

- ⚪ Reflection in `OrmMapper.valuesOf`/`fromResultSet` runs per row. Fine at plugin scale; if a hot
  path emerges, cache the `KProperty` accessors per mapping.
- ✅ Good: Hikari pooling, SQLite `maximumPoolSize=1` (single-writer), batched `saveAll` in a
  transaction, suspend IO on `Dispatchers.IO`.

## Testing / project

- 🔴 **[gap] No real JDBC round-trip tests.** `sculk-data` tests use an in-memory fake repository,
  so `OrmMapper` + `JdbcRepository` aren't exercised against a real DB — which is exactly why the
  enum bug slipped through. Add **H2-backed** integration tests covering: every supported type
  (incl. enum/UUID/Boolean), `query` (operators/order/limit), `saveAll`, `exists`, and (once built)
  schema auto-migration.
- 🟡 **[polish] Ship the test doubles for downstream.** The `Fake*Scheduler`/in-memory repo doubles
  live in test sources. A small `sculk-testing` module exporting them would make downstream plugin
  testing much easier (DaisyVotes re-implemented a `MemoryRepository`).
- ⚪ Consider a published **version catalog / BOM** so downstream plugins pin one Sculk version.

---

## Suggested roadmap (by impact)

1. **ORM schema auto-migration** + **`@Index`** — removes the biggest source of per-plugin
   boilerplate and silent breakage.
2. **Repository query surface** (`count`/`deleteWhere`/`findFirst`/`offset`/`IN`/`OR`/`findAllBy`)
   + **H2 integration tests**.
3. **Complex-type/JSON column support** and built-in `Instant`/`LocalDateTime`.
4. **GUI**: fix `refresh` dynamic content; add paginated-list + text-input helpers.
5. **Config**: nested `@Comment`s.
6. **DX**: `sculk-testing` module, auto command help, leaderboard-friendly cached `findTopBy`.

Nothing here is blocking — the library is already solid and shippable. These are the things that
would make it genuinely best-in-class to build many plugins on.
