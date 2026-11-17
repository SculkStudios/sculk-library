# What makes developer docs feel premium: evidence for the Sculk Studio docs redesign

Research date 2026-11-17. Screenshots are in `C:\Users\Daisy\AppData\Local\Temp\claude\docs-research\screens\` and were taken at 1440×1000 in dark mode, plus three mobile shots at 390×844. Unless a value is marked as an estimate, it is a **computed style measured in a real Chromium** through playwright-core, not something guessed from screenshots. Colours have been converted to sRGB hex. A trailing `@0.9` means alpha.

---

## 0. Diagnosis first: why the current site reads as a template

Screens: `sculk-current.png` (landing), `sculk-current-page.png` (`/commands/overview/`).

Computed values on https://docs.sculk.studio/commands/overview/:

| Property | Sculk now | PaperMC docs | Astro docs |
|---|---|---|---|
| h1 / h2 size, weight | 42px / 35px, 600 | 42px / 35px, 600 | 42px / 35px, 600 |
| body | 16px / 28px | 16px / 28px | 16px / 28px |
| content column | 720px at x=336 | 720px at x=336 | 720px at x=336 |
| header | 64px, search box centred-left, "Auto" theme select | identical | identical |
| active sidebar item | filled pill `#003d47` bg, `#00e5ff` text | filled pill `#60a5fa` bg | filled pill `#b3c7ff` bg |
| font | system stack (Segoe UI on Windows) | Poppins | system stack |
| `pre` font | generic `monospace` at 14.4px | generic `monospace` | generic `monospace` |

**The most important finding is that PaperMC's docs (https://docs.papermc.io) are also Starlight**, with the same geometry pixel for pixel (`papermc-docs.png`). Sculk's audience is Paper plugin developers, and they spend their day on PaperMC's docs. A recoloured Starlight therefore looks like PaperMC with the colours changed, which is the worst possible look for a premium framework built *on top of* Paper. The new site has to read as a different object at a glance, not as a reskin.

