# Workstream G — Sculk Docs 6 technical spike

Date: 2026-10-12 · Question: **TanStack Start + React + MDX + Bun, or Starlight with generated navigation?**
Scope: requirements first, framework second. Time-box honoured (~2 h incl. research). Nothing deployed.

Prototype: `%LOCALAPPDATA%\Temp\claude\C--Users-Daisy-Desktop-sculk-studio\726ffd0c-32d8-4dde-b636-e26c5f856530\scratchpad\g-docs\sculk-docs-proto` (not in repo).
Research was done via npm registry, official docs and WebFetch; items not confirmed are marked **UNVERIFIED**.

---

## 1. Recommendation (TL;DR)

**Build Sculk Docs 6 on Starlight with generated navigation.** Keep the four Sculk-specific pipelines
(manifest, snippets, selectors, generator) framework-agnostic so the shell can be swapped.
**Fallback:** TanStack Start + Fumadocs (Fumadocs officially supports Start as of 16.x) — the prototype
proves the custom pipelines port without change.

Why: ~70 % of the requirement list is docs-shell work Starlight already ships (search, Expressive Code,
synced tabs, badges, TOC, mobile nav, i18n, a11y, sitemap, 404, Workers deploy already live). TanStack Start
rebuilt that shell from zero in the prototype and shipped **344 KiB** of client JS for a 3-page site vs
Starlight's **~95 KiB** UI core for 63 pages. Both frameworks are pre-1.0 (Starlight 0.42.6, Start RC
1.168.61); Starlight has the larger production footprint (Cloudflare's own developer docs run on it).

---

## 2. Current site (baseline)

| Item | Value |
| --- | --- |
| Stack | `@astrojs/starlight` 0.41.6 · `astro` 7.1.6 · `wrangler` 4.118 · Bun 1.4.3 |
| Content | 62 MDX pages, frontmatter = `title` + `description` only; one `Card/CardGrid` import; zero custom components |
| Sidebar | hand-maintained, 79 entries in `astro.config.mjs` |
| Deploy | Workers static assets (`[assets] directory=./dist`, `404-page`), no adapter |
| `bun install --frozen-lockfile` | 384 packages, 19 s |
| `bun run build` | **3.2 s wall** (Astro reports 1.67 s for 63 pages incl. Pagefind 116 ms) |
| Output | **4.75 MiB, 157 files**; Pagefind 0.82 MiB (UI JS 171 + 117 KiB), `_astro` 0.19 MiB, biggest page HTML 88 KiB |
| Latest upstream | Starlight 0.42.6 needs Astro ≥ 7.2.10 → upgrade required before adopting 0.42-only plugins |

---

## 3. Prototype — what was built and what happened

### Scaffold (official CLI, Bun)

```
bunx --bun @tanstack/cli@latest create sculk-docs-proto --framework React --deployment cloudflare \
  --package-manager bun --no-git --no-intent --no-toolchain --no-examples --non-interactive
```

`create-start-app` is **deprecated** (prints a warning, redirects to `@tanstack/cli`). Scaffold incl.
install: **24 s**. One scaffold attempt, no failures.

### Exact versions installed

| Package | Version | Package | Version |
| --- | --- | --- | --- |
| `@tanstack/react-start` | 1.168.61 | `@mdx-js/rollup` | 3.1.1 |
| `@tanstack/react-router` | 1.170.42 | `remark-frontmatter` | 5.0.0 |
| `@tanstack/router-cli` | ^1.132.0 | `remark-mdx-frontmatter` | 6.0.0 |
| `@cloudflare/vite-plugin` | 1.63.1 | `remark-gfm` | 4.0.1 |
| `vite` | 8.3.4 | `@types/mdx` | 2.0.14 |
| `wrangler` | 4.149.0 | `react` / `react-dom` | 19.3.0 |
| `@vitejs/plugin-react` | 6.1.2 | `typescript` | 6.0.3 |
| `tailwindcss` / `@tailwindcss/vite` | ^4.1.18 | `pagefind` (post-build) | 1.5.2 |

Note the version skew inside one official scaffold (start 1.168 / router 1.170 / cli 1.132): normal for
TanStack's lockstep-less releases, but it is what "RC" looks like in practice.

### Steps completed

| # | Step | Result |
| --- | --- | --- |
| 1 | Scaffold with Bun + Cloudflare adapter | OK, 24 s |
| 2 | MDX via `@mdx-js/rollup` (`enforce:'pre'`) + remark-frontmatter + remark-mdx-frontmatter + GFM; `*.mdx` exports `frontmatter` | OK first build. Frontmatter `module/stability/minecraft/platforms/threading/since` rendered as badges in a `/docs/$` splat route |
| 3 | Sidebar from `manifest.json` (5 entries) | OK. JSON import + `import.meta.glob` resolved at build time; entries without a page render greyed. Manifest also feeds `prerender.pages` in `vite.config.ts` |
| 4 | `PackageTabs` (Gradle KTS / Maven, localStorage-synced like Starlight `syncKey`) + `SinceMinecraft` badge + `Badge` (Native/Emulated/Degraded/Unavailable, stable/experimental/internal) | OK, SSR-deterministic |
| 4b | Snippets from source: `?raw` import of **the real repo file** `examples/basic-plugin/.../BasicPlugin.kt` via Vite alias (lines 37-49) and `// #region menu` extraction with marker stripping + dedent | OK, both render; `#region` markers absent from HTML |
| 5 | `bun run build` with `prerender.enabled` + `crawlLinks` | OK. 3 pages prerendered to `dist/client/**/index.html`; 3 manifest-only routes 404 (needed `failOnError:false`) |
| 5b | `wrangler deploy --dry-run` on generated `dist/server/wrangler.json` (Worker + assets) | OK: 12 modules / 209 KiB Worker, 15 assets, total 939 KiB (205 KiB gz) |
| 5c | `wrangler deploy --dry-run` with an **assets-only** config pointing at `dist/client` (no Worker) | OK: 15 files. Pure-static hosting of a prerendered Start site works |
| 5d | `bunx pagefind --site dist/client` | OK: 3 pages, 159 words, 30 ms; 0.62 MiB (UI JS dominates) |
| 6 | `tsc --noEmit` | clean |
| — | Browser hydration / tab click | **NOT RUN** — no Chrome on this machine for Playwright. Hydration behaviour UNVERIFIED |

### Build metrics (prototype)

| Build | Wall time | `dist` | Files | Client JS | Notes |
| --- | --- | --- | --- | --- | --- |
| Hello-world scaffold | 2.25 s | 1.23 MiB | 15 | 340 KiB (109 KiB gz) | `dist/server/index.js` 747 KiB |
| + MDX, 3 pages, prerender | 2.18 s cold / 1.89 s warm | 1.29 MiB | 25 | 344 KiB | page HTML 7.3 KiB; CSS 9.1 KiB |
| Starlight, 63 pages (ref.) | 3.2 s | 4.75 MiB | 157 | ~95 KiB UI + 290 KiB Pagefind UI | includes search index |

Per-page cost is not comparable at n=3; extrapolation UNVERIFIED. Both are "seconds", not a factor.

### Friction log (TanStack Start path)

1. `create-start-app` deprecated mid-flight → `@tanstack/cli`. Minor, but docs/blog posts still point at the old name.
2. Prerender crawler first requests `/docs/x/` → 307 → re-requests `/docs/x`; works, noisy. Default `html_handling` on Workers assets resolves it in prod.
3. `import.meta.glob({eager:true})` put **every MDX page and every raw Kotlin source into one 344 KiB client chunk**. Real build must use lazy glob in the route loader; and the Snippet must be resolved at **build time** (remark/Vite plugin), not as a runtime component receiving the whole file as a prop — otherwise each source file ships to the browser.
4. No syntax highlighting: needs Shiki (`@shikijs/rehype`) wired by hand. Starlight ships Expressive Code (highlighting, frames, markers, titles, copy button).
5. The Start build always emits a Worker (`dist/server`, 730 KiB `index.js`) even when fully prerendered. Assets-only deploy is possible (5c) but then no server functions, no `/api/search`, no SSR fallback for un-prerendered routes.
6. Everything else is DIY: TOC, prev/next, heading anchors, mobile nav, theme toggle, i18n, edit link, sitemap, 404, OG tags, skip links, last-updated. Fumadocs UI covers most of it but its default search is a **server route** (Worker required) — static-index mode UNVERIFIED for Start.
7. Tailwind `prose` classes are a no-op without `@tailwindcss/typography` — trivial, but typical of the "nothing is wired" experience.
8. RC status: TanStack Start is still **Release Candidate** per its own overview page; no GA date found. API is declared stable.
9. Positive: Windows absolute alias into the repo (`@examples` → `C:/.../examples`) worked; Bun + Vite 8 + Cloudflare plugin had zero compatibility issues; workerd-backed `vite preview` ran.

---

## 4. Requirement scorecard

Legend: **N** meets natively · **P** meets with plugin · **C** needs custom code · **X** cannot (practically).

| Requirement | TanStack Start + MDX | Starlight (generated nav) | Notes |
| --- | --- | --- | --- |
| Sculk version selector | C | P/C | `starlight-versions` 0.10.1 (early dev, Starlight ≥0.39) or multi-build under `/v5/` `/v6/` + custom header override. Start: custom either way |
| Minecraft version selector | C | C | Header override (`components.Header`) + nanostore; same component in both |
| Platform selector (Paper/Folia/Canvas/Velocity) | C | C | As above; content tagged `<Platform only=...>` toggled by `data-` attr, all variants in HTML for search |
| Gradle/Maven tabs | C (built in proto) | **N** `<Tabs syncKey>` | Starlight persists choice across pages |
| Kotlin DSL / annotation tabs | C | **N** same `<Tabs>` | |
| Module badges | C (built) | **N** `<Badge>` + sidebar `badge` | |
| Support badges Native/Emulated/Degraded/Unavailable | C (built) | C (thin wrapper over `<Badge variant>`) | 4-state semantic needs own component in both |
| "since Minecraft" / "since Sculk" | C (built) | C | Frontmatter-driven in both |
| Stability markers | C (built) | **N** sidebar/page `badge` from frontmatter | Starlight renders in sidebar for free |
| Threading requirement | C | C | Frontmatter field + page header strip |
| Folia/Canvas status | C | C | Derived from manifest `platforms` |
| Dependency size | C | C | Manifest field, filled by Gradle task |
| Source link / API link | C | P/N | Starlight `editLink` native; API link = frontmatter + `PageTitle` override |
| Full-text search | C/P (Pagefind post-build proven; UI DIY) | **N** Pagefind built in, `pagefind: false` + `Search` override for Algolia/Orama | DocSearch: official `@astrojs/starlight-docsearch`; eligibility for a commercial product UNVERIFIED |
| Nav from frontmatter + manifest JSON | C (built, trivial) | **N/C** `autogenerate` + frontmatter `sidebar.*`; manifest badges via `routeMiddleware` or config-time array from JSON | Starlight sidebar is a plain JS array — generate it in `astro.config.mjs` |
| Compiled snippets from `examples/*` | C (built: `?raw` + region) | C (same remark plugin; `<Code code={raw}>` already supported) | Framework-agnostic remark plugin, see §5.2 |
| Playgrounds (MiniMessage, command tree, GUI layout, compat checker) | **N** React everywhere | P `@astrojs/react` 7 islands (`client:visible`) | Islands cannot share React context → use nanostores; fine for 4 independent widgets |
| `/create` project generator (zip) | **N** route + client zip | P/C `src/pages/create.astro` + `StarlightPage` + React island | Client-side `fflate` in both, see §5.5 |
| Cloudflare Workers + Bun | **N** (`--deployment cloudflare`, dry-run OK) | **N** (already live, assets-only) | Start always emits a Worker; Starlight needs none |
| Versioned docs side by side | C | P (`starlight-versions`) or C (multi-build) | Multi-build is the robust path for both, §5.3 |
| Edit link, TOC, prev/next, i18n, a11y, OG, sitemap, 404 | C (all) or P (Fumadocs UI) | **N** | Largest hidden cost of Start |
| Styling / theming | C (Tailwind) | **N** CSS vars (existing `sculk.css` keeps working) | |
| Client JS budget | 344 KiB baseline (109 gz) | ~95 KiB + Pagefind lazy | React runtime is the floor on Start |

Tally (rough): Start **N 4 · P 0 · C 18**; Starlight **N 11 · P 5 · C 10**. The C-column items that remain
on Starlight are exactly the Sculk-specific ones that are custom anywhere.

---

## 5. Recommended architecture (framework-agnostic where possible)

### 5.1 Manifest (`sculk-manifest.json`, generated by Gradle)

Produced by a `:docs:manifest` Gradle task in `sculk-studio` from (a) the committed `.api` dumps (symbol
list, module) and (b) stability/support annotations read at build time (kotlin-compiler-embeddable or
KSP — build tooling only, never bundled, keeps the `kotlin("reflect")` release check green). Published
as a static asset per Sculk major.

```jsonc
{
  "schema": 1,
  "sculk": "6.0.0",
  "generated": "2026-10-12T00:00:00Z",
  "minecraft": { "supported": ["1.21.11", "1.21.8"], "latestTested": "1.21.11" },
  "modules": {
    "sculk-gui": { "artifact": "studio.sculk:sculk-gui", "sizeKiB": 212, "dependsOn": ["sculk-common","sculk-text","sculk-items"] }
  },
  "symbols": [
    {
      "symbol": "studio.sculk.gui.gui",
      "module": "sculk-gui",
      "stability": "stable",                       // stable | experimental | internal  (from @Sculk* annotations)
      "since": { "sculk": "5.0.0", "minecraft": "1.21.11" },
      "minecraft": { "native": "1.21.11", "fallback": "1.20.5" },  // fallback null = unavailable below native
      "platforms": { "paper": "native", "folia": "native", "canvas": "emulated", "velocity": "unavailable" },
      "threading": "entity",                       // any | global | region | entity | async
      "page": "gui/overview",                      // docs slug that owns this symbol
      "source": "sculk-gui/src/main/kotlin/studio/sculk/gui/Gui.kt#L42",
      "api": "https://api.sculk.studio/6/sculk-gui/studio.sculk.gui/gui.html"
    }
  ]
}
```

Consumers: sidebar generation (group by module, badge by stability), page header strip, compatibility
checker playground, `/create` module picker (uses `dependsOn` + `sizeKiB`), and a **build-time
consistency check** — a manifest entry whose `page` has no MDX fails the build (the prototype's
`failOnError:true` does exactly this; Starlight: a 10-line script in `astro.config.mjs`).

Page frontmatter stays minimal (`title`, `description`, `module`, optional overrides); everything else
is looked up from the manifest by slug so numbers live in one place. The prototype duplicated them in
frontmatter only to prove rendering.

### 5.2 Snippet pipeline

- Source of truth: `examples/*` (already compile gates). Add `// #region <name>` / `// #endregion` markers;
  they are plain comments, ktlint-neutral.
- Remark plugin `remark-sculk-snippet` (framework-agnostic, runs in Astro's `markdown.remarkPlugins`
  or `@mdx-js/rollup`): fence meta ` ```kotlin snippet=examples/basic-plugin/src/.../BasicPlugin.kt#menu `
  → reads file at build time, extracts region (or `#L37-L49`), strips markers, dedents, replaces the fence
  body. Missing file/region = build error. Output is an ordinary code node, so Expressive Code (Starlight)
  or Shiki (Start) highlights it for free and **no source text reaches the client bundle** (fixes
  friction #3).
- The prototype's `src/lib/snippet.ts` (region + lines + dedent, 25 lines) is the extraction core; move
  it from runtime to the remark plugin.
- Existing options don't fit: `remark-code-import` 1.2.0 (last publish 2023, no regions), Expressive Code
  has no include plugin, Fumadocs `<include>` supports `#region` but is Fumadocs-only.

### 5.3 Versioning layout

- `docs/` always builds **current major** at `/`. Archived majors are built from their git tag in CI with
  `base: '/v5'` and copied into the same `dist/` → one Workers assets upload. `dist/versions.json`
  drives the header switcher; same-slug navigation falls back to the version root when a page is missing.
- Pagefind: one index per version by default (Starlight builds it); cross-version search via
  `pagefind.mergeIndex` if wanted. Workers assets limits: 20 000 files (Free) / 100 000 (Paid),
  25 MiB per file — current site is 157 files, so ~50 versions fit on Free.
- `starlight-versions` 0.10.1 is the shortcut but self-describes as "early development" and copies
  content into the repo; prefer multi-build for a commercial site. Same layout works for TanStack Start.

### 5.4 Selectors and content filtering

- Three stores (nanostores, work in Astro islands and in React): `sculkVersion` (from URL base),
  `minecraftVersion` and `platform` (localStorage, header `<select>`s in a `Header` override).
- Content components `<Platform only="folia">`, `<Since minecraft="1.21.11">` render **all** variants
  into HTML with `data-platform` / `data-mc` attributes and hide via CSS — Pagefind and SEO see
  everything, no hydration needed for filtering (one tiny script toggles `<html data-platform>`).
- Per-page "compatibility strip": manifest entry × selected MC/platform → Native/Emulated/Degraded/
  Unavailable badge; same component powers the compatibility-checker playground.

### 5.5 Search

Pagefind (Starlight native; proven as post-build step on Start). Add `data-pagefind-filter="version:6"`
and `module:<name>` via the page template so the UI can filter. Algolia DocSearch is a vendor
dependency with branding and a case-by-case eligibility review for non-OSS; Orama OSS 3.1.18 is viable
but adds a client-side index download. Pagefind is the default; keep the `Search` override as the escape hatch.

### 5.6 `/create` generator — client-side zip, no Worker

- Astro page `src/pages/create.astro` wrapped in `StarlightPage` with one React island (or a Start route).
- Zip in the browser with **`fflate` 0.8.3 (MIT)**; `client-zip` 2.5.1 (MIT) is the smaller alternative;
  JSZip is MIT-or-GPL dual (fine, but say "MIT"). No licence problems for a premium product.
- Templates: `settings.gradle.kts`, `build.gradle.kts` (or `pom.xml`), `paper-plugin.yml`
  (`folia-supported` from the platform picker), `src/main/kotlin/<pkg>/<Name>Plugin.kt`, `.gitignore`,
  README; module list and versions come from the manifest (`modules`, `dependsOn`, `sculk` version).
- Gradle wrapper: ship `gradle-wrapper.jar` (Apache-2.0, ~43 KiB) + `gradlew`/`gradlew.bat` as static
  assets and include them; set the Unix exec bit via fflate `attrs` (**UNVERIFIED** — test on macOS/Linux
  before launch; fallback is a README line "run `gradle wrapper`").
- A Worker endpoint is **not** needed; the site stays assets-only. Revisit only for server-side analytics
  or fetching live Maven metadata.

### 5.7 Starlight specifics to adopt

- Upgrade to Starlight 0.42.x / Astro ≥ 7.2.10 first (required by `starlight-sidebar-topics` 0.9).
- Sidebar: build the `sidebar` array in `astro.config.mjs` from the manifest (group per module, stability
  badges), `autogenerate` for guide sections, `routeMiddleware` for per-page badge injection.
- `docsSchema({ extend })` for `module`, `since`, `threading` fields; `PageTitle` override renders the badge strip.
- `@astrojs/react` 7 for the four playgrounds (`client:visible`); keep `sculk.css`.

---

## 6. Risks

| Risk | Impact | Mitigation |
| --- | --- | --- |
| Both frameworks pre-1.0 (Starlight 0.42.6; Start RC) | Breaking minors (Starlight 0.39 sidebar shape, 0.42 mobile-menu override) | Pin exact versions; `.api`-style snapshot test of rendered HTML for key pages |
| `starlight-versions` immaturity | Content duplication, breaking changes | Use multi-build layout (§5.3) instead |
| Islands cannot share React context | Playground state | nanostores; each playground self-contained |
| Manifest generation needs annotation reading at build time | Gradle task complexity | Start from `.api` dumps + a sidecar YAML for support data; automate later |
| Snippet regions drift when examples are refactored | Build breaks (good) or silently wrong lines (bad) | Regions by name only, never line ranges, in production content |
| Start path: client JS floor 109 KiB gz + DIY shell | Slower pages, weeks of shell work | Only take this path with Fumadocs UI; verify its static search mode first |
| Hydration of synced tabs not browser-tested in prototype | Unknown | Starlight `Tabs` is battle-tested; if Start is chosen, run Playwright before relying on it |
| Workers assets file limit (20 k Free) with many versions + Pagefind fragments | Deploy failure | Count files in CI; Paid plan raises to 100 k |
| Algolia DocSearch eligibility for a paid product | Rejection | Pagefind default |

---

## 7. Decision

**Starlight with generated navigation.** Proceed with the §5 pipelines; the first deliverables are the
Gradle manifest task, the remark snippet plugin, and the manifest-driven sidebar — all of which are
reusable unchanged if the shell ever moves to TanStack Start + Fumadocs (fallback, proven viable in
<2 h by this prototype).

## Sources

- TanStack Start overview (RC status), hosting, static prerendering, CLI reference:
  https://tanstack.com/start/latest/docs/framework/react/overview ·
  https://tanstack.com/start/latest/docs/framework/react/guide/hosting ·
  https://tanstack.com/start/latest/docs/framework/react/guide/static-prerendering ·
  https://tanstack.com/cli/latest/docs/cli-reference
- Cloudflare TanStack Start guide: https://developers.cloudflare.com/workers/framework-guides/web-apps/tanstack-start/
- Cloudflare static assets, limits: https://developers.cloudflare.com/workers/static-assets/ ·
  https://developers.cloudflare.com/workers/platform/limits/
- Fumadocs on TanStack Start / Vite / include / versioning: https://fumadocs.dev/docs/manual-installation/tanstack-start ·
  https://fumadocs.dev/docs/mdx/vite · https://www.fumadocs.dev/docs/mdx/include · https://fumadocs.dev/docs/versioning
- Starlight sidebar, route data, overrides, frontmatter, search, plugins, Code component:
  https://starlight.astro.build/guides/sidebar/ · https://starlight.astro.build/guides/route-data/ ·
  https://starlight.astro.build/guides/overriding-components/ · https://starlight.astro.build/reference/frontmatter/ ·
  https://starlight.astro.build/guides/site-search/ · https://starlight.astro.build/reference/plugins/ ·
  https://starlight.astro.build/components/tabs/ · https://expressive-code.com/key-features/code-component/
- Starlight changelog: https://github.com/withastro/starlight/blob/main/packages/starlight/CHANGELOG.md
- starlight-versions: https://github.com/HiDeoo/starlight-versions · starlight-sidebar-topics: https://github.com/HiDeoo/starlight-sidebar-topics
- Pagefind indexing/filtering/multisite: https://pagefind.app/docs/indexing/ · https://pagefind.app/docs/filtering/ · https://pagefind.app/docs/multisite/
- Algolia DocSearch eligibility: https://docsearch.algolia.com/docs/who-can-apply/
- Docusaurus versioning (reference model): https://docusaurus.io/docs/versioning
- remark-code-import: https://github.com/kevin940726/remark-code-import
- Astro React integration: https://docs.astro.build/en/guides/integrations-guide/react/
- Bun + TanStack Start: https://bun.com/guides/ecosystem/tanstack-start
