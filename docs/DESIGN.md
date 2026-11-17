---
name: Sculk Studio Docs
description: The Kotlin platform for Paper, Folia, Canvas and Velocity plugins, documented as charted territory.
colors:
  ground: "#000000"
  grid: "#0c0e0f"
  grid-strong: "#15181a"
  sheet: "#0a0c0d"
  raised: "#13171a"
  raised-2: "#1a1f22"
  rule: "#262c30"
  rule-strong: "#343b40"
  text: "#f3f0e7"
  text-2: "#c6c8c0"
  text-3: "#9fa29a"
  here: "#f0954f"
  here-soft: "rgba(240, 149, 79, 0.14)"
  region: "#3e8f8a"
  region-ink: "#5fe0d2"
  region-fill: "rgba(62, 143, 138, 0.2)"
  global: "#9b8fd6"
  global-ink: "#c7bbff"
  danger: "#e08a7c"
  code-bg: "#0b0e10"
  code-bar: "#111518"
typography:
  display:
    fontFamily: "'Archivo Variable', system-ui, sans-serif"
    fontSize: "clamp(42px, 5vw, 64px)"
    fontWeight: 750
    lineHeight: 1.02
    letterSpacing: "-0.035em"
    fontVariation: "'wdth' 118"
  display-home:
    fontFamily: "'Archivo Variable', system-ui, sans-serif"
    fontSize: "clamp(34px, 3.6vw, 50px)"
    fontWeight: 750
    lineHeight: 1.02
    letterSpacing: "-0.035em"
    fontVariation: "'wdth' 118"
  headline:
    fontFamily: "'Archivo Variable', system-ui, sans-serif"
    fontSize: "30px"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 112"
  title:
    fontFamily: "'Archivo Variable', system-ui, sans-serif"
    fontSize: "19px"
    fontWeight: 650
    lineHeight: 1.3
    letterSpacing: "-0.01em"
    fontVariation: "'wdth' 108"
  lede:
    fontFamily: "'Atkinson Hyperlegible Next Variable', system-ui, sans-serif"
    fontSize: "18px"
    fontWeight: 400
    lineHeight: 1.6
  body:
    fontFamily: "'Atkinson Hyperlegible Next Variable', system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.75
  caption:
    fontFamily: "'Atkinson Hyperlegible Next Variable', system-ui, sans-serif"
    fontSize: "13.5px"
    fontWeight: 400
    lineHeight: 1.55
  label:
    fontFamily: "'Archivo Variable', system-ui, sans-serif"
    fontSize: "11.5px"
    fontWeight: 650
    letterSpacing: "0.14em"
    fontVariation: "'wdth' 125"
  code:
    fontFamily: "'JetBrains Mono Variable', ui-monospace, monospace"
    fontSize: "13.5px"
    fontWeight: 400
    lineHeight: 1.7
    fontFeature: "'tnum' 1"
rounded:
  none: "0px"
  focus: "3px"
  xs: "4px"
  sm: "5px"
  md: "6px"
  lg: "8px"
  xl: "10px"
spacing:
  chunk: "16px"
  region: "128px"
  mast: "64px"
  mast-phone: "56px"
  rail: "268px"
  column: "920px"
  margin: "300px"
  sheet-x: "clamp(28px, 4vw, 60px)"
  block: "22px"
  section: "56px"
components:
  button-primary:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
    rounded: "{rounded.md}"
    padding: "0 18px"
    height: "42px"
  button-primary-hover:
    backgroundColor: "#ffffff"
    textColor: "{colors.ground}"
  search-trigger:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.text-3}"
    rounded: "{rounded.md}"
    padding: "0 12px"
    height: "38px"
    width: "min(460px, calc(100% - 56px))"
  search-trigger-hover:
    textColor: "{colors.text-2}"
  select:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.text-2}"
    rounded: "{rounded.md}"
    padding: "0 30px 0 12px"
    height: "34px"
  legend-link:
    textColor: "{colors.text-2}"
    typography: "{typography.body}"
    rounded: "{rounded.sm}"
    padding: "6px 10px"
  legend-link-active:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
  card:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text-2}"
    rounded: "{rounded.lg}"
    padding: "16px 18px"
  aside:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
    rounded: "{rounded.lg}"
    padding: "14px 16px"
  mapcard:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.text-2}"
    rounded: "{rounded.lg}"
    padding: "14px"
  code-inline:
    backgroundColor: "{colors.raised-2}"
    textColor: "{colors.text}"
    rounded: "{rounded.xs}"
    padding: "1px 6px"
  support-mark:
    backgroundColor: "{colors.text-2}"
    rounded: "{rounded.none}"
    size: "9px"
  search-dialog:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.text}"
    rounded: "{rounded.xl}"
    width: "min(640px, calc(100vw - 32px))"
