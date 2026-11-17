# C — Custom Astro docs stack (no Starlight) for Sculk Docs 6

Date: 2026-11-17 · Scope: engineering evidence for replacing the Starlight shell in
`sculk-studio/docs` with a hand-built Astro site (own layout, nav, home, components), static output
on Cloudflare Workers static assets, Bun as package manager. The repo was read only, nothing was changed.

Versions were checked against the npm registry on 2026-11-17 (`npm view <pkg> version time.modified`).
"Verified" means I read it in official docs, the npm registry, or the installed source in
`docs/node_modules`. "UNVERIFIED" means I inferred it and did not test it.

---

## 0. TL;DR

1. **Astro 7.4.12 + `@astrojs/mdx` 8.1.6, with the processor set explicitly to Sätteri** (`@astrojs/markdown-satteri` 0.4.10).
   Astro 7 made Sätteri, a Rust Markdown/MDX processor, the default, and remark/rehype plugins **no longer run unless you opt back into `unified()`**.
   That shapes everything else: plugins (snippets, `:::` asides) are written as Sätteri mdast plugins, and the processor must be set explicitly so Expressive Code can attach to it.
2. **Code: keep Expressive Code standalone** (`astro-expressive-code` 0.44.3, MIT). It natively supports both the Sätteri and the unified processors.
   It gives frames/titles, copy button, line/inline markers, `ins`/`del`, diff with highlighting and dual themes for about 1.2 KB gz of JS.
   With raw Shiki you would rebuild all of that, and its notation transformers put `// [!code ++]` comments into source, which conflicts with "snippets are imported from real example files".
3. **Search: Pagefind 1.6.0 via its Node API in a small custom integration, with a hand-built ⌘K `<dialog>` on the Pagefind JS API.**
   Filters are `version` and `module`, and code blocks are down-weighted. It loads on first intent, not on page load.
   The Pagefind Component UI (`<pagefind-modal>`) is an accessible fallback, but it costs an extra ~46 KB gz and isolates its styles (`all: initial`).
4. **Styling: plain CSS** (custom properties + `@layer`, Astro scoped styles), **no Tailwind, no vanilla-extract**.
   Fonts are self-hosted through Astro's top-level `fonts` API with the `npm` provider over `@fontsource-variable/*` (OFL-1.1).
   The site is dark-first with a light theme. Native cross-document view transitions are progressive enhancement only (no `<ClientRouter />`), and all motion sits behind `prefers-reduced-motion: no-preference`.
5. **Components: hand-written, zero JS except Tabs (about 1.5 KB custom element), TOC scroll-spy, theme, mobile nav and search.** No headless UI library.
   Starlight is MIT, so its `Tabs` (with the pre-paint restore trick) and `FileTree` can be ported with attribution instead of rewritten.
