---
version: 1
slug: "docs"
primary_target: "docs"
related_targets: []
---

# Sculk docs site

Scope: the whole documentation site at docs.sculk.studio (home, guides, concepts, reference,
roadmap). Mode: Read. Audience and job: see docs/PRODUCT.md. Build path: code-led. Reference set
chosen by the owner: the Vercel, Next.js, Linear and Bun docs. Design system: docs/DESIGN.md.

## Direction contract

THESIS: A quiet monochrome reference. Every page answers "does this work on my server, and on which
thread?" in the same places: support marks, a "Where it runs" note and one Platform control that
re-marks the whole site. Everything else stays white and grey so those answers are the only
colour on the page.

OWN-WORLD: OLED true black (#000) as the one ground for masthead, rail, column and margin; no hue on
any region or panel. Contained objects (code, cards, asides, the search dialog) sit on neutral
near-black fills behind 1px hairlines. Plain white text (#ffffff headings, #ededed body, #a1a1a1 and
#8f8f8f secondary), never blue-tinted. Poppins for all text and headings; JetBrains Mono for code,
versions and keys. Catppuccin Mocha (the owner's editor theme) inside code blocks only, upright, at
a minimum syntax contrast of 7.5. Colour appears only as status: green full, yellow partial, red
none, on round support marks and the matching aside icons. No shadows except the search dialog.

STORY: A Kotlin plugin developer finds an API, sees at once where it runs and on which platforms and
versions it holds, copies a snippet that compiles, and trusts Sculk because the docs never overclaim.

FIRST VIEWPORT: a sticky masthead with the Sculk Studio mark and name, search (Ctrl K) and the
Platform and Version controls; the rail on the left in familiar feature categories ("Setup", "Core
concepts", "Commands and events", "Text and configs", "Player UI", "Items and content", "Reference"),
each with a small line icon, the current page filled grey and scrolled into view; the column with
breadcrumb, H1 with a Copy page button, a one-sentence summary and the edit link at the foot; the margin with
"On this page", "Support" and "Where it runs". On the home page the column carries the title, lede,
one white "Install Sculk" button with two text links, a Kotlin sample, "What you can build" feature
cards and a one-line release status linking to the roadmap; the margin carries the release status
and "Start here". Rail, column and margin are centred as one composition.

FORM: the monochrome developer-docs family of Vercel, Next.js, Linear and Bun: one dark ground,
hairlines, white type, grey secondary text, gently rounded outlined controls.
Raises kept: states as marks whose shape carries the meaning; one Platform control re-marks the
whole site; tabular mono numerals for versions; each API shows the version it arrived in; section
headings carry # links; plain-word rail tags ("Partial", "Unsupported") for the chosen platform.
Signature interaction: choosing a platform re-tags the rail and highlights its support row on every
page in one pass.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