---

# Design System: Sculk Studio Docs

## Overview

**Creative North Star: "The Chunk Atlas"**

The site is a map of the API, drawn the way a Minecraft filled map is drawn: a true-black ground ruled into 16px chunks, a near-black reading sheet laid over it, and a legend that tells you where you are and what holds on your server. Every page is the same three-part chart, centred as one composition: the legend rail on the left, the reading sheet in the middle, the margin on the right carrying the ownership map, the support legend and the on-this-page list.

Meaning is carried by marks before colour. A filled square is supported or done, a half square is partial or in progress, a hollow square is none or planned, a dash is not applicable. Colour is rationed by role: terracotta says "you are here", teal and violet say "region" and "global", and nothing else is coloured. Density is that of a working reference read beside an IDE: tabular mono numerals, hairline rules, compact legend rows, a 70ch reading measure.

The system rejects the Starlight and PaperMC docs silhouette (stock header, filled-pill sidebar, coloured callout boxes, centred hero over icon cards) and recoloured template sites; the owner confirmed both.

**Key Characteristics:**
- OLED true-black ground with a low-contrast chunk grid in the frame only, never under reading text.
- One centred three-column chart: rail, sheet, margin.
- Support and status shown as square marks first; colour follows the mark.
- Wide, tight Archivo for display; Atkinson Hyperlegible Next for reading; JetBrains Mono for code and every number.
- Flat surfaces stepped by tone and hairline rules; no glow.

## Colors

A near-monochrome bone-on-black palette with three rationed signal hues, each bound to one meaning.

### Primary
- **Beacon Terracotta** (here): the "you are here" colour and nothing else. The current-page pin in the legend rail, the player pin on the ownership map, the filled chunk in the brand mark, the active on-this-page tick, the focus ring, the skip link, and the chosen platform's row in a support table (on Here Wash).
- **Here Wash** (here-soft): the 14% terracotta wash behind the support row for the platform the reader has chosen.

### Secondary
- **Region Teal** (region): the stroke of a lit region on the ownership map (the region that owns a player or entity).
- **Region Ink** (region-ink): text and labels for a lit region; the bright teal that clears contrast on black.
- **Region Fill** (region-fill): the 20% fill inside a lit region.

### Tertiary
- **Global Violet** (global): the stroke of the global region on the ownership map; dashed and dimmed when the page does not run there.
- **Global Ink** (global-ink): text and labels for the global region.

### Neutral
- **True Black Ground** (ground): the page, masthead, rail and margin. OLED black by owner preference.
- **Chunk Line** (grid) and **Region Line** (grid-strong): the 16px and 128px lines of the chunk grid on the ground.
- **Reading Sheet** (sheet): the centre column, search field, selects, dialog and map cards; one breath above black.
- **Raised Stone** (raised) and **Raised Stone 2** (raised-2): hover and current row in the rail, cards, asides, step counters; inline code.
- **Hairline** (rule) and **Strong Hairline** (rule-strong): every border, divider and table rule; strong for table heads, keycaps and hover borders.
- **Bone** (text): headings, body, the primary button fill.
- **Ash** (text-2): secondary text, rail links, captions, support rows.
- **Dust** (text-3): labels, placeholders, hollow-mark strokes, link underlines at rest.
- **Ember Warning** (danger): only the edge and glyph of a danger aside, and deleted lines in code.
- **Code Ground** (code-bg) and **Code Bar** (code-bar): code block body and its title bar.

### Named Rules
**The One Meaning Rule.** Terracotta means "you are here", teal means a region, violet means the global region. A hue never carries a second meaning, and nothing else on the page is coloured.

**The Mark Before Colour Rule.** Every state is legible in greyscale: the square's fill shape carries the meaning, colour only follows it.

**The Grid Stays In The Frame Rule.** The chunk grid shows on the ground (rail, margin, gutters) and never under the reading sheet, code or cards.

## Typography

