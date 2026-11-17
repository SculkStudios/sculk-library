# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Custom Astro 7 without Starlight: own layout, navigation, home page and components; MDX content;
Expressive Code for code blocks; Pagefind search behind a custom ⌘K dialog; plain CSS with cascade
layers and self-hosted OFL fonts; static output on Cloudflare Workers. Decided by the owner on
2026-11-17 (the earlier Starlight choice, ADR-0016, is to be superseded). Engineering evidence:
`docs/research/j-custom-astro-stack.md`.

## Users

Public Minecraft plugin developers first: Kotlin developers, and Java developers curious about
Kotlin, writing plugins for Paper, Folia, Canvas and Velocity. They arrive either evaluating Sculk
against Paper's own docs and other libraries, or mid-task, looking up how an API behaves, which
thread it needs, and which server versions it works on. The owner's own premium plugins (Sculk
Studios / Daisy ecosystem) are users too.

## Product Purpose

Documentation for Sculk Studio 6, an MIT-licensed Kotlin platform for server plugins. It must win
evaluators over and then be the reference developers keep open daily. Success: Sculk becomes the
first-choice Kotlin library for Minecraft, and its docs are a reason why.

## Positioning

- One API across Paper 1.21.4 to 26.3, Folia, Canvas and Velocity, with every feature reporting
  whether it is native, emulated, degraded or unavailable on the running server.
- Work declares what it must own (global region, a region, an entity, or no game thread) and the
  platform decides the thread, so the same code is correct on Paper and Folia.
- Every feature is verified on real servers, including a real Minecraft client in the test suites.
- Every public declaration carries a stability marker: `@SculkStable`, `@SculkExperimental`,
  `@SculkInternal`.

No other Minecraft docs site states per-API platform and version support; Sculk already records
it, so the docs can show it (`docs/research/i-minecraft-docs-landscape.md`).

## Operating Context

Read on a desktop beside an IDE (IntelliJ) while writing Kotlin, often in long sessions; checked on
phones for quick lookups. Readers copy Gradle Kotlin DSL setup and code snippets, compare platform
behaviour, and read threading rules before touching the world. Snippets are imported from compiled
example plugins (`examples/`) and integration tests, so they always build.

## Capabilities and Constraints

- Content: Start, Concepts, Guides, Reference sections in MDX; ADRs and research reports; examples
  (`examples/economy`, `hud`, `shop`, `staff`, `network`).
- Versions: 6.x (this rewrite, branch `dev`) and 5.x (branch `main`) must both be readable.
- Domain: docs.sculk.studio.
- Not released yet: artifacts are built with `publishToMavenLocal`; the site must not claim Maven
  Central availability.
- Minecraft assets: Mojang's textures, logo and font must not be used in branding; an "NOT AN
  OFFICIAL MINECRAFT PRODUCT" disclaimer is required.

## Brand Commitments

Name: Sculk Studio, by Sculk Studios. No binding logo, colour or font: the current logo may change
(owner, 2026-11-17). The owner loves OLED-style dark sites: dark-first on a true-black ground
(owner, 2026-11-17). The site must look bespoke and premium; the owner rejected the recoloured
Starlight site as generic, so it must not resemble Starlight, PaperMC's docs or template sites.

## Evidence on Hand

Real code and behaviour only: the modules, example plugins, guides and the real-server suites
(results in CI). There are no testimonials, user counts, benchmarks for marketing, or customer
logos yet; do not invent them.

## Product Principles

1. Truth over polish: never claim a capability, version or platform the code and tests do not show.
2. Answer "does this work on my server, and on which thread?" wherever an API appears.
3. Every snippet compiles because it comes from code that is built and tested.
4. Respect the reader's time: fast pages, keyboard-first search, no decoration that slows reading.

## Accessibility & Inclusion

WCAG 2.2 AA: keyboard navigation for sidebar and search, visible focus, contrast checked, motion
only where `prefers-reduced-motion` allows it.