Other specific problems:
- **Blue-tinted blacks plus pure cyan.** The page is `#050a0f`, the chrome is `#0b1e2d` and the accent is `#00e5ff`. Navy-black with saturated cyan is the most common "AI dark mode" palette. Body text `#7aa3b5` is blue-grey (7.3:1, so legible), but because the whole page is one blue hue it has no tonal depth. None of the premium sites tint their neutrals this strongly. Linear's `#08090a` and Supabase's `#131413` are almost neutral.
- **The landing page is a stock pattern**: a centred cyan H1, a pill CTA and a ghost link above a 2×2 grid of bordered cards, each with an icon chip. That is anti-pattern #1 below.
- **Code is undressed**: there's no filename bar, no language label, no visible copy affordance, a 1px border at 1px radius, a generic monospace font and 4-space indent at 14.4px. On the premium sites, code is the most-crafted object on the page.
- **There is no typographic voice**: one system sans, h1/h2/h3 at a flat 600 weight, and no tracking at large sizes. (Side note: the deployed docs are still 5.x content, and the commands sample uses `player!!`, which the repo's own rules forbid.)

---

## 1. Per-site notes

### 1.1 Stripe Docs — https://docs.stripe.com
Screens: `stripe-home.png`, `stripe-page.png`, `stripe-quickstart.png`, `stripe-api.png`, `stripe-callout.png`

- **Landing page**: no hero art. The left edge has a faint dotted-grid texture that fades out, the H1 just says "Documentation", and the rest is a three-column *text-link* directory (Payments / Revenue / Developers) followed by "Try it out", a live interactive CLI runner with a list on the left and a terminal on the right. A small skewed test card graphic carries the "4242" test number. The craft comes from **real product affordances instead of illustrations**.
- **Navigation**: a two-tier header. Tier one holds the logo, a centred search box with a `/` hint, "Ask AI", and Create account / Sign in. Tier two is a row of product tabs (Get started, Payments, Revenue…) with an underlined active tab. The sidebar is product-scoped with UPPERCASE group labels (ONLINE PAYMENTS) and a collapse button (`⇤`). The locale and language pickers sit at the foot of the sidebar.
- **Typography**: system stack (`-apple-system, Segoe UI…`). H1 is 32/40 at weight 700 in `#1a1f36`, the subtitle is a large 24px regular line, and body is 16/26 in `#3c4257`. Code in light docs is Menlo; the API reference uses Source Code Pro. Stripe proves that the font isn't the premium part: the **hierarchy (bold H1 over a large regular deck)** and the colour discipline are.
- **Colour**: indigo links `#5469d4`, ink `#1a1f36`, inline code on `#f4f7fa` with a `#d4dee9` border and 6px radius. The API reference is dark: page `#1b1e25`, header `#14171d`, hairlines `#2b3039`, links `#9289fe`.
- **Code**: the quickstart (`stripe-quickstart.png`) is the reference implementation of a **two-pane walkthrough**. Prose steps are on the left and a sticky code pane on the right, with file tabs (`server.rb`, `checkout.html`…), a live preview of the result above the code, and a Download button. Frontend and backend **language pickers are pill toggles above the page** and persist across pages. In the API reference, a dark request block (with a `cURL` selector, open, copy and AI icons) sits above a separate RESPONSE block.
- **API reference**: a three-column layout of nav, params and sticky code. Each param name is bold monospace with its type in small grey text, separated by hairlines, with "Show child parameters" as a pill. There's a version selector (`2026-10-28.endive`) in the header.
- **Page details**: breadcrumbs; a row of page actions under the title (Ask about this page · Copy for LLM · View as Markdown); the ON THIS PAGE rail with "Optional" tags in grey beside optional sections; **numbered, collapsible step sections** ("1  Set up your endpoint" with a chevron). Callouts are a thin left rule with a bold label ("Not a developer?"), with no coloured box.
- **Why it feels premium**: interactive, account-aware content (sign in and the samples contain your test keys), content-shaped components (integration-effort dots, preview panes), and restraint everywhere else.

### 1.2 Vercel Docs — https://vercel.com/docs
Screens: `vercel-docs.png`, `m-vercel.png`, `search-vercel.png`

- **Landing/overview pages are composed like product pages.** "Run server-side code with Vercel Functions" is a **56px / 600 headline at -3.36px tracking (-0.06em) in 56px leading**, set in four tight lines, with a tabbed code sample to its right ("Handle a request / Stream a response / Run after a response") and two plain rectangular buttons. Below that is an inline architecture diagram (Edge Network → Firewall → Routing → Cache → Functions) drawn in hairlines and dashed boxes.
- **Navigation**: the header has the logo, a slash, "Docs", and then Build ▾ and Learn ▾. The sidebar has its own search (`⌘K`) and a drill-down "‹ Functions" back header, so the **sidebar is a stack that replaces itself** instead of a 200-item tree. Top-level items are bold 14px with indented children. The active item is a `#1f1f1f` fill at 6px radius. The right rail has a **framework selector** (Next.js (/app)) above "On this page".
- **Typography**: Geist Sans and Geist Mono (Vercel's own fonts). Body is 16/27.2 in `#ededed`, H3 is 20/26 at 600 with -0.4px, and inline code is Geist Mono 14.4px on `#1a1a1a` with a 1px `#ffffff@0.14` border and 4px radius.
- **Colour**: pure `#000000` page, `#0a0a0a` code, `#1f1f1f` / `#2e2e2e` hairlines. There is effectively **no brand colour in the chrome**. Colour only appears in syntax highlighting.
- **Code**: a filename bar with a language glyph (`TS`), the copy button top-right, and the block radiused only at the bottom so it docks to the bar.
- **Search** (`search-vercel.png`): a centred modal on a dimmed page, with a big bare input ("config"), a book icon per result, a title line and an excerpt with **orange match highlights**, covering guides and REST endpoints in one list.
- **Mobile**: the headline still dominates. The copy-page and TOC buttons become a compact toolbar, and code tabs scroll horizontally.

### 1.3 Next.js Docs — https://nextjs.org/docs
Screens: `nextjs-docs.png`, `nextjs-callout.png`, `nextjs-footer.png`

- **Navigation**: two **stacked selectors at the top of the sidebar**, "Using App Router ⇕" (router) and "Latest v16.4.6 ⇕" (version), each with an icon tile and a two-line label. This is the cleanest model for platform switching (directly relevant to Paper/Folia/Velocity). The search pill sits top-right with `⌘K`, next to Ask AI.
- **Typography**: Geist. H1 is 40/48 at **weight 450** with -2px tracking, H2 is 24/32 at 450 with -0.96px, and body is 16/28 at `#ededed@0.9`. A **medium-light heading weight with tight tracking** is a big part of why it reads as calm and expensive, not shouty.
- **Links**: underline offset 4px, 1px thickness, underline colour `#8f8f8f` (dimmer than the text).
- **Callout** ("Good to know"): a neutral grey filled box with an ⓘ glyph and a bold label. There is **no coloured border and no tint**.
- **Package-manager tabs**: pnpm / npm / yarn / bun as small text tabs, with the active one on a filled chip.
- **Footer**: "Last updated October 2, 2026" in small grey text, then a hairline, then prev/next as **plain right- and left-aligned text** ("Next / Project Structure ›") with no cards. "Give feedback" and "Edit this page on GitHub" sit under the TOC in the right rail.

### 1.4 Linear — https://linear.app/docs, https://linear.app/changelog
Screens: `linear-docs.png`, `m-linear.png`, `linear-changelog.png`, `linear-changelog-fixes.png`

- **Docs**: page `#08090a`; body text `#d0d6e0` at 15/24 with -0.165px tracking; H1 32/36 at **weight 590** (a variable-font in-between weight) with -0.704px; H2 24/31.9 at 590 with -0.288px. The font is **Inter Variable**, but the custom 510/590 weights, the negative tracking at every size and the 15px body make it read as Linear's own. Links are white text with a **grey underline `#62666d`** at a 2.6px offset. Hairlines are `#23252a`.
- **Sidebar**: groups with sentence-case grey labels (Intake, Planning…) and **monochrome product icons** beside each item. A footer block in the sidebar switches between Docs / Developers / Learn / Contact support. The active item is 14px at 510 on an 8px-radius fill.
- **Images**: real product UI screenshots placed on a blue-to-teal gradient plate with 12px radius. The gradient is used *as a frame for the product*, never as decoration on its own.
- **Changelog**: a big "Now" H1, filter tabs (All / Changelog / Product launches…), and a search pill with a bell (subscribe). Each entry has a **date in a left gutter on a vertical timeline with a small orange dot**, the title at about 32px bold with tight tracking, a centred hero visual, and prose. Below each entry, **collapsible rows for Fixes / Improvements / API / MCP server**. This is the strongest changelog pattern found.
- **Why it feels premium**: tight type, nearly no colour, real product imagery, and a chrome-to-content ratio that leaves the text alone.

### 1.5 Tailwind CSS Docs — https://tailwindcss.com/docs
Screens: `tailwind-docs.png`, `tailwind-utility.png`, `m-tailwind.png`, `search-tailwind.png`

- **Signature details** (all of them cheap, and all of them ownable):
  - **Diagonal-hatched gutters**: the content column is framed by 40px bands of fine diagonal stripes, so the page looks like a drafted blueprint.
  - **Monospace uppercase eyebrows** with wide tracking above the H1 (`INSTALLATION`, `SPACING`) and as sidebar group labels (`GETTING STARTED`, `CORE CONCEPTS`), set in IBM Plex Mono.
  - **Numbered steps as small mono badges in bracket-like boxes** (`01`, `02`, `03`), with each step a two-column row of prose left and code right.
  - The active sidebar item is a **1px white left rule** on a continuous 1px grey rail, with no pill.
- **Typography**: Inter for UI and body (the docs are **14px/28px**: small size, very generous leading), H1 30/36 at 500 with -0.75px. Plex Mono for code at 14/20, Ubuntu Mono for some tables.
- **Colour**: page `#030712` (gray-950), body text `#d1d5dc`. Links are white, semibold, with a **sky `#00bcff` underline** at a 3px offset. The brand colour only lives in the underline, and that single detail does a lot of work.
- **Code**: a terminal/filename bar ("Terminal", `vite.config.ts`) with copy, a frame of `#ffffff@0.05` fill with a 1px inset `#ffffff@0.10` ring at 12px radius, an inner code surface at 8px radius, and **highlighted lines with a full-width tint and a sky-blue left bar**.
- **Reference pages**: a "Class / Styles" quick-reference table in mono (class in sky, CSS in violet), truncated behind a fade with a "SHOW MORE" mono pill.
- **Search**: Algolia DocSearch, grouped by section, with a `#` icon tile per result and the active result in solid brand blue.
- **Mobile**: a breadcrumb bar ("☰ Getting Started › Using Vite") replaces the sidebar, and the version chip stays in the header.

### 1.6 Bun Docs — https://bun.com/docs
Screens: `bun-docs.png`, `bun-home.png`

- **Platform**: Mintlify-based (Copy page ▾, the Ask AI floating button), but customised heavily enough that it doesn't read as Mintlify.
- **Typography is the identity**: H1 is **Archivo at weight 800, 51px with a 47px line-height (tighter than the font size)** and -1px tracking. That's a heavy grotesque headline, much like Bun's marketing. H2 is 34px at 800. Body is the system UI font at 16.5/28.8, and code is **Martian Mono** at 13.5/20, a wide, distinctive monospace.
- **Colour**: page `#0d0a0c` (a **slightly warm/plum black**, not neutral), text `#eaeae8` (warm off-white), hairlines `#28282b`, and pink brand accents on breadcrumbs, the active nav bar and card icons.
- **Navigation**: a two-tier header. Docs/Guides/Reference/Blog with a wide search, then a **product tab row with icons** (Runtime, Package Manager, Bundler, Test Runner…). The active sidebar item has a pink left bar.
- **Landing**: it does use four bordered cards, but each card ends with a **real command** ("Get started with `bun run` →") in mono. The cards hold product entry points, not slogans.
- **Right rail**: the ON THIS PAGE label is mono, uppercase and tracked, and API members are listed as inline-code chips (`server.stop()`).

### 1.7 Svelte Docs — https://svelte.dev/docs
Screen: `svelte-docs.png`

- **The only top-tier docs that use a serif for reading.** H1 is **DM Serif Display at 54px**, sidebar section headers (Introduction, Runes, Template syntax) are also DM Serif Display at 24px, and **body is a serif at 18/27**. The fonts loaded are EB Garamond, DM Serif Display, Fira Sans (UI), Fira Mono (code) and Atkinson Hyperlegible. The header has an **"A" toggle that switches the reading font** between serif and sans, which is an unusual and memorable reader-respecting control.
- **Colour**: page `#1c1e22` (a warm-ish charcoal, not black), body `#cacbce`, code surface `#1f2228` with a `#303541` border at 4px radius, and inline code on `#2e333e`. The accent is Svelte orange `#f96743`, used only for the active nav item and the current breadcrumb.
- **Page details**: a tiny uppercase breadcrumb (`SVELTE • RUNES`) above the H1, and a "SEE ALSO" row linking tutorial → reactivity → state. The sidebar is dense, with mono-ish API names (`$state`, `{#each …}`) as items.
- **Why it matters for Sculk**: it proves an editorial, bookish voice can work in developer docs and instantly separates you from every Inter/Geist site.

### 1.8 Supabase Docs — https://supabase.com/docs
Screens: `supabase-home.png`, `supabase-page.png`, `search-supabase.png`

- **Landing**: the left side has an icon and "Supabase Documentation"; the right side has a tabbed **Agent Prompt / CLI** box with copy. Below that, "Connect a framework" with a label column on the left and a 3-column grid of framework tiles, each a logo square plus a name, with no descriptions.
- **Typography**: **Manrope at 600 for headings** (H1 34px, H2 22px), **Inter at 450 for body**, and Source Code Pro for code. The body weight of 450 (not 400) gives dark-mode text more substance.
- **Colour**: page `#131413` (**green-tinted near-black**: the brand tints the neutrals very subtly), body `#bcbdbc`, muted `#989a99`, and active nav in brand green `#4acd8b`. Code panel `#181a19` with a 1px `#f2f2f2@0.07` border, 8px radius and a small drop shadow. Inline code is `#dfdfdf@0.03` with a `#f2f2f2@0.07` border.
- **Sidebar**: a product header ("Database") with an icon, and **mono uppercase group labels** (FUNDAMENTALS, WORKING WITH YOUR DATABASE (BASICS)).
- **Page details**: breadcrumbs with **"Updated Nov 3, 2026" right-aligned on the same line**; an "Is this helpful? ✕ ✓" widget at the top of the right rail; AI TOOLS (Copy as Markdown, Ask ChatGPT/Claude); and a deeply nested ON THIS PAGE. Callouts are a **danger box in a dark red tint `#2a1313`-ish (estimate) with a red icon tile** and a neutral info box with a grey icon tile. Both have rounded 6–8px corners and a full border.
- **Search**: a modal with results shown as **breadcrumb paths** ("CLI Reference > supabase config pull") plus an excerpt, matches in brand green, and a footer with "Ask Supabase AI" and keyboard hints (Navigate ↑↓, Open ↵).

### 1.9 Raycast Developer Docs — https://developers.raycast.com
Screen: `raycast-docs.png`

- **This one is a cautionary example.** It runs on **GitBook** ("Powered by GitBook" in the sidebar), with Inter, a `#1d1d1d` background and the Raycast red `#ff6363` accent. The Introduction hero is a **magenta-to-violet gradient plate with a macOS-window code mockup** (traffic lights, `raycast-api.tsx`). Raycast's product is premium, but these docs read as a themed hosted platform, which is exactly what Sculk is trying to escape.
- **The useful parts**: a glassy header (`#1c1c1c@0.88` with `blur(16px)`), inline code in IBM Plex Mono on `#2d2b2b`, and links in red with a **40% alpha pink underline** (`#edacaa@0.40`).

### 1.10 Clerk Docs — https://clerk.com/docs
Screen: `clerk-docs.png`

- **SDK selector at the top of the sidebar** ("Select your SDK: Next.js ⇕"), with a Guides/Reference switch below it. The right rail has an **"Available in other SDKs" icon grid** (20 framework logos, current one framed). This is the best pattern found for showing that a page exists for Paper, Folia and Velocity.
- **Typography**: **Suisse Int'l** (licensed) for text with a `geistNumbers` fallback layered in front so figures are tabular, and **Söhne Mono** for code. H1 is 32/40 at 600 with -0.48px. Body is 15/28 in `#9394a1`, which is low contrast (6.2:1) and too dim.
- **Colour**: page `#131316`, code frame `#212126` → `#2f3037`, and a purple accent used only on the "Copy prompt" button.
- **Components**: a "Copy prompt" block that collapses behind a fade with "Expand prompt", a **Note** callout as a bordered box with a ⓘ and a bold title, and collapsible "Step-by-step setup instructions" disclosure rows.

### 1.11 Resend Docs — https://resend.com/docs
Screen: `resend-docs.png`

- **Inset content panel.** The chrome (sidebar and header) is `#09090b`, and the article sits on a **separate rounded panel (14px radius) a hair lighter**, like a sheet laid on a desk. Prisma does the same thing. It's a strong, cheap way to say "this is a designed surface".
- **Typography**: Inter, H1 36/40 at 600 with -0.9px, body 18/28 in `#9e9e9e`. Code is **Paper Mono** (Resend's own). The CSS also loads Latin Modern and CMU Typewriter, a LaTeX-style flourish used in places.
- **Navigation**: Documentation / Guides / API Reference as top tabs with icons. The sidebar search sits next to an AI-sparkle square button. The active item is a 12px-radius `#ebebeb@0.10` fill. A **theme switcher (system/light/dark icons) is pinned at the sidebar foot**. An "Ask a question… Ctrl+I" bar floats at the bottom of the content.

### 1.12 Prisma Docs — https://www.prisma.io/docs
Screen: `prisma-docs.png`

- **Inset rounded content panel** on `#0f0f0f` chrome; the header is `#0f0f0f@0.85` with `blur(8px)` and a `#2a2a2a` hairline.
- **Typography**: **Sora at 500 for headings** (H1 36px) with no negative tracking, Inter for body at 18/28 in `#a5a5a6`, and **Mona Sans Mono** for code.
- **Links are white text with a 1.5px teal underline `#01d7e4`** at a 3.5px offset. Prisma already owns "teal underline on near-black", which is another reason Sculk shouldn't lead with plain cyan.
- **Sidebar**: an "All docs ‹" back link, a product header (ORM), an **"ORM version: Latest ▾" selector and a "Select database ▾" selector**, small grey sentence-case group labels, and a system-status dot ("All Systems Operational") at the foot.
- **Page actions**: Copy as Markdown / Open in ChatGPT ▾. The announcement bar is a thin gradient strip with a bordered CTA.

### 1.13 Astro Docs — https://docs.astro.build (Starlight)
Screens: `astro-docs.png`, `astro-start.png`

What Astro kept from stock Starlight: the header geometry, the 42/35/29 heading scale, the 720px column, the filled-pill active item (`#b3c7ff` bg / `#17264f` text), the right-rail TOC, the system font stack and the Expressive Code frames.

What Astro changed:
1. **A wordmark plus a tinted "DOCS" label** in the header.
2. **A four-way section switcher** (Tutorial / Guide / Reference / Ecosystem) as a boxed segmented list at the top of the sidebar.
3. A **landing page with a contributor-avatar wall** on a faint grid, plus a radial purple glow, an "Install Astro 🚀" pill and an announcement strip.
4. **Saturated callouts** (the Note is a solid indigo `#1e1f6b`-ish (estimate) block with a thick left bar).
5. A sponsor/course card in the right rail.
6. Language and theme selects.

The verdict: even Astro's own docs still read as Starlight. Customising Starlight's tokens and adding components doesn't change the *silhouette*, and the silhouette is what people recognise.

### 1.14 Tokio — https://tokio.rs/tokio/tutorial
Screen: `tokio-docs.png`

- **A light, Bulma-era layout**: a black header bar, a grey `#f5f5f5` sidebar slab, Roboto at 18px body, light grey code blocks and a tracked `TOKIO` sidebar label. It's honest and very readable, but it isn't premium. It's included as the baseline of "clean but dated": no code frames, no filename metadata (the filename is a floating grey chip *above* the block), a pink `#c83895` inline-code colour, and the TOC in pale grey.
- **What's good**: the tutorial is a *narrative* (a mini-Redis built chapter by chapter), and the sidebar shows the whole tutorial as one ordered spine.

### 1.15 Deno Docs — https://docs.deno.com
Screen: `deno-docs.png`

- **Brutalist and confident**: the active top tab ("Runtime") and the **active sidebar item are solid neon-mint blocks `#70ffaf` with dark text, running full-bleed to the column edge**. The current TOC entry is a mint block too. Inline code like `Deno.serve` is mint text on grey. Filename labels are **pills that sit on the code block's top border** (`server.ts`, `>_` for a shell). Inter 16/24 on `#14161a`, hairlines `#383a3e`.
- **The lesson**: one loud accent used *structurally* (as a block), not as glow, reads as intentional and owned.

### 1.16 PaperMC Docs — https://docs.papermc.io (domain neighbour)
Screen: `papermc-docs.png`. Starlight with Poppins, page `#111111` and active pill `#60a5fa`. It has a project switcher ("Paper ⇕") at the top of the sidebar and an "Experimental" badge inline in nav items, and its "Experimental" callout is a maroon block with a pink left bar. **This is the look Sculk must not resemble.**

---

## 2. Cross-site synthesis: 22 patterns that separate premium from template

**Type and text**

1. **Negative tracking that scales with size.** Every premium site tracks display type tight: Vercel -0.06em at 56px, Next.js -0.05em at 40px, Linear -0.022em at 32px, Bun -0.02em at 51px. Starlight ships `normal`. This is the single cheapest upgrade.
2. **In-between heading weights.** Next.js uses 450, Linear 590, Supabase body 450. Variable fonts let headings be *quietly* heavy. Flat 600 everywhere reads as a default.
3. **A real font pairing with a job split.** Display versus text versus mono each do something different: Svelte (serif display + sans UI + Fira Mono), Supabase (Manrope + Inter), Prisma (Sora + Inter + Mona Sans Mono), Bun (Archivo 800 + system + Martian Mono), Vercel (its own Geist family). Nobody premium uses a generic `monospace` for code.
4. **Small body with big leading, or big body with a narrow measure.** Tailwind uses 14/28, Linear 15/24 and Prisma/Resend/Raycast 18/28, with columns of 576–800px. The ratio is deliberate, not 16/28 by default.
5. **A dimmed underline instead of coloured link text.** Linear, Next.js, Vercel, Tailwind and Prisma all keep link text near-white and put the colour (or the grey) in the underline, with a 2–4px offset. Coloured link text is the template default.
6. **Mono uppercase micro-labels** for eyebrows, sidebar groups and "ON THIS PAGE" (Tailwind, Supabase, Bun, Stripe). They make the page feel instrumented and engineered.

**Colour**

7. **Neutrals are not tinted the brand hue, or only barely.** Supabase's `#131413` is a whisper of green and Bun's `#0d0a0c` a whisper of plum. Sculk's current `#050a0f` / `#0b1e2d` is a *strong* navy, and that strength is what makes it look generated.
8. **One accent with a single job.** Vercel has no accent in the chrome at all. Svelte's orange marks only the current location, Tailwind's sky is only in underlines and highlights, and Deno's mint is only the active state. The accent should mean "you are here / do this", never "decoration".
9. **There is no glow anywhere.** None of the 15 sites uses a coloured text-shadow or glow on links, cards or buttons. Light is suggested by *surfaces* (Resend's and Prisma's inset panel, Tailwind's 5% white frames), not by bloom.
10. **Three or four surface steps, separated by 5–14% white hairlines**: `#ffffff@0.05` fill + `@0.10` ring (Tailwind) or `#f2f2f2@0.07` borders (Supabase), never solid saturated borders.

**Layout and navigation**

11. **The sidebar starts with a context switcher, not a tree**: Next.js router + version, Clerk SDK, Prisma version + database, PaperMC project, Astro section tabs. For Sculk, **Platform (Paper / Folia / Canvas / Velocity) plus Version (6.x / 5.x)** belongs here.
12. **A two-tier header or product tabs** for large docs (Stripe, Bun), or a sidebar that drills down (Vercel "‹ Functions") instead of one giant expandable tree.
13. **Code beside prose on getting-started pages**: Stripe's quickstart, Tailwind's numbered rows, Vercel's hero with tabbed code. The two-pane step layout is the most "premium-coded" structure seen.
14. **Overview pages composed like product pages** (Vercel: huge headline, code, diagram), while reference pages stay quiet. Templates give every page the same skeleton.
15. **The right rail does real work**: feedback, page actions, edit link, an "available in other SDKs" grid, last-updated. Not just a TOC.
16. **Search as a modal with breadcrumb paths, highlighted matches and keyboard hints** (Supabase, Vercel). Each result shows *where* it lives.

**Code and components**

17. **Code frames carry metadata**: filename with a language glyph, copy, sometimes download/open, file tabs, and a preview pane (Stripe). The frame docks to the code (radius only on the outer corners).
18. **Line highlights as a full-width tint plus a left bar**, and package-manager tabs (pnpm/npm/yarn/bun) that persist as a site-wide preference.
19. **Calm callouts**: Next.js uses neutral grey, Stripe a thin left rule with a bold lead, Clerk a bordered box. Colour is reserved for real danger (Supabase red). Big saturated tinted boxes for every Note (Starlight/Astro/PaperMC) read as template.
20. **Numbered steps as typographic objects**: Tailwind's `01` mono badges, Stripe's large numerals with collapsible sections.
21. **Changelog as a timeline**: the date in a gutter, a dot on a vertical line, a big entry title, one hero visual, and collapsible Fixes/Improvements/API groups (Linear).
22. **Page actions for the AI era are now table stakes**: Copy page / Copy as Markdown / Open in Claude/ChatGPT (Stripe, Next.js, Vercel, Supabase, Prisma, Bun, Resend). Put them under the H1 as a quiet text row (Stripe style), not as a loud button.

**Things to add that the screenshots can't show but every site has**: a `prefers-reduced-motion`-aware 120–200ms fade/slide on the TOC indicator and the search modal, active-item transitions without bounce, and no scroll-jacking.

---

## 3. Anti-patterns: what reads as "generic AI site"

1. **A centred hero (gradient H1 or accent-coloured H1), a pill CTA plus a ghost link, then a 2×2 or 3×1 grid of bordered cards each with an icon chip and a slogan.** This is exactly the current Sculk landing page. Premium sites lead with *product*: a runnable sample, a framework grid, a live terminal, real screenshots.
2. **A saturated accent on blue-tinted black**, especially cyan/teal on navy. It's also Prisma's territory.
3. **Glow**: coloured `box-shadow`/`text-shadow` on links, cards and code. The current CSS defines `--sl-glow-link: 0 0 8px rgba(0,229,255,.35)` and `--sl-shadow-*` cyan glows.
4. **Gradient-blob or macOS-window mockup heroes** (Raycast/GitBook): traffic-light dots around a 4-line snippet on a magenta plate.
5. **Emoji or generic-icon feature cards** ("🚀 Fast", "🔒 Safe"), including the stock Lucide icons inside small tinted squares.
6. **One sans for everything at 400/600 with no tracking**, whether system stack or default Inter. (Inter itself isn't the problem; Linear and Tailwind use it. The problem is Inter *with no decisions*.)
7. **Saturated full-tint callouts for every Note/Tip/Caution**, each in a different hue. The "muddy brown warning banner" is this anti-pattern.
8. **A stock Starlight/Docusaurus/GitBook/Mintlify silhouette**: the same header heights, the same 720px column at x=336, the same "On this page" heading, and the same filled-pill active item.
9. **Bento grids of feature boxes with gradient borders**, and "sparkle" AI iconography sprinkled around.
10. **Dim, low-contrast body text** used to look moody (Clerk's 6.2:1 is the floor; go lower and it reads cheap rather than calm).
11. **Generic `monospace`** at 14.4px with 4-space indents in code.
12. **Decorative illustration of the brand theme** (pixel creepers, sculk textures as backgrounds) instead of using the theme in *structure*. Note also that Mojang's textures, logo and the Minecraft font are Mojang IP and can't be used in a commercial product's branding. Draw your own motifs.

---

## 4. Three design directions for Sculk

All fonts below are **SIL OFL** and can be self-hosted via Fontsource, so they're safe to bundle in a commercial product. The palette ideas come from the deep dark itself, not "cyan": **deepslate** (stone neutrals), **sculk** (deep teal-black), the **sculk sensor** (the luminous teal tendrils), the **calibrated sensor** (amethyst), **bone / sculk catalyst** (off-white) and **candles** (amber light in the ancient city). Contrast ratios were computed (WCAG 2.x) against the stated background.

A brand-specific idea that works in all three: **map the framework's stability markers to the colour system.** `@SculkStable` takes the teal signal, `@SculkExperimental` takes amethyst (the calibrated sensor) and `@SculkInternal` takes stone grey. Badges, sidebar markers and API entries then all speak the codebase's own vocabulary. No competitor can copy that meaningfully.

### Direction A: "Deepslate Codex" (editorial, archival, reading-first)

- **Mood**: an excavated library in the ancient city. Quiet, scholarly and confident. Svelte's serif voice crossed with Stripe's restraint.
- **Type**: display **Newsreader** (variable, optical size 72 for H1, weight 500, -0.02em; italic for eyebrows and pull lines). Text **Atkinson Hyperlegible Next** at 17/28 (a distinct shape, built for accessibility). Code **JetBrains Mono** at 13.5/22 with ligatures off. JetBrains Mono is Kotlin's native mono, since Kotlin developers live in IntelliJ.
- **Palette (dark)**: page `#121313` (deepslate, neutral) · surface `#191b1b` · raised `#202323` · hairline `#2b2f2f` · text `#e6e1d3` (bone, 14.3:1) · text-2 `#a9a596` (7.5:1) · text-3 `#7d796f` (4.3:1, meta only) · sculk teal `#3fc8b8` (9.0:1) for marks, `#6fdccd` for link underlines · candle amber `#d9a65a` (8.5:1) for warnings · danger `#d4675a`.
- **Palette (light, a real first-class mode)**: bone paper `#f1ece0` · ink `#1b1d1d` (14.4:1) · text-2 `#4a4a44` (7.6:1) · teal `#0b6e66` (5.2:1) · amber `#8a5a12` (5.0:1). The light mode, warm paper with ink and teal, becomes a signature in its own right.
- **Layout**: no hero cards. The landing page is a **table of contents set like a book's front matter**: a large Newsreader title, a two-sentence thesis, then a runnable Kotlin sample (`command("kit") { … }`) set large, then a "Chapters" list (Commands, Menus, HUD, Config…) as numbered lines with one-line descriptions and hairline separators. Content measure is 680px. Section openers get a small italic eyebrow ("Chapter 3 · Text").
- **Sidebar**: a Platform ⇕ / Version ⇕ pair at the top. Groups are labelled in **small caps Newsreader**; items are Atkinson 14px; the active item is bone text with a **2px teal left rule** (no pill). Stability dots sit after item names.
- **Code**: a `#0e0f0f` panel with a 1px `#2b2f2f` ring and 8px radius. The header bar carries the filename in mono, a `kt` glyph and copy. Syntax colours are muted earth tones (bone, teal, amber, sage `#9db58a`, comment `#6b6a63` italic). Highlighted lines get a `#3fc8b8@0.08` tint with a 2px teal bar.
- **Callouts**: Stripe-style. A thin left rule in the semantic colour, a small-caps label ("NOTE", "THREADING", "FOLIA") and no fill. A custom **"Execution context"** callout with a region/global/async glyph turns `THREADING.md` into a visual system.
- **Why it's ownable**: no plugin-ecosystem docs (PaperMC, Fabric, Spigot) and almost no developer docs use a serif voice, and a bone-paper light mode is unheard of in Minecraft tooling. It signals craft and permanence, which suits a framework sold on correctness.
- **Risk**: serif body can feel slow for API reference. The fix is to switch reference pages to a denser Atkinson-only layout.

### Direction B: "Sensor Grid" (technical instrument, blueprint precision)

- **Mood**: a calibrated instrument panel. Tailwind's drafted precision and Bun's typographic punch, tuned to the sculk sensor's "it heard you" moment.
- **Type**: **Mona Sans** (variable weight *and width*). Headlines at wdth 115 (wide), weight 700, -0.025em; text at wdth 100, weight 450, 15/26. Labels in **Departure Mono**: an OFL pixel-grid monospace, used *only* for 11px uppercase labels, eyebrows and step numerals. It nods to Minecraft's pixel heritage without using Mojang's font. Code in **JetBrains Mono**.
- **Palette**: page `#0b0c0c` · panel `#101212` · grid lines `#1c2120` · hairline `#252b2a` · text `#e8ecea` (16.4:1) · text-2 `#9aa5a2` (7.7:1) · text-3 `#6b7774` (4.2:1, labels only) · **signal teal `#19e3c8`** (12.0:1) · signal-dim `#0d4a43` (selection and highlight fill) · **amethyst `#a68bf0`** (7.1:1) for Experimental · amber `#f0b44c` for warnings.
- **Layout**: a visible 4px baseline and 8px column grid in `#1c2120` hairlines framing the content column (Tailwind's hatched gutters, but as a **sensor-ripple pattern**: concentric hairline arcs at very low contrast in the margins). Getting-started pages are **Stripe/Tailwind two-pane steps** with `01 / 02 / 03` Departure Mono numerals in bracket boxes, prose left and code right. The landing page is a **live "frequency" index**: one big wide-set headline, a tabbed code sample (Command / Menu / HUD / Config) beside it, then a dense list of modules rendered like an instrument readout (module name, stability badge, platform support ticks for Paper/Folia/Canvas/Velocity as a matrix).
- **Sidebar**: Departure Mono uppercase group labels (`CORE`, `TEXT`, `HUD`); the active item has a 1px signal-teal left bar on a continuous `#252b2a` rail. **Signature micro-interaction**: on navigation, the active marker emits a single 400ms ring ripple, a sculk sensor's vibration, disabled under `prefers-reduced-motion`. That is the *only* animated glow on the site.
- **Code**: a `#ffffff@0.04` frame with a `#ffffff@0.09` inset ring at 12px radius and an inner `#0e1010` surface at 8px radius. The header is a Departure Mono filename plus a platform tag (`PAPER`, `FOLIA`). Diff markers come as `+`/`-` gutters with teal/amber tints; highlighted lines get a teal bar. Gradle tabs: `Kotlin DSL / Groovy`.
- **Search**: a modal with breadcrumb paths in mono, matches underlined in teal, and the API symbols (`command()`, `HudEngine`) as their own result type showing their signature.
- **Why it's ownable**: the sensor ripple, the pixel-mono labels and the platform-support matrix are all derived from Minecraft *mechanics* and from Sculk's actual multi-platform promise, not from texture art. It looks engineered, which is what a framework buyer is paying for.
- **Risk**: it could tip into "terminal cosplay". Keep Departure Mono under 12px, labels only.

### Direction C: "Warden" (atmospheric product docs, darkness as material)

- **Mood**: the deep dark at night. Almost total darkness, one pulse of light, and composition doing the work. Linear and Vercel restraint with Resend/Prisma's inset sheet.
- **Type**: display **Schibsted Grotesk** at 600, -0.035em, H1 48/50 on overview pages, 34/40 on guides. Text **IBM Plex Sans** at 450, 16/27 (engineering heritage, and less ubiquitous than Inter). Code **JetBrains Mono**.
- **Palette**: chrome `#070909` · **content sheet `#0f1313`** (an inset rounded 16px panel with a 1px `#1d2424` edge, Resend/Prisma style) · raised `#151a1a` · text `#eef2f1` (16.6:1) · text-2 `#aab6b4` (9.0:1) · text-3 `#6f7d7a` (4.4:1) · **soul-teal `#5ee6d6`** (12.3:1) used for exactly three things: focus rings, the active nav marker and the single primary button (ink `#04201d` on teal, 11.2:1) · deep sculk `#0a3b3e` for text selection and highlighted lines · bone `#d8cfb8` for strings in code.
- **Layout**: overview pages are composed like Vercel's, with a huge tight headline, a tabbed code sample at its side and a hairline diagram (for example the lifecycle bootstrap → enable → region tasks → disable, or "where your code runs: global / region / async" as a three-lane diagram). Guides use a calm single column at 700px. The **changelog follows Linear's timeline**, with date gutter, dot, big title and collapsible "Fixes / API / Breaking" groups, and ABI-dump-aware "Public API changed" tags.
- **Sidebar**: Clerk-style **Platform selector** ("Paper 26.3 ⇕") and **Version selector** at the top, a Guides / Reference / Changelog segmented switch, sentence-case grey group labels, and monochrome line icons per group. The active item is a 6px-radius `#151a1a` fill with a **2px soul-teal tick at the left that breathes once (opacity 1 → 0.6 → 1, 2.4s) like the Warden's heartbeat**. It's the only moving thing on the page and is off under reduced motion. The right rail has page actions (Copy page · Open in Claude · View Markdown), an "Available on: Paper · Folia · Velocity" platform grid (Clerk pattern), feedback and "Edit on GitHub".
- **Code**: Vercel-docked frames, with a filename bar on `#0b0e0e`, code on `#0a0d0d` with a 1px `#1d2424` ring, and bottom-only radius. The syntax theme is dark stone: keywords `#7fd6cc`, strings bone `#d8cfb8`, numbers amber `#e3b062`, functions `#c9d4d2`, comments `#5d6b69` italic. A copy button appears on hover with a 120ms fade.
- **Callouts**: Next.js-style neutral fill (`#151a1a`) with a glyph and bold label. The only exception is **Danger**, which gets a 1px `#7a2a24` border. There's a special **"Runs on: region thread"** context chip inline at the top of world-touching API pages.
- **Why it's ownable**: it borrows the *experience* of the deep dark (darkness you navigate by one light and a heartbeat) rather than its colour. The inset sheet plus the single breathing marker is a memorable silhouette, and it's nothing like Starlight or PaperMC.
- **Risk**: closest to the Linear/Vercel family. It needs the platform matrix, the heartbeat and the execution-context chips to stay distinctly Sculk.

### Recommendation
**B ("Sensor Grid")** is the strongest fit. It is the most clearly *not* Starlight or PaperMC, its signature elements (sensor ripple, pixel-mono labels, platform matrix, stability colours) come from Sculk's real product claims, and it suits a developer audience that values precision. Borrow two elements from C: the **inset content sheet** and the **Linear-style changelog**. Keep A's bone-paper light mode as the light theme if a light mode is wanted at all.

Whichever direction is chosen, **leave the stock Starlight chrome behind** (header, sidebar, TOC, page frame). Starlight can stay as the content engine with fully overridden components, or move to plain Astro with Expressive Code. Recolouring alone cannot change the silhouette, as Astro's own docs show.

---

## 5. Screenshots still needed (deferred: owner's CPU was busy)

1. Svelte docs callout or aside, and the svelte.dev/docs landing page.
2. A Tailwind code block with diff markers, plus the Tailwind docs on mobile with the nav open.
3. Linear developer docs (https://linear.app/developers) to see Linear's code-block treatment.
4. Stripe API reference on mobile (390px) and Stripe docs dark mode, if it exists.
5. Hover and focus states: copy button, sidebar hover, TOC scroll-spy transitions on Vercel, Linear and Tailwind. A short screen recording would be better than stills.
6. The Clerk API reference page and the Resend API reference (endpoint layout).
7. Bun docs on mobile, to see how the product tab row collapses.

## 6. Sources
Stripe https://docs.stripe.com · https://docs.stripe.com/payments/accept-a-payment · https://docs.stripe.com/checkout/quickstart · https://docs.stripe.com/api/payment_intents/create · https://docs.stripe.com/webhooks
Vercel https://vercel.com/docs/functions · Next.js https://nextjs.org/docs/app/getting-started/installation · https://nextjs.org/docs/app/getting-started/layouts-and-pages
Linear https://linear.app/docs/conceptual-model · https://linear.app/changelog
Tailwind https://tailwindcss.com/docs/installation/using-vite · https://tailwindcss.com/docs/padding
Bun https://bun.com/docs · https://bun.com/docs/runtime/http/server
Svelte https://svelte.dev/docs/svelte/$state
Supabase https://supabase.com/docs · https://supabase.com/docs/guides/database/postgres/row-level-security
Raycast https://developers.raycast.com · Clerk https://clerk.com/docs/nextjs/getting-started/quickstart · Resend https://resend.com/docs/introduction
Prisma https://www.prisma.io/docs/orm/prisma-schema/overview · Astro https://docs.astro.build/en/basics/astro-components/ · https://docs.astro.build/en/getting-started/
Tokio https://tokio.rs/tokio/tutorial/spawning · Deno https://docs.deno.com/runtime/fundamentals/http_server/ · PaperMC https://docs.papermc.io/paper/dev/getting-started/paper-plugins/
Current Sculk https://docs.sculk.studio/ · https://docs.sculk.studio/commands/overview/