**Display Font:** Archivo Variable, width axis opened (108% to 125%) (with system-ui)
**Body Font:** Atkinson Hyperlegible Next Variable (with system-ui)
**Label/Mono Font:** JetBrains Mono Variable (with ui-monospace) for code, keycaps, versions, module names and every count

**Character:** A wide, tightly tracked grotesque sets the chart titles like a cartouche; a legibility-first humanist sans carries long reading; a mono with tabular figures makes numbers line up like map coordinates.

### Hierarchy
- **Display** (750, clamp(42px, 5vw, 64px), 1.02, width 118%, -0.035em, balanced): the page title. The home page uses the smaller Display Home (clamp(34px, 3.6vw, 50px), max 21ch).
- **Headline** (700, 30px, 1.2, width 112%, -0.02em): prose H2, always opened by a hairline rule and 24px above it.
- **Title** (650, 19px, 1.3, width 108%): prose H3. Card titles (16px) and aside titles (14px) use the same face at 650.
- **Lede** (400, 18px, 1.6, max 64ch): the paragraph under the page title.
- **Body** (400, 17px, 1.75, max 70ch): all reading prose.
- **Caption** (400, 13.5px, 1.55): map captions, support rows, margin notes, search excerpts.
- **Label** (650, 11.5px, 0.14em, uppercase, width 125%, Dust): the legend group names in the rail and the section names in the margin, the map's legend headings.
- **Code** (400, 13.5px, 1.7): code blocks; inline code at 0.86em on Raised Stone 2.

### Named Rules
**The Numbers Are Coordinates Rule.** Every version, count and module identifier is set in JetBrains Mono with tabular figures, inline in the sentence.

**The Legend Label Rule.** Uppercase tracked Label type heads a group of legend rows or a margin section. It never sits above a page title or a prose heading.

## Layout

One chart, centred: rail (268px), column (up to 920px) and margin (300px) as a single grid, with the masthead (64px) using the same three tracks so search aligns with the sheet and the controls with the margin. Rail and margin are sticky under the masthead and scroll independently; the rail scrolls the current page into view.

The sheet pads clamp(28px, 4vw, 60px) at the sides, 40px top and 88px bottom; prose holds a 70ch measure while code blocks span the full column. Vertical rhythm: 14px between prose blocks, 22px around components (asides, steps, card grids), 56px above an H2, 34px above an H3, 72px before the previous/next foot.

Responsive: below 1240px the margin drops under the sheet as an auto-fit grid of 260px panels on the Reading Sheet. Below 860px the masthead shrinks to 56px, the wordmark and version hide, the rail becomes a full-height overlay behind a menu button, and the sheet pads 18px. Below 600px code blocks scroll sideways instead of wrapping, and atlas rows stack name above description.

## Elevation & Depth

Flat. Depth is three tones (True Black ground, Reading Sheet, Raised Stone) separated by 1px hairlines, plus the chunk grid that marks the ground as ground. The one shadow belongs to the search dialog, which sits over a 72% black backdrop. Code blocks have their frame shadow removed.

### Shadow Vocabulary
- **Dialog lift** (`box-shadow: 0 24px 60px rgba(0, 0, 0, 0.7)`): the search dialog only.

### Named Rules
**The No Glow Rule.** Nothing emits light: no coloured shadows, no blurred halos, no gradients on surfaces. A surface rises by one tone step and a hairline.

## Shapes

Squares are the world's atom. Support marks, the step counter, list bullets (6px squares), the atlas chunks and the brand mark are square-cornered. Containers soften only slightly: 4px for keycaps and inline code, 5px for rail rows, 6px for controls and the primary button, 8px for cards, asides, map cards, code blocks and the foot links, 10px for the search dialog. All borders are 1px hairlines; marks use a 1.5px stroke. Ownership regions on the map are drawn as chunk-aligned polygons, solid when lit and dashed when not.

## Components

### Buttons
Restrained; there is one filled button in the system.
- **Shape:** gently squared (6px), 42px tall.
- **Primary:** Bone fill, black text, 700 weight, 18px side padding. Used once per page at most (home "Install Sculk").
- **Hover / Focus:** fill brightens to white over 160ms; focus is the 2px terracotta ring offset 2px.
- **Secondary:** plain Ash text links with a Strong Hairline underline offset 5px; on hover the text goes Bone and the underline turns terracotta.

