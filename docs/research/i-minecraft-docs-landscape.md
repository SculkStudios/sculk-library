# Minecraft server and modding docs: landscape, opportunities, identity

Research for the Sculk Studio 6 documentation site. Gathered 2026-11-17.

This report collects evidence. It does not propose a design. Screenshots are in
`C:\Users\Daisy\AppData\Local\Temp\claude\docs-research\screens\`.

---

## 0. Summary

1. **The ecosystem has converged on Starlight.** PaperMC (Paper, Folia, Velocity and now Adventure),
   Modrinth docs, PacketEvents, CanvasMC and the community Hytale docs all run Astro Starlight,
   most with stock layout and a single accent colour. A recoloured Starlight site therefore looks
   like PaperMC docs with a different accent, which explains the "generic" reaction. Compare
   `screens/paper-dev.png` with `screens/modrinth-docs.png`: apart from the accent they are the
   same page. The remaining sites use VitePress (Fabric, CommandAPI), Docusaurus (NeoForge, Geyser,
   Typewriter), MkDocs Material or Zensical (Cloud, InvUI), Sphinx (Sponge), GitBook (Lamp, Nexo,
   HelpChat), a XenForo wiki (Spigot), a custom Vue SPA (LuckPerms) or a custom generator (Skript).
2. **No site answers "does this API work on my server and version?" at the API level.** Platform
   support appears as prose callouts ("This guide is designed for non-Folia Bukkit servers"), as one
   plugin-wide flag (`folia-supported`), or as whole-site version dropdowns. The issue trackers
   show the cost: NeoForge #207, Folia #285, and a run of "doesn't load on 1.21.x" issues on
   CommandAPI. Sculk already records the data needed to answer it (`@Platforms`, `@SinceMinecraft`,
   `@Requires`, stability markers, capability states, jar-scan compat JSON). Rendering that data is
   the strongest differentiator available.
3. **The most praised patterns:**
   - Fabric's compiled reference mod, with "Files referenced" listed on every page.
   - CommandAPI's synced Java/Kotlin tabs and its Paper/Spigot preference toggle.
   - Geyser's "Host Provider / Self Host → pick your platform" setup flow.
   - NeoForge's per-version "Primers" porting guides.
   - Skript's generated per-element cards (Patterns, Since, Requirements).
   - The MiniMessage Viewer's preview modes (chat, lore, hologram, server list).
   - LuckPerms' in-site tools (web editor, verbose viewer).
4. **The most common failures:**
   - Stale tutorials, worst on the Spigot wiki.
   - Version labels that hide ranges (NeoForge "1.21.x").
   - Samples that don't compile.
   - Java-only samples. Paper and Velocity have no Kotlin at all.
   - API reference kept on a separate Javadoc site with no automatic linking (PaperMC #665).
   - No "getting started" path (PaperMC #666).
   - No rendered output for GUI libraries. InvUI and Triumph GUI show no menu images at all.
5. **Brand.** Sculk's real texture palette is near-black blue-grey (`#0D1217`, `#111B21`), deep
   teal (`#052A32`, `#034150`), a glow teal (`#009295` → `#29DFEB`) and a bone/sage accent
   (`#BBC39B`, `#D1D6B6`) that most "sculk" fan art leaves out. These were sampled from the vanilla
   textures. Colours are free to use. Mojang's textures, GUI sprites, logo-style lettering and the
   in-game font bitmap are not; the safe route is described in §8.

---

## 1. Method and limits

- I read the live pages with WebFetch and curl, detected each generator from HTML markers, and took
  headless screenshots one at a time (list in the appendix).
- Complaint evidence comes mostly from **GitHub issue trackers** of the docs repos (PaperMC/docs,
  FabricMC/fabric-docs, neoforged/Documentation, GeyserMC/GeyserWebsite, Incendo/cloud-docs,
  JorelAli/CommandAPI, PaperMC/Folia) and from maintainers' own notes on their sites.
