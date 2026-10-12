# E2 — Presentation, GUI, Config, Data, Packets: competitor matrix and recommendations

**Workstream**: Sculk Studio 6 research E2 · **Date**: 2026-10-12 · **Status**: Draft for ADRs
**Scope**: five areas, one matrix each, seven deep dives, one recommendation per area. Versions were
read from Maven Central `maven-metadata.xml`, GitHub releases/commits and source clones on the
date above; anything not confirmed is marked UNVERIFIED. Licence notes are facts, not legal advice.

## Baseline facts that frame every area

| Fact | Evidence |
|---|---|
| Minecraft is on year-based versions: **26.3 "Wilderness Bound" released 2026-09-15**; 26.4 snapshots since 2026-09-22 | [version history](https://minecraft.wiki/w/Java_Edition_version_history) |
| Paper 26.3 javadoc = `paper-api 26.3.build.157-beta`; Sculk 5's catalog still pins `paper 1.21.11`, `kaml 0.104.0`, `packetevents 2.13.0`, `protocollib 5.4.0` | [jd.papermc.io/paper/26.3](https://jd.papermc.io/paper/26.3/), `gradle/libs.versions.toml` |
| Paper exposes `snakeyaml 2.2` + `gson` as `api`; Velocity exposes Configurate 4.2.0 + `snakeyaml 2.5`; **nobody ships kotlinx** | [paper-api build](https://github.com/PaperMC/Paper/blob/main/paper-api/build.gradle.kts), [Velocity api build](https://github.com/PaperMC/Velocity/blob/dev/3.0.0/api/build.gradle.kts) |
| Paper 1.21.11 ships `sqlite-jdbc 3.49.1.0`, `mysql-connector-j 9.2.0`, `slf4j-api 2.0.17`, Netty 4.2.7 | [paper-server build](https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.11/paper-server/build.gradle.kts), Mojang 1.21.11 manifest |
| GPL plug-in rule (FSF): a nonfree program designed to load a GPL plug-in and share data structures is a combined work; community practice is `compileOnly` + `softdepend`, never shade. No legal opinion found that the practice escapes the GPL | [GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html) |

Decision vocabulary used below: **integrate** = depend on it; **abstract** = Sculk owns the API, the
project is one backend; **native** = Sculk implements it; **leave** = out of scope, point users upstream.

## A. Presentation / placeholders

### Matrix

| Project | Version · date | Licence | Maintenance | MC / Java | Paper · Folia · Velocity | Kotlin | Key features | Why used | Limitations | Jar · deps |
|---|---|---|---|---|---|---|---|---|---|---|
| [TAB](https://github.com/NEZNAMY/TAB) | 6.2.0 · 2026-09-17 | Apache-2.0 | push 2026-10-11; "feature requests ❌, free support ❌" | 1.7.10 → 26.3; runs on server's Java; compiling needs JDK 25 | Paper · **Folia yes** (`folia-supported: true`) · Velocity (teams need VelocityScoreboardAPI) · Bungee · Fabric/(Neo)Forge | Java API, `Supplier`/`Function` lambdas; JitPack `com.github.NEZNAMY:TAB-API`, `compileOnly`, must not be shaded | header/footer, tablist format, nametags(teams), sorting, sidebar, bossbar, belowname, playerlist objective, layout, global/per-world playerlist | all-in-one, packet-level, per-placeholder refresh, conditions, PAPI bridge | one team per player; vanilla team limits; legacy-`&` centric config | 8.5 MB universal / 7.9 MB Paper jar |
| [MiniPlaceholders](https://github.com/MiniPlaceholders/MiniPlaceholders) | 3.2.0 · 2026-03-27 (Central); 3.2.1 GH 2026-09-07 | Apache-2.0 | push 2026-10-10 | Paper 1.21+, Velocity 3.3+, Fabric, Sponge 12+, Minestom; **Java 21+** | Paper · Folia · Velocity · Fabric · Sponge | **best in class**: `miniplaceholders-kotlin-ext` (explicitApi DSL) | Component-native tags `<expansion_key>`; global/audience/relational; `ArgumentQueue` args | sits inside MiniMessage parse, zero string munging | **pull-only, no invalidation** | api 44 KB, kotlin-ext 6 KB, all deps compileOnly |
| [PlaceholderAPI](https://github.com/PlaceholderAPI/PlaceholderAPI) | 2.12.3 · 2026-07-03 | **GPL-3.0** | push 2026-09-12 (HelpChat) | api-version 1.13, `--release 8`; `folia-supported: true` since 2.11.7 | Paper/Spigot · Folia · no proxy | Java static `String` API | `%id_params%`, relational `%rel_%`, brackets, eCloud; Paper-only `PAPIComponents` since 2.12.1 | de-facto standard, thousands of expansions | string-only expansions, no result cache, legacy colours | plugin 1.16 MB; API jar 159 KB |
| [FastBoard](https://github.com/MrMicky-FR/FastBoard) | 2.2.2 · 2026-09-21 | MIT | push 2026-09-21 | 1.7.10 → 26.2 via NMS reflection | Paper/Spigot; async-safe; Folia UNVERIFIED (not mentioned) | plain Java; `adventure.FastBoard` takes `Component` | flicker-free sidebar, 1.20.3 `NumberFormat`, ≤15 lines | tiny, zero deps, no Bukkit-Scoreboard conflicts | sidebar only; reflection breaks per MC bump | 27 KB, 0 deps |
| [NametagEdit](https://github.com/sgtcaze/NametagEdit) | 4.0.2 (2016) + Jenkins builds | GPL-3.0 | push 2025-09-30; README 1.7–1.20 | 1.7–1.20.x | Bukkit only | none | prefix/suffix via fake packet teams | simple rank tags | "a player can belong to only one team"; other team plugins → client crash, "no workaround" | 439 KB |

### Deep dive 1 — TAB: conditions, refresh, team interoperability

**Conditions grammar** ([wiki](https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Conditional-placeholders), verified against `ConditionalExpression.java`/`Operator.java`):

| Operator | Meaning | Example |
|---|---|---|
| `>=` `>` `<=` `<` | numeric | `%ping%>=100` |
| `=` / `!=` | exact text (case + colour significant); `%x%=` is an empty check | `%world%=world` |
| `<-` / `!<-` | contains | `%world%<-lobby-` |
| `\|-` / `!\|-` | starts with | `%world%\|-lobby-` |
| `-\|` / `!-\|` | ends with | `%world%-\|nether` |
| `permission:<node>` / `!permission:` | has/lacks permission (OP passes) | `permission:tab.staff` |

- Combine: `;` = AND, `|` = OR (short form); long form `type: AND|OR` with `conditions: [...]`, `true:`/`false:` output (defaults "true"/"false"). **AND and OR cannot mix in one condition — nest** (`Condition.java:276`). Used as `%condition:name%`; any `%viewer:…%` inside makes it relational (`%rel_condition:name%`).
- Refresh of a condition = fastest placeholder inside it; permission checks count as 1000 ms.

**Refresh model** ([Placeholders](https://github.com/NEZNAMY/TAB/wiki/Placeholders), [Optimizing](https://github.com/NEZNAMY/TAB/wiki/Optimizing-the-plugin)):
- Every placeholder declares a refresh interval in ms, **divisible by 50**; `-1` = constant/event-driven. Internal: `%health%` 100, `%ping%` 500, `%online%`/`%tps%` 1000, `%date%` 60000. PAPI: `placeholder-refresh-intervals.default-refresh-interval: 500` + per-key overrides; `permission-refresh-interval: 1000`.
- Refresh runs **async by default**; `%sync:<placeholder>%` forces main thread. Unused placeholders cost nothing. `/tab cpu` reports per-placeholder cost (10 s windows).
- API (`PlaceholderManager`): `registerServerPlaceholder(id, refreshMs, Supplier<String>)`, `registerPlayerPlaceholder(id, refreshMs, Function<TabPlayer,String>)`, `registerRelationalPlaceholder(id, refreshMs, BiFunction<TabPlayer,TabPlayer,String>)` (id must start `%rel_`); `IllegalArgumentException` if not `%…%` or refresh not 50-aligned.
- `placeholder-output-replacements`: exact match, numeric interval `"0-50"`, negatives `"-100~-1"`, `"else"`; overlapping intervals → higher wins ([wiki](https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Placeholder-output-replacements)).

**Interoperability rules Sculk must document** (sources: [Nametags](https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Nametags), [Sorting](https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Sorting-players-in-tablist), [Tablist name formatting](https://github.com/NEZNAMY/TAB/wiki/Feature-guide:-Tablist-name-formatting), [Compatibility](https://github.com/NEZNAMY/TAB/wiki/Compatibility), `SafeScoreboard.java`):

1. **One team owner per player.** "Scoreboard teams is a feature that cannot be handled by multiple plugins at once." A team carries name, prefix, suffix, nametag visibility, collision rule and colour as one unit; you cannot take prefix from plugin A and collision from plugin B.
2. **Anti-override is always on** (config option removed in 6.x). TAB intercepts outgoing team CREATE/ADD_PLAYER packets; an entry already in a TAB team under another name is **stripped from the packet** and logged once to `anti-override.log` ("Blocked attempt to add player X into team Y (expected team: Z)"). Known false positives: Paper `collideRule_*`, CMI `CMINP*`, Citizens `CIT-*`.
3. **Tablist names likewise**: "TAB will block attempts to modify tablist display names coming from all other plugins." Nick plugins must expose a placeholder and let TAB render it.
4. **Opt-out API**: `NameTagManager.pauseTeamHandling(TabPlayer)` (unregisters the team and disables anti-override for that player), `resumeTeamHandling`, `hideNameTag`, `setCollisionRule`, `setPrefix/Suffix`; all changes **temporary** (lost on reload/reconnect). Alternative: per-feature `disable-condition`.
5. **Sort order lives in the team name** (client sorts teams alphabetically): ≤16 chars, 15 usable + 1 for uniqueness; the Layout feature bypasses this.
6. **Sidebar**: TAB hides its own when another plugin's objective appears in the SIDEBAR slot and restores it on unregister. **Playerlist objective**: no compat — "may not show anymore (depending on who sends it first)".
7. **Vanilla limits TAB inherits**: prefix/suffix 16 chars ≤1.12.2; 1.13+ one colour per team; RGB rounded to legacy; glow colour = team colour. Armor-stand "unlimited nametags" **removed** ("unfixable issues") — points to UnlimitedNameTags/DisplayNameTags.
8. Listed conflicts: glow plugins, Skript addons, Waterfall `disable_tab_list_rewrite`, ViaVersion on proxy, Nexo/ItemsAdder `hide_scoreboard_numbers`.

### Deep dive 2 — MiniPlaceholders expansion API; pull-only

Source: `api/.../Expansion.java`, `MiniPlaceholders.java`; docs [Expansion Creation](https://miniplaceholders.github.io/docs/developer-guide/Expansion-Creation).

```java
Expansion.Builder builder(String name);
Builder globalPlaceholder(String key, GlobalTagResolver r);      // Tag tag(ArgumentQueue, Context)
Builder globalPlaceholder(String key, Tag tag);                   // immutable constant
<A extends Audience> Builder audiencePlaceholder(@Nullable Class<A> target, String key, AudienceTagResolver<A> r); // Tag tag(A, ArgumentQueue, Context)
<A extends Audience> Builder relationalPlaceholder(@Nullable Class<A> target, String key, RelationalTagResolver<A> r); // Tag tag(A, A, ArgumentQueue, Context)
Builder author(String); Builder version(String); Expansion build();
// Expansion: register(), unregister(), audiencePlaceholders(), relationalPlaceholders(), globalPlaceholders()
// static MiniPlaceholders: globalPlaceholders(), audiencePlaceholders(), relationalPlaceholders(), audienceGlobalPlaceholders(), relationalGlobalPlaceholders()
```
- Tag name `<expansion_key>`; relational tags get `rel_` inserted. There is **no `filter()`** in 3.x — the `Class<A>` target is the filter. Usage: `MiniMessage.miniMessage().deserialize("<player_name>", player, resolver)` — the audience is MiniMessage's `Pointered` target; relational passes `new RelationalAudience<>(a, b)`.
- **Pull-only, confirmed.** The static resolver getters rebuild a `TagResolver` from the registered expansions on every call; `ExpansionImpl` holds plain arrays; `grep -ri "invalidat|refresh|listener|event" api/src/main` returns nothing. The lambda runs at parse time; the only way to refresh is to re-parse. **No invalidation/refresh/change-notification API exists.**
- Kotlin: `io.github.miniplaceholders:miniplaceholders-kotlin-ext` — `expansion("x") { global(..); audience<Player>(..); relational<Player>(..) }`, `ComponentLike.asClosingTag()`.

### Deep dive 3 — PlaceholderAPI: Component API exists (Paper-only), expansions still return String

- Core API (all `String`): `setPlaceholders(OfflinePlayer|Player, String|List<String>)`, `setBracketPlaceholders(...)`, `setRelationalPlaceholders(Player one, Player two, String|List<String>)`, `containsPlaceholders(String)`, `isRegistered(String)`, `getPlaceholderPattern()`. `colorize`/`Pattern` overloads and `registerPlaceholderHook` are `@ScheduledForRemoval(inVersion="2.13.0")`.
- **Component entry point** — 2.12.1 (2026-02-03, [PR #1157](https://github.com/PlaceholderAPI/PlaceholderAPI/pull/1157)), `src/paper` only ("Spigot-based servers will not work"):
  ```java
  public final class PAPIComponents {
    static Component setPlaceholders(OfflinePlayer|Player p, Component c);
    static Component setPlaceholders(OfflinePlayer|Player p, Component c, @Nullable Function<String, ComponentLike> deserializer);
    static List<Component> setPlaceholders(…, List<Component> cs[, deserializer]);
  }
  ```
  Mechanism: walks the component tree (incl. hover, translatable args) and runs the **String** replacer on each text node, then `Component.text(...)` or your `deserializer` (e.g. MiniMessage). Config `use_adventure_provided_replacer: true` switches to `Component.replaceText(pattern)`.
- **Expansions are still `String`**: `PlaceholderHook.onRequest(OfflinePlayer, String)`, `onPlaceholderRequest(Player, String)`, `Relational.onPlaceholderRequest(Player, Player, String)`. [Issue #1208](https://github.com/PlaceholderAPI/PlaceholderAPI/issues/1208) "Allow placeholder to return text component" (2026-03) is **open**. Legacy `§` in the string remains the colour carrier.
- Optional interfaces: `Relational`, `Cacheable` (`clear()` on unregister — **not a result cache**), `Taskable`, `Configurable`, `Cleanable`. **No result caching** (`CharsReplacer` calls `onRequest` per parse); expansions self-cache. No thread assertions in `PlaceholderAPI.java`.
- **GPL-3.0 consequence**: FSF's [GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html) holds that a program designed to load a GPL plug-in and share data structures forms a combined work. Community practice (TAB itself, Apache-2.0, `softdepend: [PlaceholderAPI]`) treats a `compileOnly` + softdepend adapter as separate works. Sculk mitigation: isolated optional module, `compileOnly`, never shaded, licence stance documented. Not legal advice.

### What Paper/Adventure already covers (no library needed)

| Need | Native API |
|---|---|
| Header/footer | `Audience.sendPlayerListHeaderAndFooter(Component, Component)` |
| Tab name / order | `Player.playerListName(Component)`, `setPlayerListOrder(int)` (1.21.2+) |
| Action bar / boss bar / title | `Audience.sendActionBar`, `showBossBar/hideBossBar`, `showTitle`, `sendTitlePart` |
| Per-player scoreboard | `ScoreboardManager.getNewScoreboard()` + `Player.setScoreboard` (weakly tracked — keep a reference) |
| Teams (Component) | `Team.prefix/suffix/displayName(Component)`, `color(NamedTextColor)`, `setOption(NAME_TAG_VISIBILITY / DEATH_MESSAGE_VISIBILITY / COLLISION_RULE, OptionStatus)` |
| 1.20.3+ score text | `Score.customName(Component)`, `Score/Objective.numberFormat(NumberFormat.blank()/fixed()/styled())`; `Objective.displayName(Component)` ≤128 chars; entry ≤32767 chars |

**How packet libs dodge conflicts**: FastBoard uses a per-player objective `fb-<hex>` and teams whose only member is a colour-code string (never a player name), and since 2.2.1 `Score.customName` on 1.20.3+ — so it cannot collide with player teams, only with the SIDEBAR slot. TAB/NametagEdit use teams whose entries *are* player names, hence the one-owner rule.
## B. Inventory GUIs

### Matrix

| | [IF](https://github.com/stefvanschie/IF) | [Triumph GUI 3.x](https://github.com/TriumphTeam/triumph-gui) | [KSpigot GUI](https://github.com/jakobkmar/KSpigot) | [InvUI 2.x](https://github.com/NichtStudioCode/InvUI) | [AnvilGUI](https://github.com/WesJD/AnvilGUI) | SmartInvs |
|---|---|---|---|---|---|---|
| Version · date | 0.12.1 · 2026-08-27; pushed 2026-09-21 | 3.1.13 · 2025-09-28; master pushed 2026-04-14 | tag 1.21.10 · 2025-12-28; **Central only has 1.21.0 (2024-08)** | 2.5.1 · 2026-09-26 | 1.10.13-SNAPSHOT; pushed 2026-07-14; no tags | 1.2.7; last push 2024-05 |
| Licence | **Unlicense** | **MIT** | **GPL-3.0** | **MIT** | **MIT** | Apache-2.0 |
| Maintenance | active, 513★; Folia issue [#383](https://github.com/stefvanschie/IF/issues/383) open since 2023 | semi-dormant: 3.x compat patches; v4 branch last commit 2025-05-18; "v4 full rewrite" #113 open since 2024 | one person, version-bump releases | very active (2.4.0 → 2.5.1 Sep 2026), 425★ | active (38 NMS modules incl. `26_R1`) | abandoned |
| MC / Java | 1.16.5 → 26.x via **20 per-version NMS modules**; compiled Java 8 | spigot-api 1.20.2, Java 8; `triumph-gui-paper` on paper-api 1.21.7 | paperweight 1.21.10, Java 21, coroutines as `api` | **single-version**: 2.4–2.5.x = MC 26.3 only; Mojang-mapped, Java 25 toolchain | 1.7+, Java 8, **Spigot mappings only** | 1.7–1.14 |
| Paper · Folia | Spigot-compatible; **not Folia-safe** (`Bukkit.getScheduler().runTask` ×2 in `GuiListener` → `UnsupportedOperationException`, [#2529](https://github.com/stefvanschie/IF/issues/2529)) | `VersionHelper.IS_FOLIA` via `Class.forName("…RegionizedServer")`; `close()` reflects onto `EntityScheduler`; claims Folia since 3.1.8 | **not Folia-safe**: page animations use `BukkitRunnable.runTaskTimer` | Paper-only; `viewer.getScheduler().runAtFixedRate` + `ThreadCheck.checkOwnedBy(viewer)` — correct by design; no flag (library) | Folia not mentioned | n/a |
| Kotlin | Java API + XML | Java; **no `triumph-gui-kotlin` on Central** | Kotlin DSL | Java core + **`invui-kotlin`** DSL | Java builder | Java |
| Model | 23 Gui types (Anvil, Beacon, BrewingStand, Cartography, Chest, Crafter, Enchanting, Furnace, Grindstone, Hopper, Loom, Merchant, Smithing, Stonecutter…); Panes: Static, Outline, Paginated, Masonry, Pattern + CycleButton/Label/PagingButtons/PercentageBar/Slider/ToggleButton; `Orientable`/`Flippable`/`Rotatable`; XML `Gui.load` | `BaseGui` (InventoryHolder) → `Gui`, `PaginatedGui`, `ScrollingGui`, `StorageGui`; `GuiItem` tagged `mf-gui` UUID NBT; `GuiFiller`; `GuiContainer.Chest/Typed` | `kSpigotGUI(GUIType) { page(n) { button; placeholder; freeSlot; pageChanger; createRectCompound(from rectTo to); compoundScroll } }`; `GUIIndividual`/`GUIShared` | `Gui`/`PagedGui`/`ScrollGui`/`TabGui` from `Structure` strings; `Item`/`AbstractItem`/`BoundItem` with `notifyWindows()`; `Window.builder()` (upper + **lower Gui drawn into player inventory**) / `mergedBuilder()`; typed windows Anvil, Brewing, Cartography, Crafter, Crafting, Furnace, Grindstone, Merchant, Smithing, Stonecutter; one viewer per window | builder: `text`, `title`, `itemLeft/Right`, `onClick → List<ResponseAction>`, `preventClose` | — |
| Click handling | `onOutsideClick → onGlobalClick → onTopClick/onBottomClick → pane`; **nothing cancelled by default**; never inspects `ClickType`/`InventoryAction`; single-slot drag converted to synthetic click; no `InventoryCreativeEvent`; listener registered **once per JVM by the first plugin** | `GuiListener` never cancels; cancellation in `InteractionModifierListener` driven by `InteractionModifier {PREVENT_ITEM_PLACE, _TAKE, _SWAP, _DROP, PREVENT_OTHER_ACTIONS}`; builder default set **empty**; holes: `SWAP_OFFHAND` unreferenced, bottom-origin `HOTBAR_SWAP` uncaught, drag only cancelled with PREVENT_PLACE ([listener](https://raw.githubusercontent.com/TriumphTeam/triumph-gui/master/core/src/main/java/dev/triumphteam/gui/guis/InteractionModifierListener.java)) | cancels actions not in {PICKUP_*, MOVE_TO_OTHER_INVENTORY} and undefined slots; bottom-inventory clicks/shift-clicks untouched; drags → synthetic `UNKNOWN` clicks | **Bukkit `InventoryClickEvent` is not fired for in-GUI clicks**: own `AbstractContainerMenu` proxy + `PacketListener` on `ServerboundContainerClickPacket`; handles shift, NUMBER_KEY, SWAP_OFFHAND, DROP, DOUBLE_CLICK, drags itself | — | — |
| Redraw | `update()` = `inventory.clear()` + display all; if rows/title changed → **new inventory + `openInventory` every viewer**; **full redraw, no diff** | `update()` = `clear()` + `populateGui()`; `updateItem(slot)` per slot; `updateTitle()` = new inventory + reopen (issue #87 "nullifies listeners") | `loadContent` = `clear()` + `setItem` per element; slide/swipe effects rerun per tick | **true diff**: `notifyUpdate(slot)` sets a `dirtySlots` bit; per-tick `updateSlots()` re-renders dirty slots; `sendChangesToRemote` compares `RemoteSlot` and sends one `ClientboundContainerSetSlotPacket` per mismatch | n/a | — |
| NMS | 20 modules (`nms/1_16_5 … 26_1`) for anvil/loom text etc.; beacon holder via NMS; `IF-uuid` PDC tag per item | none (reflection for Folia + version only) | none | **deep** (`HashedStack`, `RemoteSlot`, packet injection) → one artifact per MC version | 38 modules, shade + relocate | — |
| Jar · deps | **1.64 MB**, 0 transitive | **80 KB**; adventure-api 4.22 + serializers + adventure-platform-bukkit (paper variant excludes kyori) | 522 KB + coroutines | UNVERIFIED size; jspecify only | 39 modules | — |
| Complaints | Folia open 3.5 yrs; "update not working" #2487; Paper pre-26.1 needs `paperweight-mappings-namespace: spigot`; Java-8 API | abandoned v4; permissive defaults; title reopen bug | GPL; one maintainer; unpublished | single-version artifacts; other plugins cannot see its GUIs | superseded by `AnvilView` | dead |

**Dialog-era libraries**: [AlepandoCR/DialogAPI](https://github.com/AlepandoCR/DialogAPI) (Kotlin wrapper, 1.21.6; licence UNVERIFIED) and [PaperDialogDSL](https://hangar.papermc.io/Axiumyu/PaperDialogDSL) (Kotlin DSL + routing, Paper 1.21.10+, MIT). Both tiny and unproven.

### Paper native API (verified on [jd.papermc.io 26.3](https://jd.papermc.io/paper/26.3/))

- **`MenuType` constants → view type** ([MenuType](https://jd.papermc.io/paper/1.21.11/org/bukkit/inventory/MenuType.html), `@Experimental`): GENERIC_9X1…9X6, GENERIC_3X3, CARTOGRAPHY_TABLE, CRAFTING, GRINDSTONE, HOPPER, SHULKER_BOX, SMITHING → `InventoryView`; CRAFTER_3X3 → `CrafterView`; ANVIL → `AnvilView`; BEACON → `BeaconView`; BLAST_FURNACE/FURNACE/SMOKER → `FurnaceView`; BREWING_STAND → `BrewingStandView`; ENCHANTMENT → `EnchantmentView`; LECTERN → `LecternView`; LOOM → `LoomView`; MERCHANT → `MerchantView`; STONECUTTER → `StonecutterView`.
- `MenuType.Typed<V,B>`: `builder(): B`, `create(HumanEntity, Component)` (String overload deprecated 1.21). Builders: `InventoryViewBuilder.title(Component)/copy()/build(HumanEntity)`, `LocationInventoryViewBuilder.location()/checkReachable()`, `MerchantInventoryViewBuilder`. Open with `InventoryView.open()`; every `openAnvil/openWorkbench/openMerchant…` is deprecated since 1.21.4 in favour of MenuType ([HumanEntity](https://jd.papermc.io/paper/26.3/org/bukkit/entity/HumanEntity.html)).
- `InventoryView.setTitle(String)` is **deprecated since 1.21.1** — "changing the title is not supported … poorly defined and broken behaviors"; no `setTitle(Component)`. A title change is a new view + reopen (what IF/Triumph do) ([InventoryView](https://jd.papermc.io/paper/26.3/org/bukkit/inventory/InventoryView.html)).
- **Anvil text without NMS: yes.** `AnvilView.getRenameText()`, `get/setRepairCost`, `setRepairItemCountCost`, `setMaximumRepairCost`, `bypassEnchantmentLevelRestriction`; `PrepareAnvilEvent.getView(): AnvilView` + `setResult()`; "custom anvil results are disabled by default — repair cost must be ≥ 0 for a set result to show" ([PrepareAnvilEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/inventory/PrepareAnvilEvent.html)). **No `setRenameText`** — pre-filling the field still needs the left item's display name or packets. `MerchantView.getMerchant()` + `MerchantRecipe` for offers. Still NMS/packets: pre-seeded anvil text, cursor-independent fake slots, per-viewer item variants in a shared inventory.
- **Dialogs** (MC 1.21.6, Paper 1.21.7+; [docs](https://docs.papermc.io/paper/dev/dialogs/)): `Dialog.create(builder)`; `DialogBase.Builder.externalTitle/canCloseWithEscape/pause/afterAction(CLOSE|NONE|WAIT_FOR_RESPONSE)/body/inputs`; `DialogType.notice/confirmation/multiAction/serverLinks/dialogList`; `DialogInput.text/bool/numberRange/singleOption`; `DialogAction.staticAction/customClick(Key, …)/customClick(DialogActionCallback, Options)/commandTemplate`; `Audience.showDialog`/`closeDialog`; responses via `PlayerCustomClickEvent.getDialogResponseView().getText/getBoolean/getFloat(key)` — "not guaranteed to be an actual dialog response — validate". Event thread UNVERIFIED.

### Click semantics a GUI library must cover ([ClickType](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/ClickType.html), [InventoryAction](https://jd.papermc.io/paper/1.21.11/org/bukkit/event/inventory/InventoryAction.html))

`ClickType`: LEFT, SHIFT_LEFT, RIGHT, SHIFT_RIGHT, WINDOW_BORDER_LEFT/RIGHT, MIDDLE, NUMBER_KEY, DOUBLE_CLICK, DROP, CONTROL_DROP, CREATIVE, SWAP_OFFHAND, UNKNOWN. `InventoryAction`: NOTHING, PICKUP_ALL/SOME/HALF/ONE, PLACE_ALL/SOME/ONE, SWAP_WITH_CURSOR, DROP_ALL/ONE_CURSOR, DROP_ALL/ONE_SLOT, MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD (deprecated 1.20.6, never fires), HOTBAR_SWAP (number keys **and** offhand), CLONE_STACK, COLLECT_TO_CURSOR, UNKNOWN, plus six bundle actions.

1. Bottom shift-click: `clickedInventory == bottom && action == MOVE_TO_OTHER_INVENTORY` — the item enters the GUI with no top-slot click.
2. `NUMBER_KEY`/`SWAP_OFFHAND` on a top slot → `HOTBAR_SWAP`, `getHotbarButton()` 0–8 or −1 (offhand): swaps *into* the GUI from the hotbar.
3. `DOUBLE_CLICK` on a bottom slot → `COLLECT_TO_CURSOR` pulls matching items **out of the top inventory**.
4. `InventoryDragEvent.getRawSlots()` may span both inventories; `getNewItems()` is raw-slot keyed; cancelling discards all placements; no per-slot clicks.
5. Creative `MIDDLE` → `CLONE_STACK`; `DROP`/`CONTROL_DROP` → `DROP_ONE_SLOT`/`DROP_ALL_SLOT`; outside clicks → `DROP_*_CURSOR`.
6. `InventoryCreativeEvent` for creative set-slot of the *player* inventory while a GUI is open.
7. Javadoc: never call `closeInventory`/`openInventory` inside click/drag handlers — schedule next tick; bottom-inventory edits need `Player.updateInventory()`.

### Deep dive 4 — GUI diffing

- **The server already diffs at the packet level.** `CraftInventory.setItem` only mutates the container (`setChanged`), no packet. Each player tick, `containerMenu.broadcastChanges()` runs when `--containerUpdateDelay <= 0` (Paper `tick-rates.container-update`, default 1; [Paper patch](https://raw.githubusercontent.com/PaperMC/Paper/main/paper-server/patches/sources/net/minecraft/server/level/ServerPlayer.java.patch)). `broadcastChanges` iterates every slot: `triggerSlotListeners` compares `lastSlots`; `synchronizeSlotToRemote` does `if (!remoteExpected.matches(current))` → one `ClientboundContainerSetSlotPacket`; full `ClientboundContainerSetContentPacket` only on open/`broadcastFullState` (vanilla 1.21.11 `AbstractContainerMenu`). **Caveat**: stacks with different PDC UUIDs do not match — a `clear()`+repopulate with fresh random tags resends every slot. IF reuses a stable per-`GuiItem` UUID; Triumph tags `mf-gui` per `GuiItem`.
- **Who diffs**: IF no (clear + full display, viewers migrated on dirty). Triumph no (clear + populate; `updateItem` per slot exists). KSpigot no (clear + set, repeated per tick during animations). **InvUI yes** — dirty-slot bitset at `Item` level and `RemoteSlot` packet diff in its own menu, bypassing Bukkit events entirely (at the price of NMS, single-version artifacts and invisibility to other plugins).
- **Sculk 5 today** (`GuiSession.refreshAll`) also writes every slot via `inv.setItem` — no diff.
- **Conclusion**: for a Bukkit-inventory library, a slot-level diff saves `ItemStack`/`ItemMeta` construction, serialisation, holder lookups and listener churn, and removes the risk of a visible blank between `clear()` and repopulate (anything async or deferred will flash). It does **not** reduce packets unless items carry unstable identity. A render-state diff over a Bukkit inventory, with stable item identity, is a differentiator no MIT/Unlicense library offers today; InvUI's is tied to NMS.

### Folia survey ([Folia README](https://github.com/PaperMC/Folia/blob/ver/26.2.x/README.md), [Paper docs](https://docs.papermc.io/paper/dev/folia-support/))
`InventoryClickEvent` fires on the clicking player's region thread, so a render triggered inside the event is already on the right thread; deferred work must use `player.getScheduler()`. IF breaks (`Bukkit.getScheduler()`), KSpigot breaks (`runTaskTimer`), Triumph is Folia-aware via reflection, InvUI is correct by design. None ships `folia-supported` (none is a plugin).
## C. Configuration

### Headline findings

1. **kaml is archived** (read-only since 2025-11-30; last release 0.104.0 2025-11-18 — the version Sculk 5 pins). README: "kaml is no longer maintained… Maintained forks are welcome" ([repo](https://github.com/charleskorn/kaml)). Successor: **kotaml** (Heapy, Apache-2.0, `io.heapy.kotaml:kotaml`, 0.111.0 · 2026-09-16, pushed 2026-10-10, 28 stars) — now the YAML entry in kotlinx.serialization's [formats list](https://github.com/Kotlin/kotlinx.serialization/blob/master/formats/README.md). Underlying parser `it.krzeminski:snakeyaml-engine-kmp` (5.0.0 · 2026-10-08) is independently active.
2. **Configurate's released YAML loader neither reads nor writes per-node comments.** `YamlConfigurationLoader` @4.2.0 registers only the `HASH` header comment handler, sets `setProcessComments(false)`, and saves via `yaml.dump(node.raw())` ([source](https://github.com/SpongePowered/Configurate/blob/4.2.0/format/yaml/src/main/java/org/spongepowered/configurate/yaml/YamlConfigurationLoader.java)). Full YAML comments live in two **open** PRs: [#175](https://github.com/SpongePowered/Configurate/pull/175) (draft since 2020-10) and [#410](https://github.com/SpongePowered/Configurate/pull/410) (2023-06). HOCON and XML loaders do round-trip comments.
3. **Classpath reality**: Velocity exposes Configurate 4.2.0 (hocon/yaml/gson) + snakeyaml 2.5 as `api` ([build](https://github.com/PaperMC/Velocity/blob/dev/3.0.0/api/build.gradle.kts)); Paper exposes snakeyaml 2.2 + gson as `api` ([build](https://github.com/PaperMC/Paper/blob/main/paper-api/build.gradle.kts)); nobody ships kotlinx. configurate-yaml shades its own patched SnakeYAML, so it never conflicts with the server's.

### Matrix

| Project | Version · date | Licence | Maintenance | Java / KMP | Formats | Comments write / preserve | Unknown keys | Migrations | Diagnostics | Kotlin | Deps · jar | Complaints |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| [Configurate](https://github.com/SpongePowered/Configurate) | 4.2.0 · 2025-02-26 (prev 4.1.2 2021-08); master 4.3.0-SNAPSHOT, pushed 2026-10-09 | Apache-2.0 | active, slow releases | Java 8 runtime (multi-release jar) | YAML(1.1), HOCON, Gson, Jackson, XML | HOCON/XML yes/yes; **YAML header-only / no** | **preserved** (`ObjectMapper.save` writes fields into the existing node, never clears siblings) | `ConfigurationTransformation.versionedBuilder().versionKey().addVersion(int, t)`, `NodePath` wildcards | `ConfigurateException.path()`; **no line/column** | `extra-kotlin`: `node.get<T>()`, data classes **via kotlin-reflect**, Flow bridges | core 364 KB + yaml 379 KB (shaded snakeyaml) + geantyref + option | YAML comments open since 2020; 3.5 yrs between releases |
| [kaml](https://github.com/charleskorn/kaml) → [kotaml](https://github.com/Heapy/kotaml) | kaml 0.104.0 · 2025-11-18 **archived**; kotaml 0.111.0 · 2026-09-16 | Apache-2.0 | kaml dead; kotaml monthly | JVM 11; JS/Wasm experimental | YAML 1.2 | **yes** (`@YamlComment`, `CommentEvent` in `YamlOutput`) / **no** (parser never enables comments; `YamlNode` has no comment field) | `strictMode=true` throws `UnknownPropertyException`; `false` drops | none | **path + line/column** on every `YamlException` subtype (`IncorrectType`, `MissingRequiredProperty`, `UnknownProperty`, `DuplicateKey`, `UnknownPolymorphicType`…) | native kotlinx: sealed, polymorphism `Tag`/`Property`/`None`, contextual | kaml 198 KB + engine-kmp 461 KB + okio | archived upstream; small fork |
| [snakeyaml-engine](https://bitbucket.org/snakeyaml/snakeyaml-engine) | 3.2 · 2026-10-03 | Apache-2.0 | active | Java 11 | YAML 1.2 | parse + emit comments since 2.3 (`LoadSettings…parseComments`, `DumpSettings…dumpComments`, `CommentLine` on nodes — exact accessor spelling UNVERIFIED) | n/a (node tree) | n/a | mark line/col on nodes | plain Java | 309 KB, 0 deps | low-level |
| snakeyaml (classic) | 2.5 · 2025-08-31 | Apache-2.0 | active | Java 8 | YAML 1.1 | `LoaderOptions/DumperOptions.setProcessComments`; `Node.blockComments/inLineComments/endComments` | n/a | n/a | marks | plain Java | 340 KB | what Paper (2.2) and Velocity (2.5) expose |
| [eo-yaml](https://github.com/decorators-squad/eo-yaml) | 8.0.6 · 2024-08-15 | BSD-3-Clause | slow (pushed 2024-11) | Java 8 | YAML 1.2 subset | reads above-comments + inline; printer emits `node.comment()` | n/a | n/a | — | plain Java | 0 deps | no anchors/aliases/tags; round-trip fidelity UNVERIFIED |
| [boosted-yaml](https://github.com/dejvokep/boosted-yaml) | 1.3.7 · 2024-08-08, pushed 2026-05 | Apache-2.0 | semi-active | Java 8 | YAML | **full** (`Block` keeps before/inline/after comments on key and value; `ExtendedRepresenter` writes them back) | removed unless `setKeepAll(true)` | `BasicVersioning`, relocations, mappers, ignored routes, `MergeRule` | — | untyped route/Map API | shades snakeyaml-engine | untyped |
| [ConfigLib](https://github.com/Exlll/ConfigLib) | 4.8.1 · 2026-02-09 | MIT | active | Java 17 | YAML | writes `@Comment` by **post-processing dumped text** / **no** | `update()` drops | none | — | records | snakeyaml-engine | fragile text splicing |
| Spigot/Paper `YamlConfiguration` | in server | GPL-3.0 server class | — | 21 | YAML 1.1 | `getComments/setComments/getInlineComments` (since 1.18.1) | map API keeps all | none | — | untyped | — | untyped |
| Lightbend Config (HOCON) | 1.4.9 · 2026-06-03 | Apache-2.0 | active | Java 8 | HOCON | origin comments kept; `ConfigRenderOptions.setComments(true)` renders; render reformats whole doc | yes (tree) | none | origin line numbers | via `kotlinx-serialization-hocon` (JVM-only, experimental) | — | — |
| [night-config](https://github.com/TheElectronWill/night-config) | 3.9.0 · 2026-06-06 | **LGPL-3.0 — flag** | active | Java 8 | TOML, HOCON, JSON, YAML | TOML/HOCON r/w; **YAML `supportsComments()=false`** | yes | none | — | Java | — | licence rules it out for a proprietary bundle |
| [ktoml](https://github.com/akuleshov7/ktoml) | 0.7.1 · 2025-08-22 | **MIT** | 53 open issues | KMP | TOML | `@TomlComments` written; parser keeps `comments`/`inlineComment` on `TomlNode`, but decode→encode loses them (UNVERIFIED end-to-end) | `ignoreUnknownNames` | none | — | native kotlinx | +okio for `ktoml-file` | mixed arrays, tree-encoder limits |
| [tomlkt](https://github.com/Peanuuutz/tomlkt) | 0.6.1 · 2026-07-27 | Apache-2.0 | active | KMP | TOML | `@TomlComment/@TomlInline/@TomlMultilineString` written; "annotation metadata ignored in the parser" → no preservation | `ignoreUnknownKeys` | none | — | native kotlinx | coords now `dev.eav.tomlkt:tomlkt` | — |
| kotlinx-serialization-hocon | tracks kotlinx 1.12.0-RC | Apache-2.0 | official, experimental | JVM only | HOCON | none (Lightbend renders) | n/a | none | origin | native | Lightbend Config | no comment annotations |

### Deep dive 5 — Configurate vs kotlinx/kaml(kotaml)

| Criterion | Configurate 4.2.0 | kotlinx + kaml/kotaml |
|---|---|---|
| Comment write (annotated) | HOCON yes; **YAML no** (header only) | Yes (`@YamlComment`) |
| Comment preserve (user-written) | HOCON/XML yes; **YAML no** | **No** |
| Unknown-key preservation | **Yes** by construction (ObjectMapper writes into the existing node) | No (strictMode drops or throws) unless a side `YamlNode` tree is kept |
| Migrations | Built-in versioned transformations with wildcard `NodePath` | None; hand-roll on `YamlNode` |
| Validation diagnostics | `NodePath` only, no line/col | **Path and line/column**, one exception type per failure class |
| Kotlin data/sealed/polymorphic | data classes via kotlin-reflect (extra-kotlin); no sealed/polymorphism | Native: sealed, tag or property discriminator, contextual |
| Default generation | field initialisers + `@Comment`; `node.set(obj)` | `encodeDefaults` + annotations |
| Reload/watch | `WatchServiceListener`, `ConfigurationReference` (+ `errors()` publisher), Flow bridge | None |
| Performance | reflection + geantyref, boxed node tree | compile-time serializers; engine-kmp slower than Java engine — magnitude UNVERIFIED |
| Footprint | ~745 KB + geantyref + option; +kotlin-reflect (~3 MB) for data classes | ~660 KB over serialization-core already present |
| On server classpath | Velocity yes (api); Paper no | never shipped |
| Maintenance | active but slow; YAML comment PRs stalled 5 years | kaml archived; kotaml small but monthly |

### What "comment preservation + unknown-key preservation + generated defaults" realistically requires

- **(a) Node-tree architecture** (Configurate, boosted-yaml, raw snakeyaml-engine with `parseComments`): the typed object is a projection; save writes fields back into the *existing* tree, so untouched nodes, comments and unknown keys survive by construction. Configurate has the write-back half but its YAML reader discards comments; boosted-yaml has both halves but is untyped and drops unknown keys unless `keepAll`.
- **(b) Serializer architecture** (kotlinx + kaml/kotaml/ktoml/tomlkt): save regenerates the file from the object; comments come only from annotations; user comments and unknown keys are lost. Nothing in this family preserves user comments today.
- **Sculk 5 today**: kaml render + `CommentedYaml` (annotation comments injected by path) + `ConfigMerge` (text-level, **append-only**: file on disk is the base, existing lines copied byte-for-byte, missing keys appended; sequences opaque). This already preserves user comments and unknown keys, at the cost of declaration-order drift and no key removal — honest but fragile for the same reasons ConfigLib's text splicing is (multi-line scalars, flow collections).
- **Hybrid nobody ships for Kotlin** (what Sculk 6 would build): (1) parse once with snakeyaml-engine `parseComments=true` into a `Node` tree; (2) decode the typed object from that tree (kotaml `decodeFromYamlNode` keeps line/column diagnostics); (3) on save, encode object → fresh tree, then **merge into the old tree** per path: replace scalar values only, keep old node comments else take annotation comment, keep unknown subtrees, append new defaults with their `@Comment`. This is boosted-yaml's `Merger` + `Block` carry-over, typed. Closest existing: boosted-yaml updater (untyped), Configurate PR #175 branch (unreleased). Cost: a node-level emitter; kaml's emitter already emits `CommentEvent`s, so a kotaml patch switching the parser to `parseComments` is the smallest path — but then you own the fork.
## D. Data

Versions below come from `repo1.maven.org` `maven-metadata.xml` and GitHub release dates (the `search.maven.org` index was stale). Sculk 5 pins: HikariCP 7.1.0, Caffeine 3.2.4, Lettuce 7.6.0, sqlite-jdbc 3.53.2.1, mariadb 3.5.10, postgresql 42.7.13, H2 2.4.240.

### Matrix

| Project | Version · date | Licence | Maintenance | Java | Kotlin | Key features | Why used | Limitations | Jar · deps |
|---|---|---|---|---|---|---|---|---|---|
| [Exposed](https://github.com/JetBrains/Exposed) | 1.5.1 · 2026-10-09; 1.0.0 GA 2026-01-22 | Apache-2.0 | monthly; 180 open issues; pushed 2026-10-11 | 8+ core/jdbc, 11+ r2dbc, 17+ spring | native; Kotlin 2.2+ | DSL + DAO, `suspendTransaction()`, R2DBC module, `exposed-migration` script generation, json/datetime/crypt/money | the Kotlin SQL framework | **pulls `kotlin-reflect`** (banned by Sculk release checklist), DAO `EntityCache` stale reads ([#653](https://github.com/JetBrains/Exposed/issues/653) open since 2019), 1.0 package rename, `forUpdate` deadlock in suspended tx ([#2411](https://github.com/JetBrains/Exposed/issues/2411)) | core 1.17 MB; deps stdlib, kotlin-reflect, coroutines, kotlinx-datetime compat shim, slf4j ([POM](https://repo1.maven.org/maven2/org/jetbrains/exposed/exposed-core/1.5.1/exposed-core-1.5.1.pom)) |
| [Flyway](https://github.com/flyway/flyway) Community | 13.10.0 · 2026-10-08 | Apache-2.0 | weekly | **21+** in v13 | Java API | V/U/R migrations, checksums, history table, baseline, callbacks | industry standard | heavy for a plugin; Redgate steering | flyway-core 815 KB; required jackson-annotations + jetbrains-annotations ([POM](https://repo1.maven.org/maven2/org/flywaydb/flyway-core/13.10.0/flyway-core-13.10.0.pom)) |
| [Liquibase](https://github.com/liquibase/liquibase) | 5.0.4 · 2026-08-20 | **FSL-1.1-ALv2** (not OSI; converts to Apache-2.0 two years after each release) ([LICENSE](https://raw.githubusercontent.com/liquibase/liquibase/master/LICENSE.txt)) | quarterly | 17+ | Java | changesets (id+author+file), preconditions, rollback, `DATABASECHANGELOG` | enterprise | licence, 3.2 MB core | 3.16 MB |
| [HikariCP](https://github.com/brettwooldridge/HikariCP) | 7.1.0 · 2026-06-14 | Apache-2.0 | slow but alive (544 open issues) | 11+ (4.0.3 for Java 8) | plain Java | fastest JDBC pool; metrics; leak detection | default everywhere | SQLite semantics unaddressed by maintainers | **173 KB; one dep: slf4j-api 2.0.17** ([POM](https://repo1.maven.org/maven2/com/zaxxer/HikariCP/7.1.0/HikariCP-7.1.0.pom)) |
| [Caffeine](https://github.com/ben-manes/caffeine) | 3.3.0 · 2026-09-21 | Apache-2.0 | very active | 11+ | `CompletableFuture` API → `await()` | W-TinyLFU, `AsyncLoadingCache`, `refreshAfterWrite`, variable `Expiry`, stats, listeners, `Scheduler` | best JVM cache | no coroutine API upstream | 1.06 MB; annotation-only compile deps |
| [Lettuce](https://github.com/redis/lettuce) | 7.8.0 · 2026-09-24 | **MIT** (relicensed; LICENSE "Copyright (c) 2023-Present, Redis Ltd.") | monthly; 203 open | 8+ | built-in `RedisCoroutinesCommands` (`@ExperimentalLettuceCoroutinesApi`, needs kotlinx-coroutines-reactive) | async/reactive, cluster, Valkey wire-compatible | de-facto async client | **Netty 4.2.17 + reactor-core 3.8.7 required** → relocate or do not shade | 2.85 MB + netty + reactor ([POM](https://repo1.maven.org/maven2/io/lettuce/lettuce-core/7.8.0.RELEASE/lettuce-core-7.8.0.RELEASE.pom)) |
| [Jedis](https://github.com/redis/jedis) | 8.0.2 · 2026-10-07 (8.0.0 RESP3 default, breaking) | MIT | monthly | 8 | blocking → `withContext` | simple pooled sync client | lightweight | blocking; pool per thread | 1.29 MB; slf4j, commons-pool2, org.json, gson |
| [valkey-glide](https://github.com/valkey-io/valkey-glide) Java | 2.5.3 · 2026-09-24 | Apache-2.0 | very active; 512 open | 8+ (11+ rec.) | Java futures | Rust core via FFI, cluster-aware | Valkey-official | **per-platform native jars 7.5–8.4 MB each, uber jar 41 MB**; README warns of shaded netty/protobuf conflicts | 41 MB uber |
| [valkey-java](https://github.com/valkey-io/valkey-java) | 5.5.0 · 2025-10-10 | MIT | **no release in 12 months** | 8+ | as Jedis | Jedis 5.x fork | — | lagging | — |

JDBC driver licences (shading matters): sqlite-jdbc Apache-2.0; **mysql-connector-j GPL-2.0 with Universal FOSS Exception**; **mariadb-java-client LGPL-2.1-or-later**; postgresql BSD-2-Clause. Paper 1.21.11 ships `sqlite-jdbc 3.49.1.0` and `mysql-connector-j 9.2.0` as server `runtimeOnly` ([paper-server build](https://raw.githubusercontent.com/PaperMC/Paper/ver/1.21.11/paper-server/build.gradle.kts)) — plugins need bundle neither; load Postgres/MariaDB via the `paper-plugin.yml` library loader.

### Exposed specifics
- 1.0 renamed `newSuspendedTransaction()` → `suspendTransaction()` (JDBC and R2DBC overloads), `withSuspendTransaction()` → `inTopLevelSuspendTransaction()`, removed `suspendedTransactionAsync()` and the context parameter ("wrap in `withContext`"), removed `ThreadLocalTransactionManager` ([migration guide](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)). Connection-leak-on-cancel bugs fixed in 1.1.0 and 1.3.0 ([CHANGELOG](https://raw.githubusercontent.com/JetBrains/Exposed/main/CHANGELOG.md)).
- R2DBC drivers listed: H2, MariaDB, MySQL (asyncer), Oracle, PostgreSQL, SQL Server; **SQLite and Redshift are JDBC-only** ([databases page](https://www.jetbrains.com/help/exposed/working-with-database.html)). `exposed-dao` is JDBC-only. Only 7 R2DBC-tagged issues ever (2 open) — low usage.
- Migrations: `MigrationUtils.statementsRequiredForDatabaseMigration()`, `generateMigrationScript()`, `dropUnmapped*Statements()`; Exposed "does not apply generated migration scripts" and points to Flyway/Liquibase ([migrations page](https://www.jetbrains.com/help/exposed/migrations.html)). Gradle (1.3.0) and Maven (1.4.0) plugins generate scripts.

### Flyway / Liquibase concepts worth borrowing
- Flyway: `V` versioned, `U` undo, `R` repeatable (re-run when checksum changes, after versioned); baseline; `validateOnMigrate` default true; checksum recomputed each run and errors on drift ([concepts](https://documentation.red-gate.com/flyway/flyway-concepts/migrations)). `flyway_schema_history`: `installed_rank` (PK), `version`, `description`, `type`, `script`, `checksum`, `installed_by`, `installed_on`, `execution_time`, `success`. Out-of-order/callback details UNVERIFIED (pages 404'd).
- Liquibase: changeset identity = id + author + file path; `runOnChange`/`runAlways`; preconditions; rollback blocks; `DATABASECHANGELOG` adds `DEPLOYMENT_ID` grouping, `EXECTYPE`, `CONTEXTS/LABELS` ([tracking table](https://docs.liquibase.com/concepts/tracking-tables/databasechangelog-table.html)).
- Minecraft plugins bundling Flyway exist but are niche (Dominion, Medieval-Factions, FactionWarps, LiteEco) — mostly Kotlin plugins.

### HikariCP guidance ([wiki](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing), [README](https://raw.githubusercontent.com/brettwooldridge/HikariCP/dev/README.md))
- `connections = cores × 2 + effective_spindles`; "a few dozen at most"; deadlock-free minimum `Tn × (Cm − 1) + 1`.
- Defaults: `maximumPoolSize` 10; `minimumIdle` = max (fixed pool recommended); `connectionTimeout` 30 s; `idleTimeout` 10 min; `maxLifetime` 30 min, "several seconds shorter than any database or infrastructure time limit"; `keepaliveTime` 2 min; `leakDetectionThreshold` ≥ 2000 ms. MySQL: `cachePrepStmts`, `prepStmtCacheSize=250`, `prepStmtCacheSqlLimit=2048`, prefer `jdbcUrl`.
- slf4j is required and satisfied: Mojang 1.21.11 ships `slf4j-api 2.0.17` + `log4j-slf4j2-impl`; Velocity ships slf4j 2.0.17.
- SQLite: single writer; pool size 1 + `journal_mode=WAL` + `busy_timeout` is community practice, not a maintainer statement (UNVERIFIED as official). 7.1.0 fixed a virtual-thread spin in `ConcurrentBag`.

### R2DBC state (Oct 2026)

| Artifact | Latest · date | Licence | Netty / notes |
|---|---|---|---|
| r2dbc-spi | 1.0.0 · 2022-04-25 | Apache-2.0 | spec frozen 4.5 years |
| r2dbc-postgresql | 1.1.3 · 2026-09-21 | Apache-2.0 | **Netty 4.1.138**, reactor-core 3.5.20 |
| r2dbc-mysql (asyncer) | 1.4.3 · 2026-07-21 | Apache-2.0 | Netty 4.1.118 |
| r2dbc-mariadb | 1.4.2 · 2026-09-23 | **Apache-2.0** (the LGPL flag applies to the JDBC connector only) | Netty 4.2.15 |
| r2dbc-h2 | 1.1.0 · 2025-10-20 | Apache-2.0 | "test database" per r2dbc.io; near-dormant |
| r2dbc-pool / -proxy | 1.0.2 (2024-10) / 1.1.6 (2025-05) | Apache-2.0 | slow |
| SQLite | **no driver exists** ([r2dbc.io/drivers](https://r2dbc.io/drivers/)) | — | — |

Netty landscape: Mojang 1.21.11 = Netty 4.2.7; Paper main 4.2.16; Velocity 4.2.15; Lettuce 7.8 4.2.17; r2dbc-postgresql/mysql still 4.1.x. Bukkit plugin classloaders resolve server classes first, so unrelocated shaded Netty is silently replaced by the server copy — relocate or do not shade Netty-based clients.

### Coroutines + JDBC
- `Dispatchers.IO.limitedParallelism(n)` is "safe to replace `newFixedThreadPoolContext(n)`" because IO is elastic ([API doc](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/limited-parallelism.html)); sizing it to Hikari `maximumPoolSize` means no coroutine ever blocks inside `getConnection()`.
- Loom: JEP 491 (Java 24) removed pinning on `synchronized`; Paper's floor is Java 21 (26.1+ builds target Java 25 — see area E), and MySQL Connector/J still pins on `synchronized(connectionMutex)` ([bug 109346](https://bugs.mysql.com/bug.php?id=109346)); pgjdbc ≥ 42.6 uses `ReentrantLock`. kotlinx has no official virtual-thread dispatcher guidance.

### Caffeine specifics ([Refresh](https://github.com/ben-manes/caffeine/wiki/Refresh), [Population](https://github.com/ben-manes/caffeine/wiki/Population))
- `refreshAfterWrite` = stale-while-revalidate: first stale read triggers an async reload, the old value is returned, concurrent refreshes are deduplicated, a failed reload keeps the old value. `buildAsync` → `AsyncLoadingCache<K, CompletableFuture<V>>` with overridable executor and `loadAll` bulk. Stampede protection: `Cache.get(key, fn)` is atomic per key (`computeIfAbsent` semantics; Javadoc fetch failed — UNVERIFIED by direct quote, widely documented).
- Kotlin bridging: no upstream coroutine API; `AsyncLoadingCache` + `future.await()` or `CoroutineScope.future {}` loaders; a third-party `caffeine-coroutines` exists. Velocity bundles Caffeine 3.2.3.

### Redis/Valkey server licences
Redis: RSALv2/SSPLv1 since 2024-03; AGPLv3 added as an option from Redis 8 (2025-05) ([redis.io](https://redis.io/blog/agplv3/)). Valkey: BSD-3-Clause. Client licences are unaffected; server choice is the user's. valkey.io lists GLIDE Java, valkey-java and Redisson as tested clients; Lettuce/Jedis are wire-compatible (Spring Data Valkey uses Lettuce).

### Deep dive 6 — R2DBC vs JDBC: **JDBC + HikariCP + bounded IO dispatcher**
1. SQLite — Sculk's default and test store — has **no R2DBC driver**.
2. R2DBC SPI unchanged since 2022; r2dbc-h2 near-dormant; Exposed R2DBC has ~2 open issues (few users); DAO unsupported.
3. Each R2DBC driver drags reactor-core + Netty (4.1 for pg/mysql) into a process already running Netty 4.2 — relocation or breakage.
4. Plugin workloads are small point queries at low concurrency; a 4–10 connection pool + `limitedParallelism(poolSize)` never blocks a game thread.
5. Hikari 173 KB vs multi-MB reactive stacks that every product jar would shade.
Where R2DBC would help — a Velocity proxy doing thousands of concurrent lookups — the right fix is Caffeine `refreshAfterWrite` in front plus Lettuce (already async, Netty 4.2-aligned), not reactive SQL. Keep the repository API `suspend` so an R2DBC transport stays additive.
## E. Packets

### Ground truth: Minecraft versions in 2026 ([version history](https://minecraft.wiki/w/Java_Edition_version_history))

| MC | Full release |
|---|---|
| 1.21.9 / 1.21.10 / 1.21.11 | 2025-09-30 / 2025-10-07 / 2025-12-09 |
| 26.1 / 26.1.1 / 26.1.2 | 2026-03-24 / 04-01 / 04-09 |
| 26.2 "Chaos Cubed" | 2026-06-16 |
| **26.3 "Wilderness Bound" (current)** | **2026-09-15** |
| 26.4 | snapshots since 2026-09-22, "upcoming Q4 2026" |

Paper 26.3 javadoc is `paper-api 26.3.build.157-beta` ([jd](https://jd.papermc.io/paper/26.3/)); Paper's 26.3 post is dated 2026-09-26 ([news](https://papermc.io/news/26-3)). Sculk 5's catalog still pins `paper 1.21.11` and `packetevents 2.13.0` / `protocollib 5.4.0`.

### Matrix

| Project | Version · date | Licence | Maintenance | MC / Java | Paper · Folia · Velocity · Fabric | Kotlin | Why used | Limitations | Jar · deps |
|---|---|---|---|---|---|---|---|---|---|
| [PacketEvents](https://github.com/retrooper/packetevents) | **2.14.0 · 2026-09-23** ("mainly 26.3 support"); `2.14.1-SNAPSHOT` (2026-10-06) | **GPL-3.0** ([LICENSE](https://raw.githubusercontent.com/retrooper/packetevents/2.0/LICENSE), [FAQ](https://docs.packetevents.com/faq)) | high; multi-contributor (booky10 protocol, retrooper releases); 133 open; pushed 2026-10-06 | 1.7 → 26.3; api Java 8; spigot module Java 21 for 1.20.5–1.21.11, **Java 25 for 26.1+**; Velocity module Java 25, min Velocity 4.1.1 | Y · Y (`folia-supported: true`, `FoliaScheduler`; open crash [#1458](https://github.com/retrooper/packetevents/issues/1458), tps [#1421](https://github.com/retrooper/packetevents/issues/1421) unanswered) · Y · Y (+Bungee, Sponge) | JSpecify `@NullMarked` packages; Sept 2026 commits "Mark getPlayer as NullUnmarked for Kotlin nullable casts" | protocol-version-neutral wrappers, no NMS, same-week MC support, ViaVersion-aware `ClientVersion` | GPL; Spigot jar **shades Adventure 4.26.1** (only serializer/option relocated; conflict issue #974); no entity abstraction; 26.3 palette fix is a self-described hack (2026-10-06); [#1594](https://github.com/retrooper/packetevents/issues/1594) 26.3 axolotl registry bug open | spigot jar 5.7 MB; adventure (shaded), gson/netty 4.1.72 `compileOnly`, bStats |
| [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) | **5.4.0 · 2025-08-07** (1.21.4–1.21.8); since then only the rolling GitHub **"dev-build"** release (10.5 MB; changelog lists 1.21.9→26.3) | **GPL-2.0** ("only" vs "or-later" not stated in header — UNVERIFIED) ([License.txt](https://raw.githubusercontent.com/dmulloy2/ProtocolLib/master/License.txt)) | one main maintainer (dmulloy2 14 of 24 commits Jun–Oct 2026); 26 open; pushed 2026-10-06 | 1.8 → 26.3 on master (26.x dev builds only); Java 17 restored Aug 2026 | Y · Y (`folia-supported: true`, long Folia bug trail) · **N** · N | plain Java; index-based `StructureModifier` | ubiquitous softdepend | reflection into NMS/Mojang mappings → mapping fixes trail each drop (26.3: 2026-09-25 + 10-03, 10–18 days); **no stable release for 14 months**; `WrappedDataWatcher`/`WrappedDataValue` metadata API; `ci.dmulloy2.net` returns 403, `repo.dmulloy2.net` frozen at 5.4.0-SNAPSHOT (2025-07-30); Maven Central `net.dmulloy2:ProtocolLib` 5.4.0 | 10.5 MB (ByteBuddy etc.) |
| [EntityLib](https://github.com/Tofaa2/EntityLib) (Tofaa2) | master 3.3.7 · 2026-07-31; **4.0.0-alpha.2 · 2026-10-11** on `maven.pvphub.me/tofaa` (`io.github.tofaa2:{api,common,spigot,velocity}`); README claims Maven Central — **404** | **GPL-3.0** | solo maintainer; 6 open, 0 PRs; "stable only for 1.21.9+"; 4.0 = protocol codegen rewrite (614 files changed) | PE-driven | Y · ? (no Folia handling found) · Y · N | Java | `WrapperEntity` (spawn/remove/teleport/`addViewer(User\|UUID)`/`removeViewer`/`…Silently`/passengers), `EntityMeta.createMeta` per type incl. `TextDisplayMeta`/`BlockDisplayMeta`/`ItemDisplayMeta`/`InteractionMeta`/`PlayerMeta` (fake players), `ViewerEngine`; IDs via `Bukkit.getUnsafe().nextEntityId()` | GPL, not on Central, alpha churn, pinned to older PE (UNVERIFIED) | small; needs PE |
| [HologramLib](https://github.com/max1mde/HologramLib) | 1.8.5 (JitPack); pushed 2026-05-07 | GPL-3.0 | supports 1.19.4–**1.21.8 only** (stale for 26.x) | — | Paper/Folia | Java | PE-based text/item/block holograms, per-player | stale | — |
| FancyHolograms / DecentHolograms | FH moved to [fancyplugins monorepo](https://github.com/FancyInnovations/fancyplugins) (MIT, 3.0.0-SNAPSHOT Sep 2026); DH active (GPL-3.0, pushed 2026-10-09) | MIT / GPL-3.0 | active | — | Paper | — | plugins, not libraries ("display entities and packets") | — | — |
| PacketEvents own entity API / "PacketEntities" | **none found**; no 3.0 branch exists (branches: 2.0 default, master, fabric) | — | — | — | — | — | — | UNVERIFIED negative | — |

### Deep dive 7 — PacketEvents and MC 26.3
- **26.3 is supported in a release**: PR [#1582](https://github.com/retrooper/packetevents/pull/1582) opened 2026-09-14 (snapshot-tracking), merged 09-19, **2.14.0 released 2026-09-23** — 8 days after MC ([release](https://github.com/retrooper/packetevents/releases/tag/v2.14.0)); no breaking API changes listed. Post-release: "GlobalPalette BITS_PER_ENTRY for 26.3" fix 2026-10-06 (commit text: "Needs to be version-specific, but requires major refactor") and [#1594](https://github.com/retrooper/packetevents/issues/1594) (26.3 `axolotl_variant` registry, 2026-09-26, no maintainer reply) → live on `2.14.1-SNAPSHOT`.
- **Snapshot situation**: `https://repo.codemc.io/repository/maven-snapshots/` → `com.github.retrooper:packetevents-spigot:2.14.1-SNAPSHOT` (lastUpdated 2026-10-06); snapshot jars carry `+<git-hash>` in the version. Releases at `https://repo.codemc.io/repository/maven-releases/` (2.4.0 … 2.14.0). Published platform artifacts: `packetevents-{spigot,velocity,bungeecord,fabric,sponge}`; a separate `packetevents-api` artifact is UNVERIFIED.
- **No 3.x exists**; the "modern 2.x" line has been API-stable since 2.0.
- **Turnaround record (MC release → PE release)**: 1.21.9 → 2.10.0 in 19 d; 1.21.11 → 2.11.0 **same day**; 26.1 → 2.12.0 in 12 d; 26.2 → 2.13.0 in 6 d; 26.3 → 2.14.0 in 8 d. Pattern: one minor per MC drop plus 1–2 patches; snapshots within hours. 26.4: no PRs or issues yet.
- Setup/shading guidance: docs say `compileOnly`/`provided`; FAQ "Should I shade? **No.**"; bundling page warns that bundling moves the update burden to you ([the problem of bundling](https://docs.packetevents.com/introduction/the-problem-of-bundling)). If bundling anyway: `PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))` + `getSettings().checkForUpdates(false)` + `load()` in `onLoad`, `init()` in `onEnable`, `terminate()` in `onDisable`.
- API shape (verified): `PacketEvents.getAPI().getEventManager().registerListener(PacketListenerCommon | PacketListenerAbstract | SimplePacketListenerAbstract, PacketListenerPriority.LOWEST…MONITOR)`; `PacketReceiveEvent`/`PacketSendEvent` extend `ProtocolPacketEvent` (packetType, user, player, connectionState); send via `User.sendPacket(w)`, `getPlayerManager().sendPacket(player, w)`, `sendPacketSilently` (bypasses listeners), `receivePacket` (simulate inbound). Wrappers: `WrapperPlayServerSpawnEntity`, `WrapperPlayServerEntityMetadata(int, List<EntityData<?>> | EntityMetadataProvider)`, `WrapperPlayServerDestroyEntities(int...)`, `WrapperPlayServerTeams`, `WrapperPlayServerScoreboardObjective(+ScoreFormat)`, `WrapperPlayServerUpdateScore`, `WrapperPlayServerBlockChange`, `WrapperPlayServerMultiBlockChange(Vector3i chunk, Boolean trustEdges, EncodedBlock[])`, `WrapperPlayServerPlayerInfoUpdate(EnumSet<Action>, PlayerInfo…)`; `SpigotConversionUtil` for Bukkit↔PE; `ServerVersion`/`ClientVersion` (client version from ViaVersion/ProtocolSupport, else = server). Settings: `checkForUpdates`, `reEncodeByDefault`, `debug`, `kickOnPacketException`, `fullStackTrace`, `downsampleColors`, `timeStampMode`.

### Licence implications (not legal advice)
- FSF [GPL FAQ](https://www.gnu.org/licenses/gpl-faq.html): plug-ins "dynamically linked and making function calls to each other while sharing data structures" form "a single combined program"; a nonfree main program loading a GPL plug-in must be GPL-compatible. Community practice: packet libs ship as separate plugin jars and premium plugins `softdepend` without shading (PE's own FAQ says do not shade; premium listings link "Download ProtocolLib here"). No legal opinion that this escapes the GPL was found — UNVERIFIED.
- For Sculk: `sculk-packets-api` (MIT, no GPL import) is clean. `sculk-packets-packetevents` / `-protocollib` compile against GPL-3.0 / GPL-2.0. Conservative options: (a) licence those two backend modules GPL-compatible — **GPLv2-only and GPLv3 are mutually incompatible, so one module cannot straddle both**; (b) keep `compileOnly` + runtime softdepend and document the risk. Business decision; both facts sourced.

### Paper API that removes the need for packets (verified against [jd.papermc.io/paper/26.3](https://jd.papermc.io/paper/26.3/))

| Need | Paper API | Caveat |
|---|---|---|
| Per-player entity | `Entity.setVisibleByDefault(false)` + `Player.showEntity(Plugin, Entity)` / `hideEntity`; `Entity.getTrackedBy()`, `isTrackedBy(Player)` — since **1.19.4** | real server entity: chunk-bound, ticked, saved unless `setPersistent(false)`; hide/show is per-plugin |
| Holograms | `TextDisplay`/`ItemDisplay`/`BlockDisplay` via `Display`: `setInterpolationDuration/Delay`, `setTeleportDuration(0–59)`, `setTransformation(Matrix4f)`, `setBillboard`, `setViewRange`, `setBrightness`, `setGlowColorOverride` | same entity caveats |
| Client blocks | `Player.sendBlockChange(Location, BlockData)`, `sendBlockChanges(Collection<BlockState>)`, `sendMultiBlockChange(Map<? extends Position, BlockData>)`, `sendBlockDamage`, `sendBlockUpdate(Location, TileState)`, `openVirtualSign(Position, Side)` | nothing for chunk-data rewriting |
| Fake state | `sendEquipmentChange`, `sendHealthUpdate`, `sendExperienceChange`, `sendEntityEffect`, `sendPotionEffectChange(+Remove)`, `sendHurtAnimation`, `sendOpLevel`, `sendMap`, `setSendViewDistance` | HUD (`sendTitlePart`, `sendPlayerListHeaderAndFooter`, bossbar, actionbar) is Adventure `Audience` |
| Tab list | `playerListName(Component)`, `setPlayerListOrder(int)` (1.21.2+), per-viewer `unlistPlayer(Player)`/`isListed(Player)`, `setPlayerProfile` | fake tab entries still need packets |
| Paper virtual-entity API | **none**: "disguise api" [PR #11793](https://github.com/PaperMC/Paper/pull/11793) closed unmerged (2024-12); only client-side signs merged | — |

**Still impossible without packets in 2026** (derived from the absence of any API above): zero-tick, cross-chunk fake entities that do not exist server-side; per-player nametag text without scoreboard-team collisions; fake tablist players; client-only inventory tricks; rewriting outgoing chunk/light data; intercepting inbound packets (anticheat).
## Recommendation for Sculk 6

| Area | Verdict | One-line reason |
|---|---|---|
| A. Placeholders / presentation | **Native** resolver as MiniMessage `TagResolver`s with declared refresh; **integrate** MiniPlaceholders (Apache-2.0, bidirectional bridge); **abstract** PlaceholderAPI behind an optional `compileOnly` module; **leave** TAB (coexist by documenting its rules) | MiniPlaceholders is the only Component-native, Apache-licensed, Kotlin-friendly model, but it is pull-only, so the refresh contract must be Sculk's. |
| B. Inventory GUIs | **Native** on Bukkit inventories + Paper `MenuType`/`AnvilView`/`Dialog`; **leave** every library | No MIT/Unlicense library is Folia-correct, diffing and NMS-free at once; InvUI is but needs NMS per version. |
| C. Configuration | **Native** hybrid: kotlinx typed decode over a snakeyaml-engine comment-bearing node tree with tree merge on save; **integrate** kotaml (or vendored fork) + snakeyaml-engine; **leave** Configurate (except as a Velocity-side reader if ever needed) | Nothing in either family does comment + unknown-key preservation with typed Kotlin today; Configurate's YAML has no comments in any release. |
| D. Data | **Integrate** HikariCP, Caffeine, Lettuce (`compileOnly`, optional); **native** JDBC repositories, migrations and dispatcher; **leave** Exposed, R2DBC, Flyway/Liquibase (borrow their shapes) | JDBC + bounded dispatcher fits plugin workloads and SQLite; Exposed drags `kotlin-reflect`, R2DBC has no SQLite and brings Netty. |
| E. Packets | **Abstract** (keep `sculk-packets-api`); PacketEvents as the **primary backend**, ProtocolLib **demoted to best-effort**; **native** Paper-first fallbacks (`setVisibleByDefault`, `sendMultiBlockChange`, Adventure HUD); **leave** EntityLib (copy the design) | PacketEvents ships 26.x support within ~8 days with a stable 2.x API; ProtocolLib has had no stable release in 14 months. |

### Area A — details
- Native `SculkPlaceholders`: each placeholder declares `refresh: Duration | Constant | EventDriven` and a target type (`Audience` subclass), resolves to a `Tag` at parse time, and exposes a change signal the HUD driver can subscribe to — the piece neither MiniPlaceholders nor PAPI has.
- Bridge both ways with MiniPlaceholders (register Sculk placeholders as an `Expansion`; consume `MiniPlaceholders.audienceGlobalPlaceholders()` as a resolver). PAPI adapter: `compileOnly`, optional module, uses `PAPIComponents.setPlaceholders(player, component, miniMessageDeserializer)` on Paper, `String` path elsewhere, documents that expansion output is legacy-`§` text.
- Document TAB's eight rules verbatim in `docs/.../presentation/interoperability.mdx`; Sculk's nametag/sidebar features must (1) detect a foreign team/objective and yield with a logged reason, (2) offer per-player opt-out mirroring `pauseTeamHandling`, (3) never encode sort order in team names when `setPlayerListOrder` exists.

### Area B — details
- Keep Bukkit inventories + `InventoryClickEvent` (visible to anticheats and other plugins); render state is a `Map<Int, RenderedSlot>` per session, diffed before `setItem`, with stable item identity (no per-render UUIDs) so `broadcastChanges` sends nothing for unchanged slots.
- Locked by default; a complete interaction policy enumerating every `InventoryAction` incl. `HOTBAR_SWAP` from the bottom, `COLLECT_TO_CURSOR`, drags spanning the top, `CLONE_STACK`, `InventoryCreativeEvent`.
- Typed views via `MenuType.X.builder().title(Component).build(player)`; anvil input via `AnvilView.getRenameText` + `PrepareAnvilEvent`; forms via `Dialog`. Title change = new view with explicit lifecycle (no close/open callback suppression hacks).
- All deferred work through `SculkScheduler` entity overloads; the session's region thread is the click thread.

### Area C — details
- Replace kaml with kotaml now (pin `io.heapy.kotaml:kotaml`), keep `decodeFromYamlNode` for line/column diagnostics.
- Replace `ConfigMerge` (text-level append) with a node-tree merge: parse with snakeyaml-engine `parseComments=true`, encode the object to a fresh tree, merge per path (values replaced, user comments and unknown subtrees kept, new defaults appended with `@Comment`), emit with `dumpComments=true`. Adopt boosted-yaml's updater semantics (`keepAll`, ignored routes, relocations) and Configurate's versioned transformation shape for migrations.
- Keep TOML/HOCON out of scope; both kotlinx TOML libs lose user comments too.

### Area D — details
- One DB dispatcher: `Dispatchers.IO.limitedParallelism(maximumPoolSize)`; Hikari defaults verbatim with `maxLifetime` < server `wait_timeout`; SQLite pool size 1 + WAL + `busy_timeout` documented as Sculk policy.
- Migrations: keep additive `SchemaMigrator` for entity tables, add a Flyway-shaped versioned script runner with a history table (`installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success`) and checksum validation; never auto-drop.
- Cache: Caffeine `AsyncLoadingCache` + `refreshAfterWrite` behind a suspend facade; Lettuce coroutines API for Valkey/Redis, `compileOnly` + relocation guidance; Jedis documented as the lightweight alternative.
- Drivers: rely on Paper-provided sqlite/mysql; Postgres/MariaDB via `paper-plugin.yml` libraries; never shade mysql-connector-j (GPL-2 + FOSS exception) or mariadb-java-client (LGPL-2.1).

### Area E — details
- Bump catalog to PacketEvents 2.14.0 (26.3); integration-test the backend on Folia because [#1458](https://github.com/retrooper/packetevents/issues/1458) is open.
- Decide the backend modules' licence explicitly: `sculk-packets-api` stays MIT; `-packetevents` and `-protocollib` either go GPL-compatible (separately — GPLv2-only vs GPLv3 cannot share a module) or stay `compileOnly` + softdepend with the risk documented.
- Ship a packet-free `VirtualEntityService` fallback on Paper (`TextDisplay` + `setVisibleByDefault(false)` + `showEntity`, `setPersistent(false)`) so holograms work without any packet plugin; keep packets for zero-tick, cross-chunk entities and fake tablist entries.
- Named-field wrappers only (`EntityLib`-style `TextDisplayMeta`), `sendSilently` vs listener-visible send, `UnsafeValues.nextEntityId()` for ids.

### Five design decisions to copy
1. **TAB's refresh contract** (interval in ms, 50-aligned, `-1` constant, async by default with explicit sync opt-in, per-placeholder CPU accounting) — it is the only model that makes placeholder cost visible and bounded.
2. **MiniPlaceholders' placement of placeholders inside MiniMessage as typed `TagResolver`s** — it preserves the trust boundary (values never re-enter a template as text).
3. **InvUI's dirty-slot render model** (notify → bitset → re-render only dirty slots) — it is the one GUI design that makes diffing cheap, and it ports to Bukkit inventories without NMS.
4. **Configurate's write-into-existing-tree save and versioned transformations** — preservation of unknown keys by construction is what Sculk's text merge only approximates.
5. **Flyway's history table + checksum validation and Hikari's fixed-pool defaults** — proven operational shapes that cost almost nothing to implement natively.

### Five design decisions to avoid
1. **PlaceholderAPI's `String`-only expansion contract** — it forces legacy `§` colour carriage and makes every value a potential markup injection.
2. **Encoding tablist sort order in 16-character team names (TAB)** — Paper 1.21.2+ `setPlayerListOrder` makes the hack unnecessary, and it is the main source of team conflicts.
3. **`clear()`-then-repopulate redraws and reopen-to-retitle (IF, Triumph, KSpigot)** — they flash, churn allocations and break listeners.
4. **Reactive SQL (R2DBC) or Exposed as a hard dependency** — no SQLite driver, Netty on the classpath, and `kotlin-reflect` violate three Sculk constraints at once.
5. **Shading GPL packet or driver libraries (PacketEvents, ProtocolLib, mysql-connector-j, KSpigot)** — the upstream FAQs themselves say not to, and it would make the product unsellable under its MIT licence.

---

## Addendum 2026-10-12 — resource-pack library: `team.unnamed:creative`

| | Finding (GitHub API, Maven Central, 2026-10-12) |
| --- | --- |
| Licence | MIT ([unnamed/creative](https://github.com/unnamed/creative)) |
| Latest release | 1.7.3 · 2024-07-02 (`team.unnamed:creative-api` on Central); predates 1.21.4 item model definitions |
| `main` | `1.8.4-SNAPSHOT`; item models (`item/*ItemModel`), equipment layers, waypoint styles, `major.minor` pack formats (PR #89, 2026-05-01), deterministic directory read order (PR #93, 2026-07-31) |
| Publishing | `gradle.properties` targets `s01.oss.sonatype.org` (OSSRH, retired 2025-06-30); no 1.8.x artifact on Central; `repo.unnamed.team` returned nothing |
| Activity | 217 stars, 19 open issues, commits clustered in 2025-03, 2025-06, 2025-10/12, 2026-05, 2026-07 |

Verdict: fits Sculk's needs in shape, not in availability. Decision and revisit condition in ADR-0025.