### Chips (Badge)
- **Style:** a support mark followed by Ash words at 12.5px, no fill, no border.
- **State:** filled mark for done or success, half for in progress or caution, hollow for everything else.

### Cards / Containers
- **Corner Style:** 8px.
- **Background:** Raised Stone for cards and asides; Reading Sheet for map cards.
- **Shadow Strategy:** none (see Elevation & Depth).
- **Border:** 1px Hairline; a danger aside alone takes a 55% Ember Warning edge.
- **Internal Padding:** 16px 18px for cards, 14px 16px for asides, 14px for map cards.
- **Asides:** neutral fill with a drawn 14px line glyph in the title (circle-i, star, triangle, crossed square); only danger is coloured.

### Inputs / Fields
- **Search trigger:** a 38px field on the Reading Sheet with a Hairline border and 6px radius, Dust placeholder, a mono Ctrl K keycap; hover strengthens the border and lifts the text to Ash.
- **Select (Platform):** a 34px labelled control on the Reading Sheet, the chosen value in Bone 600, a drawn chevron; focus-within shows the terracotta ring.
- **Search dialog:** 640px on the Reading Sheet with a Strong Hairline border, a 54px input row, results that highlight on Raised Stone, matches marked by a bold underline rather than a colour; it rises 8px over 180ms.

### Navigation
- **Legend rail:** Label group names, then 15px Ash rows with a 9px support mark for the chosen platform. Hover and current rows take Raised Stone and Bone text; the current row swaps its mark for a terracotta pin.
- **On this page:** 14px Ash links on a left hairline; the active section's hairline turns terracotta.
- **Breadcrumb:** 13px Dust with Strong Hairline slashes.
- **Previous / next:** two 8px hairline boxes with a small Dust label over a 16px Archivo title; hover takes Raised Stone.
- **Mobile:** the rail becomes a full overlay toggled from a 38px menu button.

### Support Mark (signature)
A 9px square with a 1.5px stroke: filled (full), diagonally half filled (partial), hollow (none), a 1.5px dash (not applicable). Each carries an accessible label. Choosing a platform in the masthead re-marks every rail mark in one pass with a 220ms scale pulse (skipped under reduced motion) and washes that platform's support row in Here Wash.

### Ownership Map (signature)
"Where it runs": a 262 by 150 chunk-grid figure in the margin. The player's region is a teal chunk polygon, the global region a violet rectangle, async work a dashed Dust box; unlit regions are drawn dashed and dim. A terracotta pin marks the player when the code runs on the player's thread. Labels are 10px mono; the caption leads with a Bone sentence.

### Atlas Chunks (signature)
On the home margin, every module is one square in a 12-column grid (3px gaps), filled, half or hollow by status, Bone on black.

### Code Blocks
Expressive Code framed in 8px Hairline boxes on Code Ground with a Code Bar title, no shadow, a Dust underline on the active tab, 13.5px mono at 1.7. Long lines wrap at word breaks keeping indent; under 600px they scroll. The syntax theme: Bone text, teal keywords, sand strings, violet types, white function names.

## Do's and Don'ts

### Do:
- **Do** keep the ground true black (#000000) and set reading on the Reading Sheet, one tone above it.
- **Do** show every support, status or stability state as a filled, half, hollow or dash square before adding any colour.
- **Do** reserve terracotta for "you are here": the current page, the chosen platform, the player's position, keyboard focus.
- **Do** set every version, count and module name in JetBrains Mono with tabular figures.
- **Do** separate surfaces with 1px hairlines and one tone step, and open every H2 with a hairline rule.
- **Do** keep rail, sheet and margin as one centred chart, collapsing the margin under the sheet below 1240px.
- **Do** draw icons as inline line SVG at a 1.4px stroke in the current text colour.

### Don't:
- **Don't** reproduce the Starlight or PaperMC docs silhouette: stock header, filled-pill sidebar, coloured callout boxes, a centred hero over icon cards.
- **Don't** colour an aside by type; only danger takes a coloured edge.
- **Don't** give teal, violet or terracotta a second meaning, or introduce a fourth signal hue.
- **Don't** add glow, coloured shadows or surface gradients; the search dialog's dark lift is the only shadow.
- **Don't** run the chunk grid under reading text, code or cards.
- **Don't** put uppercase tracked labels above a page title or prose heading as an eyebrow.
- **Don't** use Mojang's textures, logo or font anywhere in the identity.