6. **Migration is almost free for v6 content:** 29 MDX files use only `Aside` (16 files), `Steps` (1) and `Card`/`CardGrid` (1, home).
   Provide **same-named components as MDX globals** (`<Content components={…}>`, the pattern Cloudflare's Nimbus uses), then remove 18 import lines with a codemod.
   Frontmatter `sidebar.order` keeps working if the schema keeps that shape.
7. **Versions:** v5 lives on `main` as 62 Starlight-dialect MDX pages (21 `:::` asides, 10 `Card`s).
   Its tag cannot build with the new shell, so render v5 as a **second content collection inside the one build**, pulled from `main` at build time and served at `/v5/`. Pagefind filters by `version`.
   Keep the "multi-build per git tag" plan from `DOCS_ARCHITECTURE.md` for v7 onwards, when tags already contain the custom shell.
8. **Deploy:** move to `wrangler.jsonc` (Cloudflare now recommends it for new projects). Assets-only, no `nodejs_compat`. Use `public/_headers` for immutable `/_astro/*` and security headers, and a generated `_redirects` for real 301s.
   PR previews come from `wrangler versions upload --preview-alias pr-<n>`. Run `actions/checkout` with `fetch-depth: 0` so git last-updated dates are real.

Decisions the owner must take (section 11): which branch deploys production while v5 is still the released major, and whether an ADR supersedes ADR-0016.

---

## 1. Baseline: what exists today

| Item | Value (read from repo) |
| --- | --- |
| Packages | `astro` 7.4.12, `@astrojs/starlight` 0.43.1, `sharp` 0.35.5, `wrangler` 4.163.0 (dev). Installed transitively: `@astrojs/mdx` 8.1.6, `astro-expressive-code` 0.44.1, `shiki` 4.6.0, `pagefind` 1.5.2, `@astrojs/sitemap` 3.7.5, `satteri` 0.10.5 |
| Content (v6, `dev`) | 29 MDX pages under `src/content/docs/{start,concepts,guides,reference}` + `index.mdx` (splash/hero). Frontmatter: `title`, `description`, `sidebar.order` (28 files); 3 587 lines total |
| Starlight usage (v6) | `import { Aside } …` ×16, `{ Aside, Steps }` ×1, `{ Aside, Card, CardGrid }` ×1 (home). Elements: `<Aside>` 19 (types: default/`caution`, some with `title`), `<Steps>` 1, `<Card icon=…>` 4 (icons `random`, `approve-check`, `puzzle`, `laptop`), `<CardGrid>` 1. No `:::` directives, **no code-fence meta** (94 `kotlin`, 2 `yaml`, 1 `json`, 1 `sh` fences, all pasted, none imported) |
| Custom | `src/components/ModuleRoadmap.astro` (imports Starlight `Badge`, uses `--sl-text-xs`, `--sl-color-gray-2`), `src/lib/repo-tables.ts` (reads `MODULES.md` at build), `src/styles/sculk.css` (145 lines of `--sl-*` overrides; dark `#050a0f`/accent `#00e5ff`, light accent `#005965` ~8:1) |
| v5 content (`main`) | 62 MDX pages, Starlight 0.41.6. 21 `:::note/caution/danger/tip` directives, 8 `<Card>`, 2 `<CardGrid>`, 1 splash home |
| Deploy | `wrangler.toml`: assets-only, `directory=./dist`, `html_handling=auto-trailing-slash`, `not_found_handling=404-page`, `compatibility_date=2025-01-01`, `nodejs_compat` (unused, there is no Worker script). `.github/workflows/docs.yml` deploys **from `main`** on `docs/**` changes, Node 24 + Bun 1.4.4, default shallow checkout |
| Current output | `dist/`: 90 files, 2.8 MiB. Per-page JS: Starlight `ui-core` 27.0 KB gz + ~5 KB gz of small scripts. CSS `common` 11.6 KB gz + EC 4.0 KB gz. Pagefind dir 878 KiB / 45 files |
| Examples | `examples/{economy,hud,network,shop,staff}` exist; **no `#region` markers yet** |

Requirements carried over from `DOCS_ARCHITECTURE.md` / ADR-0016 / `g-docs-spike.md`: manifest-generated sidebar, Sculk/Minecraft/platform selectors (CSS-filtered, no hydration), snippets imported from `examples/*` with `#region` (build fails if missing), Pagefind with `version`+`module` filters, versioned builds, React playground islands with nanostores, `/create` generator (fflate, client-side), Cloudflare Workers assets-only, build gates (missing page for manifest symbol, missing region, broken link, missing `module`, file-count limit).

---

## 2. Recommended stack (exact versions, npm 2026-11-17)

Pin exact versions (no `^`) for the 0.x packages. Bun's lockfile already pins the rest.

| Purpose | Package | Version | Licence | Notes |
| --- | --- | --- | --- | --- |
| Framework | `astro` | **7.4.12** | MIT | Vite 8, Rust compiler, Zod 4 (`zod ^4.6.7` dep), Sätteri default. Engines: Node ≥ 22.23.4 |
| MDX | `@astrojs/mdx` | **8.1.6** | MIT | peer: `astro ^7.2.10`, `@astrojs/markdown-satteri ^0.4.0` or `@astrojs/markdown-remark ^7.3.0` |
| Markdown processor | `@astrojs/markdown-satteri` | **0.4.10** | MIT | depends on `satteri ^0.11.6` (latest 0.11.7, repo `bruits/satteri`, created 2026-03, 1.3k★, active) |
| Code blocks | `astro-expressive-code` | **0.44.3** | MIT | supports Sätteri and unified processors (read in source, §4) |
| Code plugins (optional) | `@expressive-code/plugin-collapsible-sections` / `@expressive-code/plugin-line-numbers` | **0.44.3** | MIT | collapse long import blocks; line numbers only where useful |
| Search index | `pagefind` | **1.6.0** | MIT | Node API (`createIndex`, `addDirectory`/`addHTMLFile`, `writeFiles`) |
| Sitemap | `@astrojs/sitemap` | **3.7.5** | MIT | |
| Islands | `@astrojs/react` | **7.1.9** + `react`/`react-dom` **19.3.1** | MIT | playgrounds and `/create` only. Peer `oxc-transform-react >=0.145 <0.157` (latest is 0.158, so do not add it explicitly at latest) |
| Shared state | `nanostores` **1.5.5**, `@nanostores/persistent` **1.3.5**, `@nanostores/react` **2.0.1** | | MIT | selectors ↔ islands |
| Zip | `fflate` | **0.8.3** | MIT | `/create` |
| Images | `sharp` | **0.35.5** | Apache-2.0 | build-time only |
| Fonts | `@fontsource-variable/<family>` | **5.3.0** | OFL-1.1 | e.g. `inter`, `geist`, `jetbrains-mono`, `geist-mono` (all 5.3.0). OFL allows bundling in a commercial site, but not selling the font alone |
| Type check | `@astrojs/check` **0.9.12** + `typescript` **6.0.3** | | MIT / Apache-2.0 | `@astrojs/check` peer is `typescript ^5 \|\| ^6`. **Do not take TypeScript 7.0.2 yet** |
| Deploy | `wrangler` | **4.163.0** | MIT OR Apache-2.0 | ≥ 4.34 needed for 100k-file limit, ≥ 4.135 for `wrangler preview` |
| E2E / a11y | `@playwright/test` **1.65.1**, `@axe-core/playwright` **4.14.0** | | Apache-2.0 / MPL-2.0 (axe-core) | test-only, never shipped |
| Perf budget | `@lhci/cli` | **0.16.1** | Apache-2.0 | CI only |
| Link check (optional) | `linkinator` **8.1.0** or own `astro:build:done` gate | | MIT | own gate preferred (no network) |
| Package manager | Bun **1.4.x** | | MIT | npm `bun` latest 1.4.4; this machine has 1.4.5; CI pins 1.4.4 |

**Not recommended:** `@astrojs/starlight` (decision taken). Tailwind v4 (`tailwindcss`/`@tailwindcss/vite` 4.3.5, fine but see §6). `@vanilla-extract/*`. `astro-pagefind` 2.0.1 (MIT, works, but the README says its `Search.astro` is in maintenance mode, and a 40-line own integration gives control over weights and filters, as Chartist does). `starlight-versions`. `astro-icon` (hand-roll a 30-line `Icon.astro` with the few Lucide SVGs; Lucide is ISC). `satori` / `@resvg/resvg-js` for OG images are **MPL-2.0**: acceptable as build-time tools (file-level copyleft, nothing bundled), flagged per policy.

**Runtime:** use Bun as the package manager and script runner. **Run Astro on Node** (`bun run build` executes `astro`'s Node shebang). Astro declares `node >=22.23.4`, and native bindings (Rust compiler, Sätteri napi, sharp) are only tested on Node. Using `bun --bun astro build` is UNVERIFIED, so do not rely on it. `bun test` works well for pure TS units (snippet extraction, nav tree).

---

## 3. Astro 7 content pipeline without Starlight

### 3.1 Astro 7 changes that matter (verified: docs.astro.build/en/guides/upgrade-to/v7/)

- **Sätteri is the default Markdown/MDX processor**, and `@astrojs/markdown-remark` is no longer installed.
  `markdown.remarkPlugins`/`rehypePlugins`/`gfm` are **deprecated in favour of `markdown.processor`**.
  Sätteri plugins are `mdastPlugins`/`hastPlugins` (same AST shapes as remark/rehype, different API: `defineMdastPlugin({ name, code(node, ctx) { ctx.replaceNode(…) } })`).
- `@astrojs/mdx` 8.1.6 source: MDX inherits `markdown.processor` (`extendMarkdownConfig` default). If `mdx({ remarkPlugins })` is passed and `@astrojs/markdown-remark` is installed, MDX silently **switches to unified**; otherwise it warns and ignores them.
  Cloudflare's Nimbus documents the same trap: "remark plugins attached via `mdx({ remarkPlugins })` silently no-op".
- Sätteri feature flags (from `satteri/dist/compile.d.ts`): `gfm` (on), `frontmatter` (on), `directive` (**`:::` containers, off by default**, needed for v5 content), `math`, `headingAttributes`, `smartPunctuation` (off by default), and others.
- `compressHTML` now defaults to `'jsx'` and **can remove spaces between inline elements**. Set `compressHTML: true` for prose-heavy docs, then diff the output.
- The Rust compiler is stricter: unclosed tags and invalid nesting are errors. That is good for a11y.
- Advanced routing reserves `src/fetch.ts` (irrelevant to us). Several `astro:transitions` internals were removed.

**Recommendation:** set `markdown.processor: satteri({...})` **explicitly**.
Expressive Code only hooks into Sätteri when the processor object is present. With no processor it falls back to the deprecated `markdown.rehypePlugins` path (read in `astro-expressive-code/dist/index.js` lines 507–528).
Keep all plugin logic in pure functions (`extractRegion`, `directiveToAside`). If Sätteri blocks something, the fallback is `unified({ remarkPlugins, rehypePlugins })` from `@astrojs/markdown-remark` (Fastify's site does exactly this on Astro 7.2), and EC supports it too.

### 3.2 Collections and routes

```ts
// src/content.config.ts
import { defineCollection } from 'astro:content';
import { glob } from 'astro/loaders';
import { z } from 'astro/zod';

const page = z.object({
  title: z.string(),
  description: z.string(),
  // Same shape as Starlight's so 28 files keep working unchanged.
  sidebar: z.object({
    order: z.number().optional(),
    label: z.string().optional(),
    hidden: z.boolean().default(false),
    badge: z.enum(['new', 'experimental', 'deprecated']).optional(),
  }).default({}),
  module: z.string().optional(),          // becomes required in module sections (build gate)
  toc: z.union([z.literal(false), z.object({ min: z.number(), max: z.number() })]).default({ min: 2, max: 3 }),
  lastUpdated: z.coerce.date().optional(), // overrides git
  draft: z.boolean().default(false),
});

export const collections = {
  docs: defineCollection({ loader: glob({ base: './src/content/docs', pattern: '**/*.{md,mdx}' }), schema: page }),
  // v5 snapshot pulled from `main` at build time (§8). Starlight-only keys are tolerated, not used.
  v5: defineCollection({ loader: glob({ base: './.versions/v5', pattern: '**/*.{md,mdx}' }), schema: page.passthrough() }),
};
```

- Routes: `src/pages/index.astro` (bespoke home, replaces `index.mdx` splash), `src/pages/[...slug].astro` (v6), `src/pages/v5/[...slug].astro` (v5), `src/pages/create.astro`, `src/pages/404.astro`, `src/pages/v5/404.astro`.
  `getStaticPaths` maps `getCollection('docs')` → `{ params: { slug: entry.id }, props: { entry } }`. Collection order is non-deterministic per the docs, so always sort.
- `const { Content, headings } = await render(entry)`. `headings` is `{ depth, slug, text }[]`, which gives the TOC directly.
- URLs stay **identical to today** (`/guides/hud/`): `trailingSlash: 'always'`, `build.format: 'directory'`, and Workers `html_handling: auto-trailing-slash`. Astro's heading IDs use github-slugger, the same as Starlight, so existing deep links and anchors survive. **No redirects are needed for v6.**

### 3.3 Navigation data (`src/lib/nav.ts`, pure, unit-tested with `bun test`)

| Feature | How |
| --- | --- |
| **Sidebar** | `src/navigation.ts` declares sections in IA order (today: Start, Concepts, Guides, Reference; later Start · Learn · Guides · Concepts · Reference · Recipes · Migration · Advanced) with labels. Pages are grouped by the first id segment and sorted by `sidebar.order`, then title. Later, `sculk-manifest.json` adds module groups and stability badges (`symbols[].page` → entry). A manifest `page` without an entry throws (build gate). Rendered as `<nav aria-label="Docs">` with `<details open>` groups (zero JS), `aria-current="page"` on the active link, and current group open |
| **ToC** | `headings.filter(h => h.depth >= min && h.depth <= max)` nested by depth. `<nav aria-labelledby>` sticky on wide screens. Active-heading scroll-spy is a ~1 KB `IntersectionObserver` script, a pure enhancement |
| **Prev/next** | Flatten the sidebar tree in order and take the neighbours. Frontmatter override optional (Nimbus does `prev`/`next` overrides) |
| **Breadcrumbs** | Section → (module group) → page, as `<nav aria-label="Breadcrumb"><ol>` with `aria-current="page"` on the last item |
| **Edit link** | `https://github.com/SculkStudios/sculk-library/edit/dev/docs/${entry.filePath}` (`filePath` is project-relative for `glob()`) |
| **Last updated** | An integration runs **one** `git log --format=%cI --name-only -- src/content` at `astro:config:setup` and builds a path→date map (one process instead of one per page). Frontmatter `lastUpdated` wins. **CI must use `actions/checkout` with `fetch-depth: 0`**: today's workflow is shallow, which makes every page "updated at the last commit" |
| **Sitemap** | `@astrojs/sitemap` with `filter` excluding `/v5/` once v6 is the released major, plus `/404/` |
| **Redirects** | Keep a typed `src/redirects.ts` and emit `dist/_redirects` in `astro:build:done`. Workers static assets then serve real 301/308s: up to 2 000 static + 100 dynamic rules, first match wins, not applied to Worker responses (verified on developers.cloudflare.com). Avoid Astro's `redirects` config for static output, because it produces meta-refresh HTML pages (my understanding of current behaviour, not re-verified for v7). Use the same file to retire URLs when the IA changes |
| **i18n-readiness** | No i18n now. Keep every UI string in `src/i18n/ui.ts` (`t('search.placeholder')`), set `<html lang>` from config, build all hrefs through one `href()` helper (base-aware for `/v5/`), and leave entry ids locale-free. If needed later: Astro `i18n: { locales, defaultLocale: 'en', routing: { prefixDefaultLocale: false } }` + `docs/<locale>/…`. Pagefind indexes per `lang` automatically |
| **llms.txt (optional, cheap)** | `src/pages/llms.txt.ts` + per-page Markdown, as Drizzle, Nimbus and capnweb do. Useful for plugin authors using agents. Not required |

---

## 4. Code: Expressive Code vs raw Shiki, and the snippet pipeline

### 4.1 Comparison

| | Expressive Code 0.44.3 (`astro-expressive-code`) | Astro built-in Shiki 4.6 (`markdown.shikiConfig`) + `@shikijs/transformers` |
| --- | --- | --- |
| Engine | Shiki under the hood (same TextMate grammars) | Shiki |
| Frames, file titles, terminal frames | ✔ (`title="build.gradle.kts"`, auto terminal for `sh`) | build yourself (meta parsing + wrapper) |
| Copy button | ✔ (≈1.2 KB gz JS, external hashed file; measured in current `dist`) | build yourself |
| Line markers / labels | ✔ `{1,4-6}`, `ins={3}`, `del={2}`, labelled `{"1":5}` | `transformerMetaHighlight` (`{1,3}`) only |
| Inline markers | ✔ `"text"` / `/regex/` with mark/ins/del | `transformerMetaWordHighlight` (mark only) |
| Diff | ✔ `diff lang="kotlin"` keeps highlighting | `transformerNotationDiff`: **needs `// [!code ++]` comments in the source**, which is unacceptable for imported example files |
| Collapsible sections / line numbers | ✔ official plugins | build yourself |
| Tabs | no (Tabs are our component wrapping code blocks; same as Starlight) | no |
| Theming | any VS Code/Shiki theme JSON or `ExpressiveCodeTheme`. `themes: [dark, light]`, `themeCssSelector` (e.g. `[data-theme='dark']`), `useDarkModeMediaQuery`, `styleOverrides`, `minSyntaxHighlightingColorContrast` (default 5.5, auto-fixes low-contrast tokens), `emitExternalStylesheet` (cacheable CSS) | `themes: { light, dark }` + `--shiki-dark` CSS variables, or the `css-variables` theme (Drizzle does this) |
| Processor support in Astro 7 | Sätteri **and** unified, detected from `markdown.processor` (read in source) | built in |
| `<Code>` component | `astro-expressive-code/components` (same features) | `astro:components` `<Code>` (does **not** inherit `shikiConfig`) |
| Cost | ~4 KB gz CSS + 1.2 KB gz JS today. Build time is fine at this scale (current site: 63 pages in 3.2 s, spike) | smallest output, most custom code |

**Recommendation: Expressive Code.** This is the one Starlight piece worth keeping, because it is a standalone MIT package with a first-class Astro integration.
Use two custom themes (dark-first plus light) generated from the brand tokens as Shiki theme JSON, with ``themeCssSelector: (t) => `[data-theme='${t.type}']` ``.
Set `useDarkModeMediaQuery: true` for the no-JS case and `styleOverrides` for radius, borders and fonts.
Place `expressiveCode()` **before** `mdx()` in `integrations`.

**Grammar quality.** Shiki ships `kotlin` (aliases `kt`, **`kts`**) from `fwcd/vscode-kotlin` (MIT), plus `groovy` and `java` from `microsoft/vscode`, and `yaml`/`toml` from TextMate bundles (tm-grammars README).
These are TextMate (regex) grammars: good token colouring for keywords, strings, annotations and string templates, but no semantic highlighting (types and extension functions look like identifiers).
It is the same grammar Starlight uses today, so there is no regression. `build.gradle.kts` → `kotlin`, `pom.xml` → `xml`, `paper-plugin.yml` → `yaml`, `gradle.properties` → `properties`.
Add a "grammar zoo" page of tricky Kotlin (DSL lambdas, `@file:` annotations, raw strings, `context(...)`) to the visual test set and judge by eye. UNVERIFIED for edge cases.

### 4.2 Snippets imported from real source (`#region`)

Keep the design from `DOCS_ARCHITECTURE.md`. Only the plugin host changes, from remark to Sätteri.

```
src/lib/snippets/extract.ts         pure: readFile → find `// #region name` … `// #endregion` (nested OK),
                                    strip all other region markers, dedent, trim; throw with file:line on miss
src/lib/snippets/satteri-snippets.ts mdast plugin, `code` visitor
src/components/mdx/Snippet.astro     <Snippet file=… region=… title?> → extract() + EC <Code> (for JSX contexts)
```

```ts
// sketch: verify ctx.replaceNode semantics for `code` nodes against satteri 0.11 docs
import { defineMdastPlugin } from 'satteri';
export const sculkSnippets = ({ repoRoot }: { repoRoot: string }) =>
  defineMdastPlugin({
    name: 'sculk-snippets',
    code(node, ctx) {
      const m = /(?:^|\s)snippet=(\S+)/.exec(node.meta ?? '');
      if (!m) return;
      const [file, region] = m[1].split('#');           // region names only; `#L10-20` rejected
      const value = extractRegion(resolve(repoRoot, file), region); // throws → build fails
      const meta = node.meta!.replace(m[0], '');
      ctx.replaceNode(node, { ...node, value, meta: /title=/.test(meta) ? meta : `${meta} title="${basename(file)}"` });
    },
  });
```

- Authoring stays ```` ```kotlin snippet=examples/hud/src/main/kotlin/HudPlugin.kt#scoreboard ````, and every EC meta (`{3-5}`, `ins=`, `collapse={1-8}`) still works because substitution happens in mdast before EC's hast plugin runs.
- Nothing from `examples/` reaches the client bundle, because substitution happens at build (fixes spike friction #3).
- **Dev reload gap (UNVERIFIED):** editing an example `.kt` will not re-render the MDX that imports it. Add a tiny Vite plugin that records snippet→MDX dependencies during transform, watches `../examples/**`, and invalidates those modules (or sends `full-reload`).
- Add the source link next to the title (`source` from the manifest, or `github.com/…/blob/dev/<file>#L…` computed from the region's line range at build time).
- Marker hygiene: `// #region name` is a plain comment, so it is ktlint-neutral. Add a Gradle/ktlint-independent check that region names are unique per file.

---

## 5. Search (Pagefind 1.6.0)

### 5.1 What Pagefind gives us (verified on pagefind.app + GitHub releases)

- 1.5.0 (2026-04): **Component UI** (web components: `<pagefind-modal>`, `<pagefind-modal-trigger>` with `mod+k`, searchbox, results, filter dropdown/pane, templates), **metadata search by default with `metaWeights` (title 5.0)**, diacritics, index chunks ~45% smaller, **search in a Web Worker**.
  1.6.0 (2026-11-01): `--max-fragments`, sub-result titles, Component UI fixes.
- JS API: `const pagefind = await import('/pagefind/pagefind.js')`, `options({ baseUrl, ranking, excerptLength })`, `init()`, `preload(term)`, `debouncedSearch(term, { filters, sort }, ms)`, `filters()`, `result.data()` → `{ url, excerpt, meta, sub_results[] }` (sub-results link to heading anchors).
- Filters: `data-pagefind-filter="version:6"` (inline value), `module[content]` on a `<meta>`. Query with `{ filters: { version: '6', module: { any: [...] } } }` (`any`/`all`/`none`/`not`).
- Ranking: `termFrequency 1.0`, `termSimilarity 1.0`, `pageLength 0.75`, `termSaturation 1.4`, `diacriticSimilarity 0.8`, `metaWeights { title: 5.0 }`, plus `data-pagefind-weight` on elements.
- Node API: `createIndex({ rootSelector, excludeSelectors, … })`, `addDirectory`, `addCustomRecord({ url, content, language, meta, filters })`, `writeFiles({ outputPath })`, `close()`.

### 5.2 Measured payload (current `dist`, Pagefind 1.5.2)

| File | gz | Loaded when |
| --- | --- | --- |
| `pagefind.js` | 12.8 KB | on search intent |
| `pagefind-worker.js` | 11.9 KB | on first search |
| `wasm.en.pagefind` | ~68 KB (already compressed) | on first search |
| index/fragment chunks | small, per term/result | per query |
| `pagefind-component-ui.js` + `.css` | **39.1 KB + 7.1 KB** | only if Component UI is used |
| (Starlight today) `pagefind-ui.js` | 29.7 KB | |

### 5.3 Recommendation

- **Indexing:** an own `integrations/pagefind.ts` (~60 lines, the Chartist pattern) at `astro:build:done`:
  - Index only `<article data-pagefind-body>`.
  - Inject `data-pagefind-weight="0.4"` on `<pre>` inside `.expressive-code` before `addHTMLFile`, so the guide that *explains* `withEntity` outranks the pages whose code merely *calls* it.
  - Exclude `og/`, `create/` and redirect stubs.
  - Write to `dist/pagefind/`.
  - Serve the last build's index in `astro dev`, as Chartist and `astro-pagefind` do.
- **Page tagging (layout):** `<meta data-pagefind-filter="version[content]" content="6">`, `<meta data-pagefind-filter="module[content]" content={module}>`, `data-pagefind-meta="section:Guides"`, and `stability` from the manifest. Set `ranking.metaWeights: { title: 5, module: 3, section: 1 }`.
- **UI:** a hand-built ⌘K **`<dialog>`** (Fastify's `Search.astro` is a close reference):
  - The trigger button shows a `⌘K/Ctrl K` hint and has `aria-keyshortcuts`.
  - Handle `mod+k` and `/` globally, but not while focus is in an input.
  - The input is `role="combobox"` with `aria-expanded`, `aria-controls` and `aria-activedescendant`. Results are `role="listbox"`, grouped per page with sub-results as heading anchors.
  - A `role="status"` live region announces "12 results"; arrow/Home/End/Enter/Esc are handled; focus returns to the trigger on close.
  - Filter chips: the version defaults to the current page's version (toggle "all versions"), plus module chips from `pagefind.filters()`.
  - Load `pagefind.js` on `pointerenter`/`focus` of the trigger or on the shortcut, then `preload()` while the user types.
  - Budget: ≤ 4 KB gz own JS.
  - This is the only way to get a bespoke, premium result layout. Component UI forces `all: initial` and `--pf-*` variables.
- **Fallback:** if the custom combobox cannot pass the a11y tests in time, ship `<pagefind-modal>` themed through `--pf-*` (WAI-ARIA modal/combobox/listbox per Pagefind docs, mod+k built in) and swap later.
- **Not chosen:** Algolia DocSearch (vendor, eligibility review for a commercial product) and Orama (an extra client index download), as in the spike.
- **CSP note:** Pagefind compiles WebAssembly and starts a Worker. A CSP needs `script-src 'self' 'wasm-unsafe-eval'` and `worker-src 'self'` (UNVERIFIED exact set; test before enabling `security.csp`).

---

## 6. Styling, fonts, theming, motion

### 6.1 Approach: plain CSS + cascade layers + custom properties

| Option | Verdict |
| --- | --- |
| **Plain CSS, `@layer`, tokens as custom properties, Astro scoped `<style>`** | **Recommended.** The design is bespoke and prose-heavy, so you would write a prose stylesheet anyway. Tokens map 1:1 to EC `styleOverrides` and the theme JSON. There is no build plugin or class soup in MDX-rendered HTML, and the existing `sculk.css` palette (contrast already tuned: `#005965` ≈ 8:1 on light) ports directly. `shravanngoswamii/astro-docs` uses layers for the same reason: user CSS wins without `!important` |
| Tailwind v4.3.5 (`@tailwindcss/vite`) | Viable: Nimbus, Fastify and Chartist all use it on Astro 7. It suits teams that think in utilities. Costs: prose still needs custom CSS (or `@tailwindcss/typography`), MDX output cannot carry utilities, and design tokens end up in `@theme`. Choose it only if the designer works that way |
| vanilla-extract (`@vanilla-extract/css` 1.21.3, Vite plugin 5.2.6) | No. TS-authored styles help component libraries with runtime theming, but add a build plugin and little value for a static site |

Layer order (one file, imported once in `BaseLayout.astro`):
`@layer reset, tokens, base, layout, components, prose, code, utilities, overrides;`
Put component `<style>` blocks in `@layer components { … }` so layer order, not scoped-attribute specificity, decides precedence.
EC emits its own stylesheet. Theme it through EC options, never through selectors fighting its reset.

Modern CSS to use, with progressive fallbacks: `color-mix()`/`oklch()` for tints, `light-dark()` where convenient, nesting, `:has()` (e.g. `.card:has(a:focus-visible)`), container queries for cards/tabs, `text-wrap: balance` on headings, `scroll-padding-top` = header height (needed for WCAG 2.4.11), `scrollbar-gutter: stable`.
Check MDN Baseline at implementation time.

### 6.2 Theming: dark-first, light supported

- `:root { color-scheme: dark; --bg: …; }` defaults to dark, and `:root[data-theme='light'] { color-scheme: light; … }`.
- A blocking inline `<script is:inline>` in `<head>` (≈300 B) reads `localStorage.theme` (`system|dark|light`, wrapped in try/catch) and `matchMedia('(prefers-color-scheme: light)')`, and sets `data-theme` **before first paint**. This means no flash and no CLS.
  With JS disabled, use `@media (prefers-color-scheme: light) { :root:not([data-theme]) { … } }`.
- EC: `themeCssSelector` → `[data-theme='dark'|'light']`. Pagefind Component UI (if used): `data-pf-theme="dark"`.
- The theme toggle is a `<button aria-pressed>` or a 3-state menu, and announces its state.

### 6.3 Fonts (self-hosted, variable)

- Astro's **top-level `fonts` config** (no longer under `experimental`; listed in the configuration reference). Providers include `fontsource` (remote), `local` and **`npm`**.
  `fontProviders.npm({ remote: false })` resolves only from `node_modules`, which gives **offline, deterministic builds** from `@fontsource-variable/*`. The exact family/package option shape needs verifying against the provider reference.
- `<Font cssVariable="--font-sans" preload />` for the body face only. The mono face loads on demand (code is below the fold on most pages).
  Use a variable weight range (`weights: ['100 900']` for Fontsource-style providers) and Latin + Latin-ext subsets only.
- Astro generates **metric-adjusted fallbacks** (`optimizedFallbacks`, default on) from the last generic fallback. That gives near-zero CLS on swap. Keep `font-display: swap` (Astro's default behaviour is not stated on the page; verify in the emitted `@font-face`).
- Typical cost: one Latin variable woff2 of ~30–50 KB for the body, plus mono ~30 KB lazily.

### 6.4 View transitions and motion

- **Use native cross-document view transitions, not `<ClientRouter />`:**
  `@media (prefers-reduced-motion: no-preference) { @view-transition { navigation: auto; } }`, with `view-transition-name` on header, sidebar and article so only the article cross-fades.
  The site stays a true MPA: no script re-execution lifecycle (`astro:page-load`, `data-astro-rerun`), and no interference with Pagefind, Tabs restore or islands.
  Browsers without support just navigate. Astro itself says `<ClientRouter />` "will increasingly become unnecessary".
- Sidebar scroll position: save `scrollTop` to `sessionStorage` on `pagehide` and restore in an inline script right after the sidebar markup (what Starlight does). `transition:persist` needs ClientRouter, so do not use it.
- Every animation (hover glows, dialog open, tab underline) sits behind `prefers-reduced-motion: no-preference`. Hover-only affordances get a focus equivalent.

---

## 7. Components: the Starlight replacements

All are `.astro` components with **zero client JS unless marked**. They are registered as MDX globals in `src/components/mdx/index.ts` and passed via `<Content components={mdx} />`, so authors never import them.
This is the pattern Nimbus uses (`src/components.ts`, "components available inside MDX without import"), and Nimbus deliberately reuses the Starlight names (`Aside`, `Card`, `CardGrid`, `Steps`, `Tabs`, `TabItem`).

| Component | API (Starlight-compatible where it exists) | Implementation notes |
| --- | --- | --- |
| `Aside` | `type="note\|tip\|caution\|danger"`, `title?` | `<aside aria-label={title ?? typeLabel}>` with an icon (`aria-hidden`). Contrast-checked tints per type. Also the target of the `:::type[title]` mapping plugin (Sätteri `directive: true` + mdast `containerDirective` visitor) for v5 |
| `Tabs` / `TabItem` | `syncKey?`, `label`, `icon?` | **JS (~1.5 KB custom element).** WAI-ARIA tabs pattern: `role="tablist/tab/tabpanel"`, roving tabindex, arrows/Home/End, `aria-selected`, `aria-controls`. Panels are server-rendered with `hidden` except the first. Synced keys persist in `localStorage` and are restored **before paint** by a tiny inline restore element placed after each synced set (port of Starlight's `starlight-tabs-restore`, MIT, keep the notice). All panel content stays in the HTML, so search and crawlers see everything |
| `PackageTabs` | presets: Gradle (Kotlin DSL) / Gradle (Groovy) / Maven | `Tabs syncKey="build"` with EC blocks inside |
| `PlatformTabs` | Paper / Folia / Canvas / Velocity | `Tabs syncKey="platform"`, with its initial value from the platform selector store |
| `Platform`, `Since` | `only="folia"`, `minecraft="26.1"` / `sculk="6.1"` | Render all variants with `data-platform`/`data-mc`. CSS hides them against `<html data-platform=…>`. Zero hydration, as `DOCS_ARCHITECTURE.md` specifies |
| `Steps` | wraps one `<ol>` | CSS counters plus a connector line. Validate at build that the slot is a single `<ol>` (as Starlight does) |
| `Card`, `CardGrid`, `LinkCard` | `title`, `icon?`; `stagger?`; `title`, `href`, `description?` | Grid via container queries. `LinkCard` has a single link with the whole card clickable through `::after` (no nested interactive elements) |
| `Badge` | `text`, `variant="note\|tip\|success\|caution\|danger"`, `size?` | Text plus colour, never colour alone. Used by `ModuleRoadmap` |
| `SupportBadge`, `StabilityBadge` | `native\|emulated\|degraded\|unavailable`; `stable\|experimental\|internal` | Semantic wrappers; data from the manifest |
| `CompatStrip` | (none; reads slug → manifest) | Manifest entry evaluated against the selected MC version and platform |
| `FileTree` | slot: nested Markdown list | Build-time transform of the rendered list into a tree with folder/file icons and `<details>` folders (zero JS). Port Starlight's `FileTree` + `rehype-file-tree` (MIT) logic. It parses the slot HTML string, independent of the Markdown processor |
| `Snippet` | `file`, `region`, `lang?`, `title?`, EC props | §4.2 |
| `Icon` | `name` | Local SVG map (Lucide, ISC) covering the ~15 icons used, including mappings for Starlight names `random`, `approve-check`, `puzzle`, `laptop` |
| Shell | `Header`, `Sidebar`, `MobileNav` (`<dialog>` drawer), `Toc`, `Breadcrumbs`, `PrevNext`, `EditLink`, `LastUpdated`, `ThemeToggle`, `VersionSwitch`, `PlatformSelect`, `McVersionSelect`, `SearchDialog`, `SkipLink`, `Banner` (dev/archived notice), `Footer` | JS only in: MobileNav (native `<dialog>.showModal()` handles inert, Esc and focus return), Toc spy, ThemeToggle, selectors (≤1 KB, write `<html data-*>` + `localStorage`), Search |
| Playgrounds | React islands `client:visible` | Only on their pages; state via nanostores (islands cannot share React context) |

**Headless UI library: none.** The interactive surface is three patterns: dialog (native `<dialog>`), tabs (≈80 lines) and combobox (search).
Radix, Ark and Base UI are React-bound and would force hydration for chrome that should be static. Zag has vanilla adapters but is heavier than the hand-written versions.
Inside the React playground islands, a headless lib is fine if one is needed (Radix/Base UI/Ark, all MIT).

---

## 8. Versioned docs (v5 / v6)

**Facts:** v5 docs live on `main` (62 MDX, Starlight 0.41.6 dialect, `:::` asides, Cards). Production deploys from `main` today. The new shell will exist only on `dev`.
`DOCS_ARCHITECTURE.md` plans "current major at `/`, archived majors built from their git tag with `base: '/vN'`, merged into one assets upload".

**Problem:** building v5 from its tag would produce the **old Starlight look** under `/v5/`, because tags predate the custom shell. That means two designs, two search UIs and no shared switcher.

**Recommendation (two-tier):**

1. **v5 now: content collection, single build.** In CI, check out `main`'s `docs/src/content/docs` into `docs/.versions/v5/` (a second `actions/checkout` with `ref: main`, `path:`, sparse checkout). Locally, use `git archive origin/main docs/src/content/docs | tar -x`. Gitignore it so no content is copied into the repo, which avoids the objection to `starlight-versions`.
   Then:
   - `collections.v5` uses the same schema (passthrough), and `src/pages/v5/[...slug].astro` the same layout, with a "You're reading v5" banner and canonical/sitemap/noindex rules decided by release state (below).
   - Starlight dialect works through the same-named globals plus the `:::` directive plugin. Imports from `@astrojs/starlight/components` resolve through a Vite alias to `src/components/mdx/index.ts`, so **v5 needs zero edits**.
   - Pagefind: one index, every page tagged `version:5|6`. Search defaults to the current page's version.
   - `VersionSwitch` keeps the same slug when it exists in the other version and falls back to that version's root. The available versions come from a generated `versions.json`, as the architecture specifies.
2. **v7 onwards: multi-build per tag** as `DOCS_ARCHITECTURE.md` says, because tags will then contain the custom shell. Run `astro build --base /v6` (or `site`+`base` from env, as Chartist does) from the tag, copy `dist/` into the main `dist/v6/`, and merge Pagefind indexes. One index per version is simpler: the switcher already scopes search. `mergeIndex` is available if cross-version search is wanted.

**Release-state rules:** while 6.0 is unreleased, v5 is the *released* major. Production must keep serving v5 docs as primary until 6.0 ships (§11, decision 1).
After 6.0, `/v5/*` gets `X-Robots-Tag: noindex` via `_headers` (or `<meta name="robots">`) and leaves the sitemap, and the banner links to the v6 equivalent.

**File budget:** 90 files today. Workers static assets allow **20 000 files per version on Free and 100 000 on Paid, 25 MiB per file** (wrangler ≥ 4.34 for the higher limit).
Pagefind's new `--max-fragments` caps growth. Keep the "file count < limit" build gate.

---

## 9. Cloudflare Workers static-assets deployment

`wrangler.jsonc` ("Cloudflare recommends using `wrangler.jsonc` for new projects"):

```jsonc
{
  "$schema": "./node_modules/wrangler/config-schema.json",
  "name": "sculk-docs",
  "compatibility_date": "2026-11-17",
  // assets-only: no `main`, no `nodejs_compat` (there is no Worker code)
  "assets": {
    "directory": "./dist",
    "html_handling": "auto-trailing-slash",
    "not_found_handling": "404-page"   // nearest 404.html, so /v5/404.html serves v5 misses
  },
  "preview_urls": true
}
```

`public/_headers` (≤ 100 rules, ≤ 2 000 chars/line; rules merge and duplicate values are comma-joined, so detach with `!Header` before overriding). These headers do not apply to Worker responses, which we have none of:

```
/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=()
  Strict-Transport-Security: max-age=31536000; includeSubDomains
/_astro/*
  Cache-Control: public, max-age=31536000, immutable
/pagefind/fragment/*
  Cache-Control: public, max-age=31536000, immutable
/pagefind/index/*
  Cache-Control: public, max-age=31536000, immutable
```

The defaults are `Cache-Control: public, max-age=0, must-revalidate` + `ETag`, which is right for HTML, `pagefind.js`, `pagefind-entry.json` and `versions.json`.
Mark only content-hashed paths as immutable. Astro's `/_astro/*` is hashed (fonts from the Fonts API land there too; verify the output path).
Whether Pagefind's `fragment/` and `index/` filenames are content-hashed in 1.6 is UNVERIFIED (1.5.2 notes "deterministic index filenames"); confirm before adding those two rules.
Add a CSP last, in `_headers`, which can also carry `frame-ancestors` (a meta tag cannot). Alternatively use Astro's `security.csp` (hash-based, since astro@6.0.0) and test Pagefind wasm/worker against it.

**Static asset requests are free and unlimited** (Workers billing). Only Worker invocations bill, and there are none.

**Previews:**
- **Version URLs:** `wrangler versions upload --preview-alias pr-123` → `pr-123-sculk-docs.<subdomain>.workers.dev`. Assets belong to the version ("Your Worker code, configuration, and static assets belong to a Worker version"). This needs `preview_urls: true` and a `workers.dev` subdomain. Previews are public by default, so protect them with Cloudflare Access if needed. The alias must be lowercase/digits/dashes, ≤ 63 characters with the worker name. Recommended for PRs: a GitHub job builds, uploads with alias `pr-${{ github.event.number }}`, and comments the URL.
- **Workers Previews** (`wrangler preview`, wrangler ≥ 4.135): per-branch isolated environments, `X-Robots-Tag: noindex` on workers.dev, 100 previews on Free. Whether **assets-only** Workers are covered is not stated in the docs, so treat it as UNVERIFIED and try it after Version URLs work.

**CI changes to `docs.yml`:**
- `fetch-depth: 0` (git dates).
- A second checkout of `main` for v5 content.
- `bun install --frozen-lockfile`, then `bun run build` (Node runtime), then `bun run test:e2e` (Playwright + axe against `astro preview`), then LHCI, then `wrangler deploy`.
- Decide the trigger branch (§11).

---

## 10. Accessibility and performance targets

### 10.1 WCAG 2.2 AA checklist for this site (w3.org/TR/WCAG22)

| SC | What it means here |
| --- | --- |
| 2.4.1 Bypass blocks | First focusable element: "Skip to content" → `#main` (visible on focus); optionally also "Skip to navigation" |
| 2.4.2 / 3.1.1 | Unique `<title>` "Page · Sculk Studio"; `<html lang="en">` |
| 1.3.1 / 4.1.2 | Landmarks: one `header`, `nav[aria-label=Docs]`, `main`, `nav[aria-label=On this page]`, `footer`. One `h1` per page. Tabs/combobox/dialog follow the APG patterns. `aria-current="page"` in the sidebar and breadcrumbs |
| 2.1.1 / 2.1.2 | Everything works by keyboard: sidebar `<details>` toggles, tabs with arrows, search with arrows/Enter/Esc, no traps (native `<dialog>` handles inert background) |
| 2.4.3 / 2.4.7 | Logical order. `:focus-visible` ring of 2 px solid accent + offset, ≥ 3:1 against both themes (1.4.11) |
| **2.4.11 Focus not obscured (new in 2.2)** | Sticky header: set `scroll-padding-top` and `scroll-margin-top` on headings and focusables so a focused element is never hidden under it |
| **2.5.8 Target size min (new in 2.2)** | ≥ 24×24 CSS px for sidebar links, TOC links, copy buttons, tab buttons, the theme toggle and badge links (or enough spacing) |
| **3.2.6 Consistent help (new, A)** | Support/Discord/GitHub links in the same place on every page (footer/header) |
| 1.4.3 / 1.4.11 | Text ≥ 4.5:1, UI and graphics ≥ 3:1, in **both** themes, including code tokens. EC's `minSyntaxHighlightingColorContrast` (5.5) auto-adjusts token colours. `#00e5ff` is a dark-theme-only accent (≈1.5:1 on white, as `sculk.css` already notes) |
| 1.4.4 / 1.4.10 / 1.4.12 | 200% zoom, reflow at 320 px (code blocks may scroll horizontally, which is 2-D content), and text-spacing overrides must not clip (no fixed heights on callouts/cards) |
| 1.4.13 | Tooltips (e.g. badge explanations) can be dismissed with Esc, are hoverable, and persist |
| 4.1.3 Status messages | Search result count and "Copied!" announced via `role="status"` |
| Scrollable code | Scrollable `<pre>` must be keyboard-focusable (`tabindex="0"`, axe `scrollable-region-focusable`). Verify what EC emits |
| 2.3.3 (AAA, cheap) | All motion behind `prefers-reduced-motion` |

Testing: run Playwright + `@axe-core/playwright` over every built page in both themes (`page.emulateMedia({ colorScheme })` plus the `data-theme` toggle).
Add scripted keyboard journeys: skip link, open search with ⌘K, pick a result, tabs with arrows, mobile drawer with Esc.
Add a manual NVDA + VoiceOver pass before launch. This machine has no Chrome (per the spike), so run it in CI or install Playwright's own Chromium on I: per the shared-PC rules.

### 10.2 Lighthouse 100 (all four categories) strategies

- **No render-blocking JS.** All own scripts are module scripts (deferred) except the ~300 B theme and tabs-restore inline scripts. Budget: ≤ 10 KB gz JS per docs page excluding search (today ≈ 32 KB gz with Starlight), and ≤ 20 KB gz CSS.
- **CSS:** one layered global stylesheet + EC CSS. `build.inlineStylesheets: 'auto'` inlines small ones.
- **Fonts:** preload only the body woff2, use metric-matched fallbacks (no CLS), and subset.
- **Search on intent:** about 93 KB of Pagefind assets never load on page view.
- **Images:** `astro:assets` `<Image>`/`<Picture formats={['avif','webp']}>`, `image.layout: 'constrained'` + `image.responsiveStyles: true` for automatic `srcset`/`sizes`, explicit dimensions, `loading="lazy"` below the fold and `fetchpriority="high"` on the hero only. Co-locate screenshots in `src/assets/`, because `public/` images are not optimised. Inline the logo SVG.
- **Prefetch:** `prefetch: { defaultStrategy: 'hover' }`, or `'viewport'` for sidebar neighbours. Measure first, since Drizzle uses `viewport` + `prefetchAll`.
- **SEO/BP:** meta description (already required by the schema), canonical, sitemap, `robots.txt`, OG tags (+ optional build-time OG images; MPL tools, see §2), no console errors, HTTPS, correct `lang`.
- **Enforce:** LHCI (`@lhci/cli` 0.16.1) with assertions at 1.0 for a11y/BP/SEO and ≥ 0.98 perf on home, a long guide, the search-open state and `/create`. Add bundle-size assertions on `dist/_astro/*.js`.

---

## 11. Migration plan (Starlight → custom shell)

**Effort estimate (v6 content):** the content itself needs **no edits** to build in phase 1. All change is in shell code. The codemod in phase 3 touches 18 import lines.

| Phase | Work | Content changes |
| --- | --- | --- |
| 0. Prep | Branch from `dev` (e.g. `feat/docs-shell`). Write **ADR-0031 "Custom Astro docs shell"** superseding ADR-0016 and update `DOCS_ARCHITECTURE.md` "Shell"/"Search" (they name Starlight's `Search` override and `starlight-versions`). Snapshot today's built HTML for 5 key pages as a visual/anchor reference | none |
| 1. Swap the engine | Remove `@astrojs/starlight`. Add `@astrojs/mdx`, `astro-expressive-code`, `@astrojs/markdown-satteri`, `@astrojs/sitemap`. Own `content.config.ts` (§3.2, `sidebar.order` kept). `[...slug].astro` + `DocsLayout`. Vite alias `'@astrojs/starlight/components' → src/components/mdx/index.ts` so imports still resolve. `index.mdx` → `pages/index.astro` (move its Aside/Card copy into the bespoke home) | none (home moves) |
| 2. Parity components + shell | `Aside`, `Steps`, `Card`, `CardGrid`, `Badge`, `Icon` (+ `Tabs`, `LinkCard`, `FileTree` for upcoming pages/v5). Header, sidebar, TOC, breadcrumbs, prev/next, edit link, last updated, theme, skip link, mobile nav, 404. Port `ModuleRoadmap.astro` (`Badge` import, `--sl-*` → own tokens). Rewrite `sculk.css` into layered tokens (palette carries over) | none |
| 3. Codemod | Delete the 18 `import { … } from '@astrojs/starlight/components'` lines (globals now). Remove the alias for v6 but keep it for the v5 collection | 18 lines |
| 4. Search | Pagefind integration + `SearchDialog`, filters `version`/`module`, code down-weight | none |
| 5. Gates + CI | Link checker over `dist`, axe, LHCI, file count, snippet-region gate, manifest gate. `docs.yml`: `fetch-depth: 0`, v5 checkout, PR previews | none |
| 6. Snippets | Add `// #region` to `examples/*` and convert pasted ` ```kotlin ` fences to `snippet=` page by page. A soft gate first (warn on fences > N lines without `snippet=` in guides), then hard | progressive |
| 7. v5 | `.versions/v5` checkout, `:::` directive plugin, `/v5/` routes, `VersionSwitch`, banner | none in v5 |
| 8. Sculk-specific | Manifest-driven sidebar + `CompatStrip`, selectors, playground islands, `/create`, exactly as `DOCS_ARCHITECTURE.md` describes (these pipelines are shell-agnostic by design) | `module:` frontmatter added per page |

**What can be kept by providing same-named components:** every `<Aside type title>`, `<Steps>`, `<Card title icon>`, `<CardGrid>`, plus `<Tabs syncKey>`/`<TabItem label>`, `<Badge text variant>`, `<LinkCard>`, `<FileTree>`, which v5 and future pages may use.
Also kept: all fence syntax (EC stays), heading anchors (same slugger), URLs (same routes and trailing slash), and `sidebar.order`.

**What must change:** `template: splash`/`hero` (home becomes a page), Starlight-only frontmatter on v5 (ignored via passthrough), `--sl-*` CSS variables in `ModuleRoadmap`, the import lines (optional thanks to the alias, but cleaner removed), and Card icon names (mapped in `Icon`).

**Owner decisions needed:**
1. **Which branch deploys production before 6.0 ships?** `main` (v5) deploys today. Options:
   - (a) keep production on `main`/Starlight until 6.0 and serve the new site only as a Version URL/preview from `dev`;
   - (b) deploy `dev` to production now, with v6 marked "in development" and v5 (pulled from `main`) at `/v5/`, prominently linked as the released version.
   **Recommendation: (a) until the new shell passes the gates, then (b).** It is reversible: `wrangler rollback`.
2. ADR wording, since ADR-0016 explicitly chose Starlight over a hand-built shell, citing the spike's "weeks of shell work" estimate. The custom shell accepts that cost in exchange for the bespoke design.

---

## 12. Real open-source custom Astro docs sites (not Starlight)

| Site | Repo | Stack (read from repo, 2026-11) | Architecture worth copying |
| --- | --- | --- | --- |
| **Nimbus** (Cloudflare's Astro docs framework; nimbus-docs.com) | [cloudflare/nimbus](https://github.com/cloudflare/nimbus), MIT, 1.8k★, pre-1.0 (`@cloudflare/nimbus-docs` 0.20.0) | Astro 7.4, **Sätteri** (`@astrojs/markdown-satteri`), MDX 8.1, Shiki 4.5 + `@shikijs/transformers` (not EC), React 19, Tailwind 4.3, Pagefind 1.5, wrangler | Three tiers: an npm package with *mechanism* (`getSidebar`, `getPrevNext`, `getTOC`, `getBreadcrumbs`, `getEditUrl`, `getLastUpdated`, `getDocsStaticPaths`), *taste* (layouts/components) copied into the user's repo, and an opt-in registry. **MDX globals registry** `src/components.ts` passed via `<Content components>`, using Starlight's names. Versioned docs as collections `docs-<v>`. Build-time MDX validation as a content pass (because remark plugins no-op under Sätteri). `llms.txt`/Markdown twins. *Use as a reference for helpers. Do not adopt it: pre-1.0, React peer dependency, Tailwind-bound taste* |
| capnweb docs | [cloudflare/capnweb](https://github.com/cloudflare/capnweb) `packages/docs`, MIT | Astro 7.2 on Nimbus 0.14, Tailwind, Pagefind | A real consumer of Nimbus: custom `search/providers/pagefind.ts` |
| **Fastify** (fastify.dev) | [fastify/website](https://github.com/fastify/website), MIT | Astro 7.2, MDX 7, **unified processor** via `@astrojs/markdown-remark` (custom remark/rehype), Tailwind 4.3, `astro-pagefind` 2.0.1, Shiki dual themes | **Versioned docs as folders in one collection** (`generateId` keeps `v5.x` paths), `VersionSelect` + `VersionBanner`. **Hand-built ⌘K `<dialog>` on the Pagefind JS API, scoped to the page's version.** Docs pulled from the upstream repo by `scripts/fetch-docs.mjs` (same idea as our v5 pull). `DocsShell`, `TableOfContents`, `CodeTabs` components |
| **Chartist** docs | [chartist-js/chartist](https://github.com/chartist-js/chartist) `apps/docs`, MIT, 13k★ | Astro 7.4, MDX 8.1, Tailwind 4.3, Pagefind 1.5.2 via **own integration** | Pagefind **Node API integration**: indexes only `data-pagefind-body`, **code blocks weighted 0.4**, excludes archived `v1/`/`v0/`. Archived majors as static output under `public/v1`, `public/v0`, with redirect integrations. Examples live in `src/examples` and render as live examples (CodeMirror playground loaded on demand). `site`/`base` from env so the same build can sit under a sub-path |
| **Drizzle ORM** docs (orm.drizzle.team) | [drizzle-team/drizzle-orm-docs](https://github.com/drizzle-team/drizzle-orm-docs), **CC-BY-4.0** (attribution required; do not lift code without attribution), 620 MDX pages | Astro 4 (older), MDX, `rehype-slug` + `rehype-autolink-headings`, Shiki `css-variables` theme + `@shikijs/transformers`, React islands, Sass | **`_meta.json` per directory for sidebar order**, `DialectTabs` (synced tabs per database dialect, our Paper/Folia analogue), `llms.txt` endpoints, redirects as a Vite plugin, `build.format: 'file'` "due to CloudFlare Pages trailing slash problem" (a Pages-era issue; Workers `html_handling` solves it) |

Template-grade references (small, for patterns only): [shravanngoswamii/astro-docs](https://github.com/shravanngoswamii/astro-docs) (styles in cascade layers so user CSS wins; no licence file, so **do not copy**), [qwexvf/astro-docs-template](https://github.com/qwexvf/astro-docs-template) (Astro 6 + Tailwind 4 + Pagefind, MIT), [CloudCannon/stratus-astro-documentation-template](https://github.com/CloudCannon/stratus-astro-documentation-template) (MIT).

Common pattern across all of them: a `[...slug].astro` catch-all, sidebar data computed in TS from collections plus a per-directory or config ordering file, Pagefind on its JS API or Component UI, Shiki-family highlighting, synced tabs hand-written, and Tailwind for most (not required).

---

## 13. Proposed folder layout

```
docs/
├─ astro.config.ts            processor: satteri({ features:{directive:true}, mdastPlugins:[snippets, asides] }),
│                             integrations: [expressiveCode(), mdx(), sitemap(), react({include}), sculkDocs()]
├─ ec.config.mjs              themes (custom dark/light JSON), plugins, styleOverrides
├─ wrangler.jsonc
├─ package.json · bun.lock · tsconfig.json
├─ integrations/
│  ├─ sculk-docs.ts           composes the below
│  ├─ pagefind.ts             Node API indexing, weights, dev serving
│  ├─ git-dates.ts            one `git log` → virtual:sculk/git-dates
│  ├─ gates.ts                links, manifest↔pages, module frontmatter, file count
│  └─ redirects.ts            src/redirects.ts → dist/_redirects
├─ scripts/
│  └─ pull-v5.ts              local fallback for the CI checkout of main
├─ public/                    _headers, favicon.svg, robots.txt
├─ src/
│  ├─ content.config.ts
│  ├─ navigation.ts           section order/labels
│  ├─ redirects.ts
│  ├─ i18n/ui.ts
│  ├─ content/docs/**         v6 MDX (unchanged paths)
│  ├─ assets/                 logos, screenshots (optimised)
│  ├─ layouts/                BaseLayout.astro · DocsLayout.astro · HomeLayout.astro
│  ├─ pages/                  index.astro · [...slug].astro · v5/[...slug].astro · create.astro · 404.astro · llms.txt.ts?
│  ├─ components/
│  │  ├─ mdx/                 Aside Tabs TabItem PackageTabs PlatformTabs Steps Card CardGrid LinkCard Badge
│  │  │                       SupportBadge StabilityBadge FileTree Snippet Platform Since CompatStrip index.ts
│  │  ├─ shell/               Header Sidebar MobileNav Toc Breadcrumbs PrevNext EditLink LastUpdated ThemeToggle
│  │  │                       VersionSwitch PlatformSelect McVersionSelect SearchDialog SkipLink Banner Footer Icon
│  │  ├─ home/                bespoke landing sections
│  │  ├─ playgrounds/         *.tsx React islands
│  │  └─ ModuleRoadmap.astro
│  ├─ lib/                    nav.ts · toc.ts · manifest.ts · versions.ts · stores.ts · repo-tables.ts · href.ts
│  │  ├─ snippets/            extract.ts · satteri-snippets.ts
│  │  └─ markdown/            satteri-asides.ts (::: → Aside)
│  ├─ scripts/                tabs.ts · toc-spy.ts · search.ts · theme.ts · selectors.ts · sidebar-scroll.ts
│  └─ styles/                 index.css (@layer order) · tokens.css · reset.css · base.css · layout.css · prose.css · code.css
├─ .versions/                 (gitignored) v5 content pulled from main
└─ tests/
   ├─ unit/                   bun test: extract, nav, toc, versions
   └─ e2e/                    Playwright + axe: a11y sweep, keyboard journeys, visual snapshots
```

---

## 14. Risks

| Risk | Likelihood / impact | Mitigation |
| --- | --- | --- |
| **Sätteri is young** (satteri 0.11.x, repo created 2026-03; `@astrojs/markdown-satteri` 0.4.x) and is the Astro 7 default. Its plugin API differs from remark, and remark plugins silently no-op | Medium / high | Pin exact versions. Keep plugin logic pure. Fallback to `unified()` (EC supports both, as Fastify shows). Snapshot-test rendered HTML of key pages |
| EC's Sätteri path is recent (EC 0.44.x) | Low-medium / medium | Pin, render a "kitchen sink" page in CI, fall back to unified |
| **Shell maintenance cost**: everything Starlight did for free (a11y details, mobile nav, RTL, i18n, print CSS) is now ours. The spike estimated "weeks" | High / medium | Scope v1 shell tightly, port MIT Starlight pieces (Tabs restore, FileTree) with notice, use Nimbus helpers as a reference, and enforce with the axe/keyboard/LHCI gates |
| Hand-built search combobox fails a11y | Medium / medium | Pagefind Component UI as a drop-in fallback (§5.3) |
| Wrong "last updated" dates from shallow CI clone | High if unaddressed / low | `fetch-depth: 0` |
| `compressHTML: 'jsx'` eats spaces in prose; Rust compiler rejects sloppy HTML | Medium / low | `compressHTML: true`; fix markup as errors surface |
| v5 content in Starlight dialect | Low / low | Same-named globals, Vite alias for the import path, `:::` plugin, passthrough schema |
| Deploy branch mismatch (prod from `main`, new site on `dev`) | Certain / high if ignored | Owner decision §11. Version-URL previews until cutover |
| Snippet edits don't hot-reload in dev | Medium / DX only | Vite invalidation plugin (§4.2) |
| CSP + Pagefind wasm/worker | Medium / medium when CSP is enabled | Ship CSP last, test `'wasm-unsafe-eval'` + `worker-src` |
| Cross-document view transitions unsupported in some browsers | Certain / none | Progressive enhancement only |
| Workers asset limits with more majors + Pagefind fragments | Low / medium | File-count gate, `--max-fragments`, Paid plan raises to 100k |
| `@astrojs/check` incompatible with TypeScript 7 | Certain if bumped / low | Pin `typescript@6.0.3` until `@astrojs/check` widens its peer range |
| Licences | Low | Everything recommended is MIT/ISC/Apache-2.0/OFL-1.1. **Flag:** `satori`/`@resvg/resvg-js` (MPL-2.0, build-time only, if OG images are generated), `axe-core` (MPL-2.0, test-only), Drizzle docs (CC-BY-4.0, don't copy), `shravanngoswamii/astro-docs` (no licence, don't copy). Keep the MIT notice for any Starlight code ported |

---

## Sources

- Astro v7 upgrade guide: https://docs.astro.build/en/guides/upgrade-to/v7/
- Astro Markdown (processors, Sätteri/unified, heading IDs): https://docs.astro.build/en/guides/markdown-content/
- Astro content collections: https://docs.astro.build/en/guides/content-collections/
- Astro configuration reference (markdown.*, redirects, trailingSlash, build.format, prefetch, compressHTML, security.csp, image.*): https://docs.astro.build/en/reference/configuration-reference/
- Astro syntax highlighting: https://docs.astro.build/en/guides/syntax-highlighting/
- Astro fonts guide and provider reference: https://docs.astro.build/en/guides/fonts/ · https://docs.astro.build/en/reference/font-provider-reference/
- Astro view transitions: https://docs.astro.build/en/guides/view-transitions/
- Sätteri: https://github.com/bruits/satteri · https://satteri.bruits.org/docs/ (plus the installed `satteri/dist/*.d.ts`, `@astrojs/mdx/dist/index.js`, `astro-expressive-code/dist/index.js` in `docs/node_modules`)
- Expressive Code: https://expressive-code.com/installation/ · https://expressive-code.com/reference/configuration/ · https://expressive-code.com/key-features/text-markers/
- Shiki languages / tm-grammars: https://shiki.style/languages · https://github.com/shikijs/textmate-grammars-themes/blob/main/packages/tm-grammars/README.md · Kotlin grammar https://github.com/fwcd/vscode-kotlin
- Pagefind: https://pagefind.app/docs/api/ · https://pagefind.app/docs/search-config/ · https://pagefind.app/docs/ranking/ · https://pagefind.app/docs/filtering/ · https://pagefind.app/docs/js-api-filtering/ · https://pagefind.app/docs/node-api/ · https://pagefind.app/llms-component-ui.txt · https://github.com/Pagefind/pagefind/releases
- astro-pagefind: https://github.com/shishkin/astro-pagefind
- Cloudflare Workers static assets: https://developers.cloudflare.com/workers/static-assets/ · headers https://developers.cloudflare.com/workers/static-assets/headers/ · redirects https://developers.cloudflare.com/workers/static-assets/redirects/ · HTML handling https://developers.cloudflare.com/workers/static-assets/routing/advanced/html-handling/ · billing https://developers.cloudflare.com/workers/static-assets/billing-and-limitations/ · limits https://developers.cloudflare.com/workers/platform/limits/ · Wrangler config https://developers.cloudflare.com/workers/wrangler/configuration/ · Version URLs https://developers.cloudflare.com/workers/configuration/previews/ · Workers Previews https://developers.cloudflare.com/workers/previews/
- WCAG 2.2: https://www.w3.org/TR/WCAG22/ · APG tabs https://www.w3.org/WAI/ARIA/apg/patterns/tabs/ · combobox https://www.w3.org/WAI/ARIA/apg/patterns/combobox/ · dialog https://www.w3.org/WAI/ARIA/apg/patterns/dialog-modal/
- Starlight (MIT) Tabs/FileTree source: `docs/node_modules/@astrojs/starlight/dist/user-components/` · https://github.com/withastro/starlight
- Reference sites: https://github.com/cloudflare/nimbus · https://github.com/cloudflare/capnweb · https://github.com/fastify/website · https://github.com/chartist-js/chartist · https://github.com/drizzle-team/drizzle-orm-docs · https://github.com/shravanngoswamii/astro-docs · https://github.com/qwexvf/astro-docs-template · https://github.com/CloudCannon/stratus-astro-documentation-template
- npm registry (versions/licences, 2026-11-17): https://www.npmjs.com/ via `npm view`
- Repo inputs: `DOCS_ARCHITECTURE.md`, `docs/adr/0016-docs-platform.md`, `docs/research/g-docs-spike.md`, `docs/astro.config.mjs`, `docs/package.json`, `docs/src/**`, `docs/wrangler.toml`, `.github/workflows/docs.yml`, `git show main:docs/...`
