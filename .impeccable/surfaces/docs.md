---
version: 1
slug: "docs"
primary_target: "docs"
related_targets: []
---

# Sculk docs site

Scope: the whole documentation site at docs.sculk.studio (home, guides, concepts, reference,
changelog). Mode: Read. Audience and job: see docs/PRODUCT.md. Build path: code-led. Chosen
sketch: .impeccable/mocks/decision/atlas.html (the owner selected it on 2026-11-17).

## Direction contract

THESIS: The API as charted territory. Every page answers "does this work on my server, and on which
thread?" with a map-legend rail, an ownership map and support marks; it refuses the Starlight/PaperMC
silhouette (stock header, filled-pill sidebar, coloured callout boxes, centred hero over icon cards).

OWN-WORLD: OLED true-black ground (#000) with a 16px chunk grid at very low contrast in the frame
only; near-black sheet for reading; bone text #e6e2d6; terracotta #c9824a means "you are here" and
nothing else; teal #3e8f8a and violet #9b8fd6 mean regions (player/region vs global). Archivo (wide,
tight) for display, Atkinson Hyperlegible Next for reading, JetBrains Mono for code and numbers.
Support and stability are marks first (filled, half, hollow squares), colour second. No glow.

STORY: A Kotlin plugin developer finds an API, sees at once where it runs and on which platforms and
versions it holds, copies a snippet that compiles, and trusts Sculk because the docs never overclaim.

FIRST VIEWPORT: masthead with chunk-grid mark, search (Ctrl K) and the Platform and Version
selectors; legend rail left with the current page pinned; the reading sheet with breadcrumb, wide
H1, lede, version/module meta and page actions; the right margin with the "where it runs" chunk map,
the support legend and the on-this-page list. On the home page the same frame carries a module atlas:
every module as a legend row with stability and per-platform marks.

FORM: Minecraft filled maps and the map-art palette, candidate 3 of 7 on the ordered list.
seed: 24ef659d885c8af1
Raises kept: states as marks (cutting bench); one Platform control re-marks the whole page (Miura
sheet); tabular data-density numerals (datamatics); colour rationed by role (Bauhaus); sections cut
as blocks with clear gaps (cloud quarry); each API shows the version it arrived in (sticker case).
Signature interaction: choosing a platform re-marks every support square on the page in one pass.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
