# ADR-0031: A custom documentation site on Astro, without Starlight

**Status**: Accepted · **Date**: 2026-11-17 · **Supersedes**: ADR-0016

## Context

ADR-0016 chose Astro Starlight from a spike that weighed shell features against a hand-built
TanStack Start site. The owner had asked for a custom site and rejects the resulting Starlight site:
recoloured, it looks generic. Research since confirms why. PaperMC, Folia, Velocity, Adventure,
Modrinth's docs and PacketEvents all run stock Starlight, so a recoloured Starlight site reads as
PaperMC's docs with another accent, and colour cannot change its silhouette
(`docs/research/h-docs-design.md`, `docs/research/i-minecraft-docs-landscape.md`). No Minecraft
docs site states per API which platforms and versions it holds for; Sculk records that data and
should show it. Astro 7 makes a custom shell practical: content collections, MDX on the Sätteri
processor, Expressive Code and Pagefind work without Starlight (`docs/research/j-custom-astro-stack.md`).

## Decision

- **Custom Astro 7, no Starlight.** Our own layout, navigation, home page and components; MDX
  content in a `glob()` collection with Starlight's frontmatter shape, so pages and URLs carry over.
  Same-named `Aside`, `Steps`, `Card`, `CardGrid` and `Badge` components replace Starlight's.
- **Code blocks with Expressive Code**, set explicitly on the Sätteri processor. **Search with
  Pagefind** behind our own Ctrl K dialog. **Plain CSS** with cascade layers, custom properties and
  self-hosted OFL fonts. Static output on Cloudflare Workers, as before.
- **Design direction "The Atlas"**, chosen by the owner: the API as charted territory from
  Minecraft's maps. A legend rail for navigation, a chunk-grid frame, a "where it runs" ownership map
  and support marks per page, on an OLED true-black ground. Pages declare where their API runs and
  how each platform supports it; one Platform control re-marks every page. The contract lives in
  `.impeccable/surfaces/docs.md`, the product brief in `docs/PRODUCT.md`.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep Starlight, override its components | Its silhouette is the category's; the owner rejected it, and overriding every component leaves only the content pipeline, which Astro gives directly. |
| TanStack Start + React | A larger client floor and a release-candidate framework; nothing in the requirements needs a React app. |
| TanStack Start + Fumadocs UI | Fastest to polish, but another recognisable template look. |

## Consequences

We own navigation, layout and accessibility that Starlight handled: keyboard navigation, focus,
contrast and reduced motion are tested with the site. The Sätteri processor is young (0.x); if it
blocks a feature, `@astrojs/markdown-remark` is the fallback. Versioned v5 content keeps its own
build until it is pulled into the same shell.