- **Not reached:**
  - **Reddit:** the search tool's crawler is blocked there.
  - **Discord:** private.
  - **SpigotMC forum threads:** the forum returned 403 to the fetcher, so the Spigot wiki was judged
    from a browser screenshot only.
  - **Effect on this report:** Reddit and Discord sentiment is therefore under-represented. Where
    an issue quotes Discord (PaperMC #697, Fabric #629), I cite that issue.
- Some of the site summaries came from a fetch model reading rendered HTML. I checked the
  load-bearing claims (generator, tabs, versioning) against the raw HTML or the screenshots.

---

## 2. Landscape at a glance

| Site | Stack | Versions | Platform differences | Code samples | API reference | Notable |
| --- | --- | --- | --- | --- | --- | --- |
| PaperMC docs (Paper, Folia, Velocity, Adventure) | Astro Starlight (rewritten from Docusaurus in PR #560, Apr 2025) | Unversioned, latest only; `26.3.build.+` placeholders | Prose "Folia" callouts; separate "Folia support" page | Java only; Gradle KTS, with Maven marked "Discouraged" | Header link to jd.papermc.io, per-version Javadoc; manual inline links | "Experimental" sidebar pills; edit link; last-updated commit |
| Fabric docs | VitePress 2 alpha | Per-page version picker (26.2 back to 1.20.4); `/porting` pages | n/a (client and server only) | Java; snippets pulled from a **reference mod** that is built in CI | Links into Yarn/Mojang names; no Javadoc host | "Files referenced" and "Page authors" panels; Crowdin translations |
| NeoForge docs | Docusaurus | Dropdown with *ranges* (e.g. "1.21.9 - 1.21.10") | Client vs server notes | Java; MDG vs NeoGradle compared in a table | None linked; tells readers to read decompiled sources | "Primers" (porting) nav section; Mod Generator |
| Sponge docs | Sphinx (RTD-style) | "stable" labelled 8.0.0, while SpongeAPI branches reach `api-20` | n/a | Java | jd.spongepowered.org | 20 locales |
| Adventure / MiniMessage | Folded into PaperMC Starlight (docs.advntr.dev redirects) | Callouts such as "requires 1.19.4+" | "Natively supported" platforms | Java | jd.papermc.io/adventure | Per-tag Tag/Arguments/Examples layout; in-game screenshots; links out to webui.advntr.dev |
| LuckPerms wiki | Custom Vue SPA (LuckPermsWeb) rendering the GitHub wiki markdown | API semver changelog list | Bukkit vs Sponge sections | Java; Maven, Gradle Groovy and Gradle KTS | javadoc.io | Built-in **Tools** (web editor, verbose and tree viewers); "Thread safety", "Blocking operations" sections |
| GeyserMC wiki | Docusaurus | "Supported versions" page | **Host Provider / Self Host → platform cards** (Paper/Spigot, Fabric, NeoForge, Velocity, BungeeCord, ViaProxy, Standalone) | Config YAML | REST API docs | Dump Viewer utility; 12 locales; clear "not official Minecraft" footer |
| Modrinth docs | Starlight with starlight-openapi | n/a | n/a | HTTP | OpenAPI operation pages | Docs look like stock Starlight; the marketing site has a subtle maze pattern |
| Hangar | Nuxt app; API docs page; publishing guide in PaperMC docs | n/a | Platforms Paper/Velocity/Waterfall | — | API docs page | — |
| CommandAPI | VitePress 1.6.4 | Version dropdown (12.1.0) | **Paper ↔ Spigot preference toggle** in the right rail; Folia warnings inline | **Java / Kotlin tabs**; separate Kotlin DSL section | javadocs.commandapi.dev | "Developer's Note" callouts; page authors |
| Cloud (Incendo) | MkDocs Material 9.7.1 | v2 beta and legacy 1.x docs | One page per platform module | Java; Kotlin section; Maven / Gradle (Kotlin) / Gradle (Groovy) tabs | javadocs.dev | Feedback widget; open issues ask for Brigadier examples |
| PacketEvents | Starlight (Ion theme) | — | Lists Netty platforms but no per-platform setup | Sparse | — | Testimonials on the landing page; thin reference |
| InvUI | Zensical (the MkDocs Material successor) | Latest only ("Dropped multi-version and Spigot support in v2") | "Paper and forks"; "**not** thread-safe"; Folia "experimental" | Gradle Kotlin/Groovy/Maven tabs; `invui-kotlin` module | Javadoc **and KDoc** | No GUI screenshots |
| Triumph GUI | Custom (no generator marker) | 3.x / 4.x selector | — | — | Javadoc | No GUI screenshots; thin |
| Skript docs | Custom generator from annotations | Version badge (v2.17.1); per-element **Since** | "Requirements" field | Skript | Javadocs link | Generated cards: Patterns / Since / Required entries / Examples; "generated automatically" |
| Spigot wiki | XenForo wiki | None; stale titles | — | Java; Maven, Ant, Eclipse | hub.spigotmc.org Javadoc | Ads; titles like "Particle list 1.8.8", "auto-export your plugin using ant" |
| *Extras* | | | | | | |
| CanvasMC docs | Starlight | — | Folia-fork API differences | — | — | Another Starlight site in the same niche |
| Typewriter | Docusaurus, monospace everywhere | Version badge per page | — | — | — | **Version and Difficulty badges**, "Before starting" prerequisites |
| Misode's generators | Custom Preact app | 26.1/26.2/26.3 picker | — | JSON | — | Gold standard for interactive, version-aware MC tooling |
| Fabric template generator, NeoForge Mod Generator | Custom | MC version dropdown | — | Kotlin checkbox (Fabric) | — | Project generators are now expected |
| hytalemodding.dev | Next.js and Fumadocs | — | — | — | — | Generic shadcn black; has `llms.txt` |

---

## 3. Per-site findings

### PaperMC docs: docs.papermc.io (Paper, Folia, Velocity, Adventure)

**Stack and information architecture**
- Astro Starlight. "rewrite with Starlight" was PaperMC/docs PR #560 (Apr 2025). Adventure moved in
  with #569, and docs.advntr.dev now redirects to https://docs.papermc.io/adventure/.
- A project switcher sits at the top of the sidebar (Paper, Velocity, Folia, Adventure, Waterfall,
  Misc). Each project splits into Administration and Development.
- **Paper Development:** Getting started (Project setup, How plugins work, Paper plugins
  [Experimental pill], plugin.yml, paperweight-userdev). API (Command, Component, Event, Entity,
  Inventories, Lifecycle, Data components, PDC, Scheduling, Plugin messaging, Plugin configuration,
  Registries [experimental], Dialog API, Recipes, Particles, Folia support, Roadmap). Misc
  (Databases, Debugging, Minecraft internals, Reading stacktraces).
  https://docs.papermc.io/paper/dev/project-setup/

**Versions**
- One live version; no selector.
- Build snippets use `compileOnly("io.papermc.paper:paper-api:26.3.build.+")`.
- The page notes that before 26.1 the format was `{VERSION}-R0.1-SNAPSHOT`. That format change is a
  real trap for copy-paste snippets.
- JDK 25 is required for 26.x.

**Platforms**
- Folia appears as a coloured callout on the scheduler page ("This guide is designed for non-Folia
  Bukkit servers. If you are using Folia, you should use its respective schedulers.";
  `screens/paper-dev.png`) and as a dedicated page,
  https://docs.papermc.io/paper/dev/folia-support/.
- That page covers the `folia-supported: true` flag, the warning "Marking a plugin as supported on
  Folia is _not nearly enough_", a brand check via `ServerBuildInfo...isBrandCompatible`, and the
  four schedulers.
- There are no per-API markers.
- The Folia section itself is admin-only: Overview, Region logic, FAQ.
  https://docs.papermc.io/folia/reference/overview

**Code**
- Java only; Kotlin is never mentioned.
- Gradle KTS and Maven tabs, with Maven labelled "Discouraged".
- Velocity pages are Java with Guice `@Inject`.
- Velocity has a "Common pitfalls" page and a "Porting from Velocity 1.x.x" guide.

**API reference**
- Header link to jd.papermc.io (versioned, e.g. `/paper/26.3/`), plus hand-written inline links
  (`BukkitScheduler`).
- Open issue #665 asks for "Auto-linking to Javadocs … with a component/something."

**Good**
- Fast, accessible, and an honest "Experimental" pill in the sidebar.
- MiniMessage format pages use one consistent layout per tag (Tag / Arguments / Examples) with
  real in-game screenshots. https://docs.papermc.io/adventure/minimessage/format/

**Weak (from their own tracker)**
- #666: there is no coherent getting-started path for Adventure.
- #697: the project-creation page switches back and forth between the IntelliJ mcdev wizard and
  manual setup. One maintainer: "the templates aren't kotlin scripts, which kinda sucks". Another:
  "Both Gradle and Maven can be very overwhelming to a beginner dev."
- #663, #669, #668: no conceptual overview of chat components, and SPIs undocumented.
- #715: no hotswap guide.
- A 2024 PaperMC forum thread asked for Kotlin examples.
  https://forums.papermc.io/threads/how-create-a-command-in-paper-using-kotlin-with-gradle.1286

### Folia

- Developer guidance is one page inside Paper. A Folia user-requested issue (#285,
  https://github.com/PaperMC/Folia/issues/285) says: "It's kinda hard as a plugin developer to tell
  what the do's and don'ts are of writing a folia plugin". It asks for Paper-vs-Folia code
  side by side, which thread runs a command, listener or runnable, a fallback scheduler pattern,
  and "a full list of unsupported events/apis".
- Plugin compatibility lives in a GitHub wiki list
  (https://github.com/PaperMC/Folia/wiki/Plugin-Compatibility).
- Modrinth's loader tags include both `folia` and `canvas`
  (https://api.modrinth.com/v2/tag/loader), so "runs on Folia/Canvas" is already a vocabulary
  users filter by.

### Fabric: docs.fabricmc.net

**Stack and information architecture**
- VitePress (2.0 alpha).
- The sidebar is grouped by game concept: Getting Started, Items, Blocks, Rendering, Data
  Generation, Mixins, Loom, Loader.
- Each page has a description line under its title (`screens/fabric-docs.png`).

**Versions**
- A per-page version picker covers 26.2 back to 1.20.4 with versioned URL paths.
- Porting pages link across versions.
- Old versions carry an "may be incomplete" banner.

**Code (best in class)**
- "A fully-working mod with all the source code of this documentation" lives in `/reference`.
- Each page lists **Files referenced** with links to the real source files, and **Page authors**.
- Snippets carry line numbers and a language label.

**Translations:** Crowdin, 7+ locales. Content is CC BY-NC-SA 4.0.

**Weak (from their tracker)**
- #727: asks for "checklist page(s) for experienced modders to refer back to".
- #710 (from watching a reviewer's video):
  - explain *why*, not only *what*;
  - standardise prerequisites, perhaps as checklists;
  - say explicitly that imports are omitted;
  - warn readers off old tutorials;
  - "WHY ARE THEY CHANGING IT AGAIN".
- #629: VitePress layout scales poorly when zoomed out; the sidebar becomes huge.
- #739: client-side JavaScript breaks Internet Archive captures (a 404 animation covers the
  content).
- #720: "Basic Rendering Concepts is really outdated".
- #759, #706: bad API or bad examples in tutorials.
- The older community wiki (wiki.fabricmc.net) now disclaims itself and points to the docs.

### NeoForge: docs.neoforged.net

**Stack and information architecture**
- Docusaurus, MIT-licensed.
- Navbar: Docs, Toolchain Features, **Primers**, User Guide, Modpack Development.

**Versions**
- A dropdown of MC versions and ranges.
- Issue #207: "the version is listed as 1.21.x … the constructor used in the documentation was
  actually added in 1.21.2, and the 1.21 MDK is using 1.21.1." The maintainer explains that `.x`
  means "latest, beta included". The reporter asks for explicit versions instead of `.x`, and
  notes that the linked MDK doesn't match the samples.

**Code**
- Java.
- Open issue #379 wants "tabs for JSON vs. datagen".
- Several issues are snippets that don't compile or name things that don't exist: #329 "uses
  `BuiltInRegistries.BLOCKS`, which doesn't exist", #310, #311, #313.

**API reference**
- None. The docs teach readers to browse decompiled sources in the IDE.
- A third-party Javadoc host exists and is labelled "may be outdated".

**Good:** Primers as first-class porting guides, a Mod Generator, and clear tip/caution callouts.

### Sponge: docs.spongepowered.org

- Sphinx with reStructuredText.
- 20 locales, the broadest translation coverage in the survey.
- Version menu: "stable" (8.0.0), 7.4.0, 6.0.0, 5.1.0.
- The SpongeAPI repo has branches through `api-20`, and recent doc commits still say "fix
  spawning example for API 8". The visible label lags the platform. This is a cautionary example of
  versioned docs drifting from the API.
- Javadoc lives at jd.spongepowered.org.

### Kyori Adventure

- Now part of PaperMC docs (see above).
- The external **MiniMessage Viewer**, https://webui.advntr.dev (MIT,
  github.com/KyoriPowered/adventure-webui), offers preview modes Chat (Closed), Chat (Open), Lore,
  Hologram and Server List, plus a formatting toolbar, share links and an in-game-like background
  (`screens/minimessage-viewer.png`). The docs link out to it rather than embedding it.
- The webui repo ships `font/1_Minecraft-Regular.woff` and companions of unstated provenance; see
  §8 before copying the approach.

### LuckPerms: luckperms.net/wiki

- A custom Vue SPA (LuckPermsWeb) that renders the markdown from the separate `LuckPerms/wiki`
  repo. It needs JavaScript to render anything.
- **Information architecture:** a flat, long sidebar list (Install, Getting Started, Storage,
  Configuration, FAQ, Command Usage, Features, How To …). Search is in the header
  (`screens/luckperms-wiki.png`).
- **Developer API page:**
  - states "The API uses Semantic Versioning" and the current release (`5.5`);
  - keeps a major-version changelog;
  - gives Maven, Gradle Groovy and Gradle Kotlin DSL snippets;
  - covers obtaining the API per platform (Bukkit ServicesManager, Sponge, singleton);
  - has explicit **Thread safety**, **Immutability**, **Blocking operations** and
    **CompletableFutures** sections.
  https://github.com/LuckPerms/wiki/blob/master/pages/Developer-API.md
- **Distinctive:** the site is also the product UI (web editor, verbose viewer, tree viewer under
  "Tools"). This is the model for a Sculk diagnostic-dump viewer.

### GeyserMC: geysermc.org/wiki

- Docusaurus.
- The Setup page is the best platform-selection UX in the survey. "Host Provider" / "Self Host"
  toggle, then platform cards (Paper/Spigot, Fabric, NeoForge, Velocity, BungeeCord, ViaProxy,
  Standalone). Each reveals the same numbered structure (Install → Configure → Connect → HTTPS)
  with platform-specific cautions such as "Install Geyser only on the Velocity server!".
  https://geysermc.org/wiki/geyser/setup/ (`screens/geyser-setup.png`)
- A "Dump Viewer" utility and 12 locales.
- The footer disclaimer is a good template: "This website is not an official Minecraft website and
  is not associated with Mojang Studios or Microsoft …"
- **Tracker:**
  - #10: "Try adding live sample examples instead of general placeholders … most of us will get
    confused, like where to put such & such files".
  - #180: "Currently supported versions page is not up to date".

### Modrinth: modrinth.com and docs.modrinth.com

- The docs are stock Starlight with starlight-openapi. Each API operation is a page with GET/path,
  typed parameters and responses (`screens/modrinth-docs.png`). They are functional and visually
  indistinguishable from PaperMC docs except for the green accent.
- The marketing site carries its identity: a large green mark and a low-contrast isometric maze
  pattern behind the hero, with content cards below (`screens/modrinth-home.png`). The theme stays
  on the landing page; reading pages stay plain.

### Hangar: hangar.papermc.io

- A Nuxt app with an API docs page at https://hangar.papermc.io/api-docs.
- The publishing guide lives in PaperMC docs at https://docs.papermc.io/misc/hangar-publishing/.
- Only relevant as a distribution target. The Sculk docs could link a "Publish to Hangar/Modrinth"
  guide.

### CommandAPI: docs.commandapi.dev

- VitePress 1.6.4.
- **Synced Java / Kotlin tabs** on every example.
- A **Preferences: Paper ↔ Spigot toggle** in the right rail filters platform-specific content
  site-wide (`screens/commandapi.png`).
- A version dropdown (12.1.0), page authors, "Developer's Note" callouts, and inline Folia
  warnings ("Registering commands after the server is done loading will _not_ work on Folia").
- **Tracker evidence of version pain:** #701 "Failed to load on Minecraft 26.3", #660 "Add 1.21.8
  to your hardcoded version check", #648, #649, #653, #654, #659 (each MC update), #686
  (`UnsupportedOperationException` on Folia), #704 (Paper shade depends on unavailable NMS
  modules). Developers repeatedly need "which versions/platforms does this run on, today?"

### Cloud (Incendo): cloud.incendo.org

- MkDocs Material 9.7.1.
- **Structure:** Core → Annotations → Kotlin → Minecraft integrations (Bukkit/Paper, Bungee,
  Velocity, Fabric, Sponge, NeoForge) → Discord → Postprocessors.
- Maven / Gradle (Kotlin) / Gradle (Groovy) tabs.
- Technical detail is deferred to javadocs.dev.
- **Open issues:** #42 "Brigadier examples and notes", #48 "Missing annotation documentation",
  #39 "Document tooltip suggestions", #43 "admonition telling people not to register their commands
  in plugin.yml". These are reference gaps that generated docs would close.

### PacketEvents: docs.packetevents.com

- Starlight with the Ion theme.
- Introduction → Setup when Bundling → Processing and Sending.
- The landing page carries a team table and testimonials, but the docs are thin: no Javadoc link,
  sparse samples, and no per-platform setup despite listing Paper, Spigot, Velocity, BungeeCord,
  Fabric and Sponge.

### Triumph GUI and InvUI (menu libraries)

- **InvUI** (https://docs.xenondevs.xyz/invui/):
  - Zensical; Gradle Kotlin/Groovy/Maven tabs; Javadoc *and* KDoc.
  - States "Minimum required Java version: 25", "InvUI only targets the latest Minecraft version",
    "**not** thread-safe", Folia "experimental".
  - Honest, but **no rendered menu images anywhere** on the overview.
- **Triumph GUI** (https://triumphteam.dev/docs/triumph-gui/introduction):
  - 3.x / 4.x selector; GUI, Paginated, Scrolling and Storage types.
  - **No screenshots of any GUI.**
- For libraries whose whole output is visual, this is the clearest missed opportunity in the
  survey.

### Skript: docs.skriptlang.org

- A custom static generator: "the only documentation is generated automatically" from annotations.
- Every syntax element is a card with **Patterns, Since, Required/Optional entries, Examples**
  (collapsible) and a category tag ("Structure") (`screens/skript-docs.png`).
- Visually dated (orange/cyan on black, Poppins). The model is right, though: reference is
  generated from code metadata, with "Since" on every element.

### Spigot wiki: spigotmc.org/wiki

- A XenForo wiki behind Cloudflare. The fetcher got 403; the screenshot loaded.
- Ad sponsor blocks sit beside the content.
- **Stale index titles:** "Creating a blank Spigot plugin in Eclipse / NetBeans", "Guide - How to
  auto-export your plugin using ant", "Particle list 1.8.8", "MongoDB With Morphia". A History /
  Editors / Discussion (36) tab model (`screens/spigot-wiki.png`).
- A third-party post observed the wiki's plugin-creation page ranks below YouTube and forum posts.
  https://www.justdave.net/dave/category/minecraft/
- A gist notes that guides are "outdated and do not clearly direct you on how to setup your
  environment properly".
  https://gist.github.com/AnchyDev/246ca6854b1b5398fbe74e24a84e742e
- This is the archetype of what Sculk's docs must not become.

### Extras worth knowing

- **Typewriter** (https://docs.typewritermc.com):
  - Docusaurus restyled entirely in monospace.
  - Each page carries a **Version: 0.8.0** badge, a **Difficulty: Easy** badge and a
    **"Before starting"** prerequisites callout (`screens/typewriter.png`).
  - The identity is strong, but long prose in monospace is tiring. Prefer to take the badges, not
    the font.
- **Misode's generators** (https://misode.github.io): version-tagged (26.3 chip), form on the left
  and live JSON on the right, presets, share link.
- **Project generators:** the Fabric template generator (https://fabricmc.net/develop/template/,
  with a "Kotlin Programming Language" checkbox; `screens/fabric-template.png`), the NeoForge Mod
  Generator (https://neoforged.net/mod-generator/) and the IntelliJ Minecraft Development wizard.
  Generators are now table stakes.
- **CanvasMC docs** (https://docs.canvasmc.io): Starlight. Canvas states that Folia has "incomplete
  API support for plugins to work with region threading" and adds its own API, a third platform
  shape Sculk must describe.
- **hytalemodding.dev:** Next.js and Fumadocs in the default black shadcn look, with an `llms.txt`.
  https://hytalemodding.dev/llms.txt
- **GitBook** (Lamp, Nexo, HelpChat) is common for plugin *user* docs. It is fast to write but
  looks the same everywhere and gives little control over components.

---

## 4. What developers complain about (consolidated)

| # | Complaint | Evidence |
| --- | --- | --- |
| 1 | **Stale content with no freshness signal**: tutorials for dead versions, IDEs, build tools | Spigot wiki index; Fabric #720; Geyser #180; Sponge "8.0.0" label; Fabric #710 "advise modders to be careful with old posts" |
| 2 | **Version labels hide ranges, or don't match the linked starter** | NeoForge #207 (`.x` meant 1.21.2+, MDK was 1.21.1) |
| 3 | **Samples that don't compile or use APIs that don't exist** | NeoForge #329, #310, #311, #313; Fabric #759, #706, #695 |
| 4 | **Platform/threading rules are implicit** ("what runs where?") | Folia #285; Paper's Folia callout says "this guide is not for you"; InvUI "not thread-safe"; CommandAPI #686 |
| 5 | **Every MC update breaks something, and the docs don't say which versions work** | CommandAPI #701, #660, #648, #649, #653, #654, #659 |
| 6 | **Java-only, Maven-first or Groovy-first samples** | Paper/Velocity Java only; forum ask for Kotlin; PaperMC #697 "templates aren't kotlin scripts" |
| 7 | **Guide and API reference live on different sites; no automatic links** | PaperMC #665; NeoForge has no reference; Cloud defers detail to Javadoc |
| 8 | **No linear getting-started path / no "why"** | PaperMC #666; Fabric #710 |
| 9 | **Placeholders instead of concrete, visual examples** | Geyser #10; InvUI and Triumph have no GUI images |
| 10 | **Reference gaps that hand-written docs never close** | Cloud #39, #42, #48; PaperMC #668, #669, #747 |
| 11 | **Setup pages that interleave two paths** | PaperMC #697 |
| 12 | **Layout/zoom and JS-only rendering problems** | Fabric #629 (zoom), #739 (archive breakage); LuckPerms renders nothing without JS |
| 13 | **No checklists for experienced users** | Fabric #727 |
| 14 | **Missing page descriptions / SEO** | NeoForge #296; Spigot wiki page "mysteriously absent from search results" |
| 15 | **Ads and sponsor noise beside content** | Spigot wiki |

---

## 5. Features that would make Sculk's docs best-in-class (ranked by value)

The rank weighs pain in the evidence above, how unique the feature is in the niche, and how
cheaply Sculk's existing metadata can feed it (`API_POLICY.md`, `COMPATIBILITY.md`,
`THREADING.md`, the jar-scan JSON, `examples/`, `integration-tests/`).

### 1. A generated compatibility strip on every API symbol and guide

- **What it shows:**
  - platforms (Paper · Folia · Canvas · Velocity), from `@Platforms`;
  - the MC floor, from `@SinceMinecraft`;
  - the capability state per anchor version (Native / Emulated / Degraded / Unavailable), from
    the compatibility generator;
  - the stability marker (Stable / Experimental `@OptIn`).
- Guides aggregate the strips of the symbols they use.
- **Why first:** nobody in the niche does this per API (complaints 2, 4, 5). It turns "works on
  Folia?" from a forum question into a glance.
- **Detail that matters:** say *what* is degraded, not just that it is. The
  capability table already has the wording ("Degraded through `Player.getClientBrandName`").

### 2. Execution-context callouts tied to `@Requires`

- Every world-touching API shows "Requires: Entity owner", with what that means on Paper vs
  Folia/Canvas (one row of the `THREADING.md` table).
- Snippets that hop contexts are annotated inline.
- **Evidence:** Folia #285's exact questions (which thread runs commands, listeners, tasks; a
  fallback pattern; a list of unsupported APIs) can be answered in one dedicated page plus these
  callouts.

### 3. Compiled, tested snippets with a link back to the full file

- Pull snippet regions from `examples/*` and `integration-tests/*` (Fabric's reference-mod model).
- Show "Files referenced" and a "verified on Paper 1.21.4 / 26.3, Folia …" stamp from the
  integration matrix.
- **Removes:** complaint 3 entirely and complaint 1 partly.

### 4. Version-aware, copyable build setup, Gradle KTS first

- One snippet generator that knows:
  - the repository (Sculk repo + Paper repo);
  - the Sculk BOM / version catalog;
  - the `paper-api` coordinate format for the chosen MC version (`-R0.1-SNAPSHOT` before 26.1,
    `26.x.build.+` after);
  - the JDK (21 vs 25).
- Maven sits in a secondary tab or is omitted. Groovy is optional.
- Selecting a platform or version updates every snippet on the site, using CommandAPI-style
  persisted preferences.

### 5. A project generator

- Choose platforms (Paper, Folia-ready, Canvas, Velocity), modules (presentation, menus, dialogs,
  commands, config…), MC floor, and testkit on/off. Get a zip or a `git clone` command.
- Fabric, NeoForge and IntelliJ already set this expectation. PaperMC #697 shows how much a bad or
  absent template hurts.

### 6. Unified search across guides, API reference and keys

- Index guides, API symbols (from Dokka/ABI), config keys, MiniMessage tags and placeholders,
  permissions and capability names in one search, with symbol-aware ranking (typing `MenuType`
  should land on the symbol).
- Back-tick mentions in guides auto-link to the reference (PaperMC #665).

### 7. Rendered Minecraft output beside the code

- **Surfaces:** menus (chest grids, anvil prompts, merchant), dialogs, sidebar, boss bar, action
  bar, titles, nametags/below-name, item tooltips.
- **Two tiers:**
  - Real screenshots captured automatically from the integration-test client per anchor version.
    Screenshots of gameplay are explicitly allowed; see §8.
  - A CSS/SVG renderer for live previews, drawn in a Minecraft-*proportioned* but original style.
- **Evidence:** InvUI and Triumph have no GUI images, Geyser #10 asks for concrete examples,
  and the MiniMessage Viewer's preview modes are the most-linked external tool.

### 8. An embedded MiniMessage playground with Sculk placeholders

- Inline on every presentation page, seeded with the page's example, with chat, lore, sidebar,
  boss bar, title and dialog render targets.
- It should *teach the security rule*: show a value entering as an unparsed placeholder, and show
  why `replace()` into a template is unsafe.
- **Feasibility note:** MiniMessage is JVM code; the webui runs it server-side. For Workers, options
  are build-time rendering for static examples plus either a small JVM endpoint, a Kotlin/Wasm or
  TeaVM build of the parser, or a JS port. I have not verified any of these; this needs a spike.

### 9. Migration guides, generated where possible

- "Raw Paper/Bukkit API → Sculk" for each feature area.
- Sculk 5 → 6.
- Per-MC-update "primers" (NeoForge's model) listing which capabilities changed state, generated
  from the jar-scan diff and the `.api` dump diff.

### 10. Typed config reference and preview

- From config classes (`@Comment`, `@Range`, `@NotBlank`), generate a key reference (type, default,
  range, comment) and the rendered default YAML.
- Add an interactive "edit and validate" view that reports exactly what the plugin would report.

### 11. A diagnostic-dump viewer

- Paste a Sculk runtime dump and see platform, version, capability states, execution-context
  ownership and warnings, rendered with the same badges as the docs.
- Equivalents: Geyser's Dump Viewer and LuckPerms' Tools.
- It doubles as a support tool for Daisy's paid plugins.

### 12. Testkit-first examples

- Every interface page shows its `sculk-testkit` fake and a test using it. No surveyed site does
  this, and it suits Sculk's "fakes refuse what they cannot model" rule.

### 13. Checklists, prerequisites and difficulty

- "Before you start" boxes (Typewriter) and a recipe checklist per task (Fabric #727), each item
  linking into the guide. Add a "Why" sentence at the top of each tutorial (Fabric #710).

### 14. Explicit versioning, honestly labelled

- Docs built per release tag with **concrete ranges** ("Sculk 6.2 · Minecraft 1.21.4 – 26.3"),
  never ".x" (NeoForge #207).
- Show a "last verified against Paper 26.3 build N" stamp, and a banner on old versions.

### 15. Synced language tabs only where they differ

- Kotlin is primary. Show a Java tab only where interop differs (explicit-API Java callers,
  `@JvmStatic` and builders), synced site-wide like CommandAPI.

### 16. Machine-readable outputs

- `llms.txt`, a raw Markdown URL per page, and compat and capability JSON published alongside.
- AI assistants now answer plugin questions. PaperMC docs are indexed by Algolia's DocSearch MCP,
  and Hytale docs ship `llms.txt`. Cheap to do, and it keeps answers current.

### 17. Robust delivery

- Content rendered as static HTML that works without JS and in web archives (Fabric #739).
- A page description on every page (NeoForge #296).
- Holds up at 50–200% zoom (Fabric #629).
- No ads.

---

## 6. Complaints to avoid (checklist for reviewers)

- [ ] Every page shows which Sculk and Minecraft versions it was verified on. Nothing reads ".x".
- [ ] No snippet exists outside a compiled source set.
- [ ] No sample hides its imports without saying so, or links to the full file.
- [ ] No world-touching API is documented without its execution context.
- [ ] No "works on Folia" claim relies on prose alone; it comes from `@Platforms` and capability
  data.
- [ ] No setup page interleaves two paths. Pick one, then branch with tabs or cards (Geyser).
- [ ] No Groovy-first or Maven-first build snippet. Repository, BOM and JDK are always included.
- [ ] No API mention in a guide is left unlinked.
- [ ] No visual feature (menu, dialog, HUD) appears without a rendered image of its output.
- [ ] No placeholder file paths ("put it somewhere"). Show the real tree.
- [ ] No content that only renders with JS, no zoom-hostile fixed sidebars, no missing
  descriptions.
- [ ] No sponsor or ad noise, and no cookie wall for a docs site that needs no cookies.
  Typewriter and Skript both show consent banners.
- [ ] No stale tutorial left in navigation. Old versions are archived behind a version switch with
  a banner.
- [ ] Checklists exist for repeat tasks, and every tutorial starts with why.

---

## 7. Visual identity research

### 7.1 Why the current site reads as generic

In this exact niche a reader has seen this before:
- Starlight's default chrome: header search pill, three-column layout, "On this page" rail, theme
  dropdown.
- The default heading scale.
- One accent colour.

PaperMC, Modrinth, PacketEvents, CanvasMC and community Hytale docs all ship it. Recolouring
changes only the accent. Identity has to come from:
- page anatomy (compat strips, rendered MC output, generated reference cards);
- typography;
- a distinctive surface treatment;
- motion with meaning.

### 7.2 How game-adjacent developer sites carry a theme (observed)

| Site | What carries the theme | Readability outcome |
| --- | --- | --- |
| **Bevy** (`screens/bevy.png`) | Custom site; bird mark inlined *into a heading* ("What's a [logo]?"); warm brown "Stability Warning" panel; playful copy ("A bevy is a group of birds!") | Personality via voice and one or two branded components; prose stays plain. Good. |
| **Godot** (`screens/godot.png`) | Ribbon-badge logo and coral uppercase section labels on a Read-the-Docs theme | Readable but dated; theming is only the logo and an accent. |
| **Roblox Creator Hub** (`screens/roblox.png`) | Cinematic in-engine art only in the landing hero and video cards; docs pages neutral | Theme lives in the marketing layer; reading pages stay quiet. |
| **Modrinth** (`screens/modrinth-home.png`) | Low-contrast isometric maze pattern and a big brand mark on the landing page only | Tasteful "blocky" evocation without textures; the docs subdomain loses it entirely. |
| **Typewriter** (`screens/typewriter.png`) | Monospace for everything | Unmistakable, but tiring for long reading. A warning against over-theming body text. |
| **MiniMessage Viewer** (`screens/minimessage-viewer.png`) | Pixel display font for the title and a real in-game background | Useful because the preview *is* the content; fails as a general docs style. |
| **Hytale modding** (`screens/hytalemodding.png`) | Nothing; stock Fumadocs black | Clean, anonymous. |
| **Unity** (not screenshotted) | Corporate-neutral docs with the brand reserved for marketing | Same lesson as Roblox. |

**Pattern:** the best sites keep body text, tables and code neutral and high-contrast. They put
the theme in four places:
1. the frame (header, sidebar edges, code-block chrome);
2. a small number of signature components;
3. the landing page;
4. motion that means something.

### 7.3 The real sculk palette (sampled from vanilla 26.1 textures)

These were sampled by dominant pixel colour from `sculk.png`, `sculk_sensor_tendril_*`,
`sculk_shrieker_*`, `sculk_catalyst_*`, `reinforced_deepslate_*`, `deepslate_tiles` and
`calibrated_sculk_sensor_amethyst` in the local client jar. **Colour values are not protectable;
the textures are.** Use the numbers, never the images.

| Role (suggested) | Hex | Source | Contrast on `#0D1217` |
| --- | --- | --- | --- |
| Void / page background | `#0D1217` | sculk base (darkest) | — |
| Raised surface | `#111B21` | sculk base | — |
| Deep teal surface / code bg | `#052A32` | sculk mid | — |
| Teal border / rule | `#034150` | sculk, shrieker | — |
| Glow (muted) | `#009295` | sensor tendril active | 4.97 (AA body, borderline) |
| Glow (bright): links, focus, "live" | `#29DFEB` | sensor tendril highlight | 11.51 |
| Inactive tendril (disabled states) | `#1D7589` / `#164A57` | tendril inactive | — |
| **Bone** (stable/positive, headings accent) | `#BBC39B` / `#D1D6B6` | shrieker and catalyst bone | 10.21 / 12.55 |
| Sage (secondary text) | `#819988` / `#A2AF86` | catalyst, reinforced deepslate | 6.13 |
| Deepslate greys (frames, tables) | `#2D2D2D` `#383737` `#414141` `#4B4C4F` | deepslate tiles | — |
| Amethyst (experimental / opt-in) | `#B38EF3` / `#8D6ACC` | calibrated sensor | 7.24 |

**Notes**
- The brief's `#00F0FF` is brighter and more electric than the game (13.36:1). `#29DFEB` is the
  in-game highlight and reads as "sculk" rather than "cyberpunk".
- Keep the brightest glow for under about 5% of pixels (interactive and "alive" things only).
- **Bone is the secret weapon.** Real sculk blocks pair the teal with pale, bony sage. Using it for
  headings and "Stable" badges makes the palette feel like *Minecraft's* deep dark, not a generic
  neon dark mode.
- **Light theme:** a bone-paper background around `#E9EBDD` with deep-teal ink. `#034150` on it
  gives 9.27:1 and `#006B6E` gives 5.23:1. Light mode then becomes the "ancient city in daylight",
  not an inverted afterthought.
- **Semantic mapping that avoids gimmicks:**

  | Signal | Colour |
  | --- | --- |
  | Native | glow teal |
  | Emulated | sage |
  | Degraded | amber (your own, not from the game) |
  | Unavailable | deepslate grey with strike-through |
  | Experimental | amethyst |
  | Stable | bone |

### 7.4 Motifs, translated into tasteful UI

| Game element | UI translation | Where |
| --- | --- | --- |
| **Sculk sensor pulse** (vibration rings) | One expanding concentric ring, about 400 ms, from the interaction point | Copy-to-clipboard confirm; anchor-jump highlight; search result focus; "playground compiled" indicator. Disabled under `prefers-reduced-motion`. |
| **Sculk veins** | 1 px branching hairlines in `#034150` that grow along connectors | Architecture and threading diagrams (Global → Region → Entity), the sidebar's active-branch line, the "Files referenced" tree |
| **Shrieker** (warning that summons the Warden) | Reserved for *danger* callouts: a dark panel with a bone top edge and a single static ring | "Never `replace()` a value into a template", main-thread blocking, Folia-unsafe calls. Use the motif, not the word "Shrieker". |
| **Reinforced deepslate** (unbreakable frame) | A heavier frame for things that are guaranteed: 2 px deepslate border with pixel-stepped corners via `clip-path` | Stable API cards, ABI-guaranteed signatures |
| **Ancient city** (symmetry, deep-slate architecture, soul-fire teal) | Strict grid, generous negative space, symmetric landing hero, near-black gradient falloff ("darkness" vignette) | Landing page and 404 only |
| **Darkness effect** (Warden pulse) | Slow vignette pulse | 404 / "Unavailable on this server" empty states only |
| **Catalyst bloom** | Brief bone-to-teal bloom when a checklist item completes | Checklists, generator steps |

**Guardrails**
- No textured backgrounds behind text.
- No animated particles.
- No pixel font for body copy.
- The glow is a 1–2 px edge or a ring, never a big blurred halo.
- Every motion explains state (copied, jumped, compiled, failed).

### 7.5 Typography (licence-checked options)

- **Body/UI:** a humanist or neo-grotesk sans with a large x-height. IBM Plex Sans (OFL) and Geist
  (OFL) are options; Inter is used so widely that it reads as default. Atkinson Hyperlegible (OFL)
  is an option if accessibility is the lead value.
- **Code:** JetBrains Mono (OFL), the natural choice for a Kotlin audience, or Monaspace (OFL).
- **Pixel accent, used sparingly:** **Monocraft** (OFL-1.1, https://github.com/IdreesInc/Monocraft,
  about 11.5k stars). It is a monospaced font *inspired by* the Minecraft typeface. Use it for
  badges, keyboard hints, slot counts in rendered menus and the 404 page, never for paragraphs.
  - GNU Unifont, the game's own fallback font, is OFL-1.1 and GPL-with-font-exception. It is usable
    for CJK and unusual glyphs in rendered UI previews.
- **Avoid:**
  - **Minecraftia** needs a paid commercial licence (https://andrewtyler.gumroad.com/l/minecraftia).
  - The fonts bundled in adventure-webui (`1_Minecraft-Regular.woff` and others) have unstated
    provenance.
  - The game's `ascii.png` glyph bitmap is a Mojang asset.
  - Any "MINECRAFT logo-style" lettering for the wordmark.

### 7.6 Rendering Minecraft UI faithfully but safely

- **Real screenshots:** capture them with the existing client integration harness. The usage
  guidelines explicitly allow "videos, streams, and screenshots of you playing or using
  Minecraft". This is the most faithful option and legally the cleanest.
  - Composite them onto a neutral frame; don't add Mojang artwork.
  - Lead with "your" content (Sculk menus and dialogs), not vanilla scenery.
- **Live previews:** draw the *geometry and behaviour* in CSS/SVG:
  - an 18 px slot grid scaled ×2/×3;
  - 9-column rows;
  - title bar spacing;
  - the purple-bordered tooltip (colour values only);
  - boss bar segments;
  - the right-side sidebar layout.

  Use **original** sprites (own 16×16 item icons, or Sculk brand glyphs) and Monocraft/Unifont
  text. "Faithful" means the proportions, layout and text formatting match, so developers can judge
  line lengths, wrapping and colours. It does not mean copying `generic_54.png` or vanilla item
  textures.
- **Community practice:** Misode (assets pulled from the `misode/mcmeta` mirror) and the
  MiniMessage Viewer do use vanilla assets. That is tolerated in hobby tools but not licensed. For
  a brand that sells products, don't rely on that tolerance.

---

## 8. Licensing notes (Minecraft Usage Guidelines)

Source: https://www.minecraft.net/en-us/usage-guidelines (fetched 2026-11-17). Key text:

- **Scope:** "Our brand, we mean any names, related logos, fonts, textures, and any other
  distinctive characteristics …". "Our assets, we mean the code, software, graphics, textures,
  images, models, sounds … and any videos or screenshots taken from our games".
- **Commercial use is broad:** "commercial use means any uses of our name, brand, or assets that
  you use and share with others (regardless of whether you receive payment or provide it for
  free)." A free docs site for a framework that supports paid plugins is still in scope.
- **Don't imply officialness:** "do not … include anything that makes people think that what you
  are sharing could be … official or approved by, endorsed by, associated with …"
- **Required disclaimer:** "Prominently include the disclaimer similar to the following: 'NOT AN
  OFFICIAL MINECRAFT [PRODUCT/SERVICE/EVENT/etc.]. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR
  MICROSOFT' on your product, listing, description, website/webpage, and all other related
  materials." Geyser's footer is a good working example.
- **Naming:** "Minecraft" may appear only in a secondary title or description, used "to describe
  your creations or their purpose honestly". "Don't use any other aspect of any of our brand or
  assets as part of any related branding, including as a logo or part of a logo." So "Sculk Studio
  — a Kotlin framework for Minecraft server plugins" is fine. A logo built from a sculk texture or
  the game font is not.
- **Websites and domains:** allowed if the site "doesn't appear official" and relates to Minecraft
  "in a fair, reasonable, and appropriate way".
- **Screenshots:** "You are allowed to create, use, and share videos, streams, and screenshots of
  you playing or using Minecraft".
- **Mods and plugins:** "an original plug-in that extends the functionality of Minecraft but that
  doesn't contain a substantial part of our copyrightable code or content". Distribute the mod, not
  a modded game.
- **Publications precedent (by analogy):** "Don't use the Minecraft logo or the word 'Minecraft' in
  letters that look like … our logo"; "Don't use Minecraft artwork or images taken from official
  product packaging, merchandise, or marketing materials". Screenshots of your original creation
  are fine.

**Practical rules for the Sculk site**

| Use | Status |
| --- | --- |
| Colour values sampled from textures | **Safe.** Colours aren't protected. |
| Original pixel art, CSS-drawn UI geometry, OFL fonts (Monocraft, Unifont, Plex, JetBrains Mono) | **Safe** |
| Real in-game screenshots of Sculk features | **Safe**, with the disclaimer |
| Vanilla texture PNGs or GUI sprites as site art, backgrounds or icons | **Avoid.** They are "assets". |
| The game's font bitmap, Minecraftia without a licence, unknown "Minecraft.woff" files | **Avoid** |
| Minecraft logo, logo-style lettering, official marketing art (including official Deep Dark key art) | **Avoid** |
| Mojang's Warden, Steve or creeper likenesses | **Avoid** |

**Flag for Daisy (not a blocker, just honest):** "Sculk" is a term from the game. The guidelines
count "any names … and any other distinctive characteristics of any part of our name and games" as
brand. The established name is unlikely to be a practical problem, especially with the disclaimer
and an original logo. Keep the *logo* fully original, avoiding sculk-block textures and shrieker
silhouettes copied from game art, so the brand doesn't lean on Mojang's assets. If the brand ever
goes beyond Minecraft tooling, get a trademark opinion.

None of this is legal advice.

---

## Appendix A: screenshots taken

All in `C:\Users\Daisy\AppData\Local\Temp\claude\docs-research\screens\`:
paper-dev, fabric-docs, modrinth-home, modrinth-docs, luckperms-wiki, commandapi, skript-docs,
minimessage-viewer, bevy, godot, roblox, spigot-wiki, geyser-setup, misode (still loading, little
value), fabric-template, typewriter, hytalemodding (`.png`).

## Appendix B: key URLs

- PaperMC: https://docs.papermc.io/paper/dev/project-setup/ ·
  https://docs.papermc.io/paper/dev/folia-support/ · https://docs.papermc.io/paper/dev/scheduler/ ·
  https://docs.papermc.io/adventure/minimessage/format/ ·
  https://docs.papermc.io/velocity/dev/api-basics/ · https://github.com/PaperMC/docs/issues
  (#665, #666, #697, #715) · PR #560 "rewrite with Starlight"
- Folia: https://docs.papermc.io/folia/reference/overview · https://github.com/PaperMC/Folia/issues/285
  · https://github.com/PaperMC/Folia/wiki/Plugin-Compatibility
- Fabric: https://docs.fabricmc.net/develop/ · https://github.com/FabricMC/fabric-docs/issues
  (#629, #710, #720, #727, #739, #759) · https://fabricmc.net/develop/template/
- NeoForge: https://docs.neoforged.net/docs/gettingstarted/ ·
  https://github.com/neoforged/Documentation/issues (#207, #296, #329, #379) ·
  https://neoforged.net/mod-generator/
- Sponge: https://docs.spongepowered.org/stable/en/
- Adventure webui: https://webui.advntr.dev · https://github.com/KyoriPowered/adventure-webui
- LuckPerms: https://luckperms.net/wiki/Developer-API ·
  https://github.com/LuckPerms/wiki/blob/master/pages/Developer-API.md
- Geyser: https://geysermc.org/wiki/geyser/ · https://geysermc.org/wiki/geyser/setup/ ·
  https://github.com/GeyserMC/GeyserWebsite/issues/10
- Modrinth: https://docs.modrinth.com/ · https://docs.modrinth.com/api/ ·
  https://api.modrinth.com/v2/tag/loader
- Hangar: https://hangar.papermc.io/api-docs · https://docs.papermc.io/misc/hangar-publishing/
- CommandAPI: https://docs.commandapi.dev/create-commands/registration ·
  https://github.com/JorelAli/CommandAPI/issues
- Cloud: https://cloud.incendo.org/ · https://github.com/Incendo/cloud-docs/issues
- PacketEvents: https://docs.packetevents.com/
- InvUI: https://docs.xenondevs.xyz/invui/ · Triumph GUI:
  https://triumphteam.dev/docs/triumph-gui/introduction
- Skript: https://docs.skriptlang.org/
- Spigot wiki: https://www.spigotmc.org/wiki/spigot-plugin-development/
- Extras: https://docs.typewritermc.com · https://misode.github.io · https://docs.canvasmc.io ·
  https://hytalemodding.dev/llms.txt · https://bevy.org/learn/ · https://docs.godotengine.org ·
  https://create.roblox.com/docs
- Licensing: https://www.minecraft.net/en-us/usage-guidelines ·
  https://github.com/IdreesInc/Monocraft (OFL-1.1) · https://andrewtyler.gumroad.com/l/minecraftia
