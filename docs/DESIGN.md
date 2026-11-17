---
name: Sculk Studio Docs
description: Documentation for the Kotlin platform for Paper, Folia, Canvas and Velocity plugins, in white and grey on true black.
colors:
  ground: "#000000"
  panel: "#0a0a0a"
  code-bg: "#121212"
  raised: "#141414"
  current: "#1a1a1a"
  rule: "#1f1f1f"
  rule-strong: "#2e2e2e"
  heading: "#ffffff"
  text: "#ededed"
  text-2: "#a1a1a1"
  text-3: "#8f8f8f"
  good: "#a6e3a1"
  partial: "#f9e2af"
  bad: "#f38ba8"
typography:
  display:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "clamp(32px, 3.2vw, 42px)"
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: "-0.025em"
  display-home:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "clamp(32px, 3.2vw, 44px)"
    fontWeight: 600
    lineHeight: 1.12
    letterSpacing: "-0.025em"
  headline:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "24px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.02em"
  title:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "18px"
    fontWeight: 600
    lineHeight: 1.3
    letterSpacing: "-0.01em"
  lede:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "17px"
    fontWeight: 400
    lineHeight: 1.7
  body:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.75
  nav:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "14.5px"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "14px"
    fontWeight: 600
  small:
    fontFamily: "'Poppins', system-ui, sans-serif"
    fontSize: "13px"
    fontWeight: 400
  code:
    fontFamily: "'JetBrains Mono Variable', ui-monospace, monospace"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.7
rounded:
  xs: "2px"
  sm: "4px"
  md: "6px"
  lg: "8px"
  xl: "10px"
  full: "50%"
spacing:
  rail: "268px"
  column: "920px"
  margin: "300px"
  mast: "64px"
  mast-compact: "56px"
  sheet-top: "44px"
  sheet-side: "clamp(28px, 4vw, 64px)"
  sheet-bottom: "88px"
  flow: "14px"
  h2-gap: "52px"
  h3-gap: "34px"
  grid-gap: "12px"
components:
  button-primary:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
    rounded: "{rounded.lg}"
    padding: "0 20px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.heading}"
    textColor: "{colors.ground}"
  button-outline:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text}"
    rounded: "{rounded.lg}"
    padding: "0 12px"
    height: "32px"
  button-outline-hover:
    backgroundColor: "{colors.raised}"
  search-trigger:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text-2}"
    rounded: "{rounded.lg}"
    padding: "0 12px"
    height: "38px"
  nav-link:
    textColor: "{colors.text-2}"
    typography: "{typography.nav}"
    rounded: "{rounded.md}"
    padding: "5px 10px"
  nav-link-active:
    backgroundColor: "{colors.current}"
    textColor: "{colors.text}"
  nav-tag:
    textColor: "{colors.text-2}"
    rounded: "5px"
    padding: "0 6px"
  feature-card:
    backgroundColor: "{colors.ground}"
    textColor: "{colors.text-2}"
    rounded: "{rounded.xl}"
    padding: "16px 18px"
  feature-card-hover:
    backgroundColor: "{colors.raised}"
  aside:
    backgroundColor: "{colors.panel}"
    textColor: "{colors.text}"
    rounded: "{rounded.xl}"
    padding: "14px 16px"
  code-block:
    backgroundColor: "{colors.code-bg}"
    typography: "{typography.code}"
    rounded: "{rounded.xl}"
  inline-code:
    backgroundColor: "{colors.raised}"
    textColor: "#f2f2f2"
    rounded: "{rounded.md}"
    padding: "1px 5px"
  support-mark:
    rounded: "{rounded.full}"
    size: "8px"
---

# Design System: Sculk Studio Docs

## Overview

**Creative North Star: "The Quiet Reference"**

A developer reference in white and grey on OLED true black, in the family of the Vercel, Next.js, Linear and Bun docs. The page is one black ground from edge to edge. Structure comes from hairline rules, neutral near-black fills on contained objects, and the weight of white type. Nothing is tinted. The only colour in the interface is the green, yellow and red of the support marks, so a reader's eye goes to support first.

The frame is centred: a navigation rail, a reading column and a right margin form one composition with a sticky masthead above. Every guide carries the same answers in the same places: a one-sentence summary under the title, what is on the page, which platforms support it (support marks) and which thread runs its code. On wide screens these sit in the margin with the table of contents first; below 1240px the margin gives way to a support sentence and a collapsible table of contents under the summary. One Platform control in the masthead re-marks the whole site.

Code blocks are the one place with a theme of their own: Catppuccin Mocha, the owner's editor theme, upright and lifted to high contrast. Poppins sets every word of interface and prose; JetBrains Mono sets code, version numbers and keys.

**Key Characteristics:**
- One true-black ground (#000) for masthead, rail, column and margin.
- White and grey text only; headings pure white, body near white.
- Colour reserved for support and status: green full, yellow partial, red none.
- Poppins throughout; JetBrains Mono for code and numbers.
- Catppuccin Mocha inside code blocks and nowhere else.
- Centred three-column frame: rail, column, margin.
- Navigation in familiar feature names, each category with a small stroke icon.

## Colors

A monochrome interface on true black, with three signal colours that only ever mean support.

### Primary
- **Support Green** (good): a filled mark for full support; the Tip icon in asides.
- **Support Yellow** (partial): a half-filled mark for partial support; the Caution icon.
- **Support Red** (bad): a hollow ring for no support; the Danger icon and, at 50% alpha, the Danger aside border. The three hues are Catppuccin Mocha's green, yellow and red, so they sit in tune with the code blocks.

### Neutral
- **True Black** (ground): the page, masthead, rail, column and margin. The sheet token resolves to the same value.
- **Panel Black** (panel): the fill of asides, MDX cards and the search dialog.
- **Code Black** (code-bg): code blocks and their title bar (the code-bar token is the same value).
- **Raised Grey** (raised): the search trigger, inline code, hover fills on cards, footer links, search results and the Copy page button.
- **Current Grey** (current): the active rail item and the support row for the selected platform.
- **Hairline** (rule): column dividers, list borders, table rows, the masthead's bottom edge.
- **Strong Hairline** (rule-strong): card, button, input and code block borders; table head rules.
- **Pure White** (heading): page titles, section headings, feature card titles, link hover.
- **Near White** (text): body text, link text, the primary button fill.
- **Grey** (text-2): secondary text, rail links at rest, captions, card descriptions.
- **Dim Grey** (text-3): breadcrumbs, footnotes, category icons, link underlines, the section # link at rest.

### Named Rules
**The One Ground Rule.** Every region of the frame paints true black. Contained objects (code, cards, asides, the dialog) may sit on a neutral near-black fill; no region and no panel takes a hue.

**The Signal Rule.** Green, yellow and red mean support level (or the matching aside type) and nothing else. Links, the current page, focus and buttons stay white and grey.

**The White Text Rule.** Text is neutral white or grey. It never takes Catppuccin's blue-tinted text colour or any other tint.

## Typography

**Display Font:** Poppins (with system-ui, sans-serif), weights 600 and 700
**Body Font:** Poppins (with system-ui, sans-serif), weights 400 and 500
**Label/Mono Font:** JetBrains Mono Variable (with ui-monospace, monospace)

**Character:** One geometric sans for everything a reader reads, tightened slightly at heading sizes; a mono only where the text is code or a number to copy.

### Hierarchy
- **Display** (600, clamp(32px, 3.2vw, 42px), 1.15): the page title, balanced, in pure white. The home title runs to 44px at 1.12 and caps at 24ch.
- **Headline** (600, 24px, 1.3): section headings in prose, 52px above.
- **Title** (600, 18px, 1.3): subsections, 34px above. Feature and MDX card titles use 600 at 15.5px to 16px.
- **Lede** (400, 17px, 1.7): the opening paragraph, up to 64ch.
- **Body** (400, 16px, 1.75): prose, up to 70ch.
- **Nav** (400, 14.5px, 1.45): rail links; 500 on the current page. Table of contents links are 14px (13.5px at depth 3).
- **Label** (600, 14px, sentence case): rail category names and margin section labels ("Where it runs", "Support", "On this page").
- **Small** (400, 13px): breadcrumb and page meta; 12.5px for footnotes and badges.
- **Code** (JetBrains Mono, 14px, 1.7): code blocks. Inline code is 0.86em on a raised grey chip. Version numbers and keyboard keys also use the mono.

### Named Rules
**The One Family Rule.** Poppins sets all interface and prose text, headings included. JetBrains Mono is for code, numbers, keys and paths only.

**The Sentence Case Rule.** Labels are sentence case at normal letter spacing. Hierarchy comes from weight and white versus grey.

**The Upright Code Rule.** Code is never italic; Catppuccin's italic functions and types are set upright.

## Layout

A centred three-column grid: rail (268px), column (up to 920px), margin (300px), under a sticky 64px masthead that uses the same three tracks. The rail and the margin are sticky below the masthead and scroll on their own. The reading sheet pads 44px top, clamp(28px, 4vw, 64px) sides and 88px bottom; prose caps at 70ch, while code blocks widen to the full column.

Vertical rhythm in prose: 14px between blocks, 52px above an h2, 34px above an h3, 10px under a heading. Margin sections sit 32px apart. Card grids use a 12px to 14px gap with 260px minimum tracks.

At 1240px and below, the margin moves under the column as a grid of 260px tiles. At 860px and below, the masthead shrinks to 56px, the brand word and version hide, the search trigger fills the row, and the rail becomes a full-screen overlay behind a menu button. At 600px and below, feature cards stack to one column, module rows stack name over description, and code scrolls sideways instead of wrapping.

## Elevation & Depth

Flat. Depth comes from hairlines and from a few steps of near-black fill (#000 ground, #0a0a0a panels, #121212 code, #141414 raised), never from shadows. Code blocks have their frame shadow removed. The one shadow is on the search dialog, which floats over a 72% black backdrop.

### Shadow Vocabulary
- **Dialog lift** (`box-shadow: 0 24px 60px rgba(0, 0, 0, 0.7)`): the search dialog only.

### Named Rules
**The Hairline Rule.** Separation is a 1px rule or a 1px border. A surface lifts only as a hover response (a raised grey fill), never at rest.

## Shapes

Soft rectangles. Containers (cards, asides, code blocks, the dialog, footer links) round at 10px; buttons, the search trigger and the platform select at 8px; rail links, inline code and search results at 6px; keyboard keys at 4px. Support marks are 8px circles; numbered step counters are square. Category icons are 16px line drawings with 1.4px round strokes.

## Components

### Buttons
- **Shape:** gently rounded (8px).
- **Primary:** near-white fill (#ededed) with black text, 600 weight, 44px tall, 20px side padding. Used once, for "Install Sculk" on the home page. Hover goes to pure white.
- **Outline:** black fill, strong hairline border, near-white text, 32px tall ("Copy page"). Hover fills raised grey. The label confirms with "Copied".
- **Text links as actions:** secondary actions beside the primary are grey underlined links that turn white on hover.
- **Focus:** a 2px near-white outline at 2px offset on every focusable element.

### Chips (Badge and rail tag)
- **Badge:** a support mark followed by grey 12.5px words ("done", "in progress"). The mark carries the colour; the words stay grey.
- **Rail tag:** a word, "Partial" or "Unsupported", in an 11px outlined chip (5px radius) at the end of a rail link. It shows only for the platform chosen in the masthead.

### Cards / Containers
- **Corner Style:** 10px.
- **Background:** feature cards are transparent on the ground; MDX cards and asides use panel black (#0a0a0a).
- **Shadow Strategy:** none (see Elevation & Depth).
- **Border:** 1px strong hairline. Feature card hover lifts the border to #4a4a4a and fills raised grey.
- **Internal Padding:** 16px 18px for cards, 14px 16px for asides.
- **Asides:** a neutral box with a small line icon and a title (Note, Tip, Caution, Danger). The icon takes the signal colour for Tip, Caution and Danger; only Danger also tints its border.

### Inputs / Fields
- **Search trigger:** raised grey, strong hairline border, 8px radius, 38px tall, grey placeholder text, a Ctrl K key at the right. Ctrl K, Cmd K and / open the dialog.
- **Search dialog:** 640px wide at 12vh from the top, panel black, 10px radius. The field is borderless on transparent; results show a mono path, a white 600 title and a grey excerpt, with matches in white and underlined, not highlighted. The selected result fills raised grey.
- **Platform select:** an outlined 34px box (8px radius) with a grey "Platform" label and the chosen platform in 600 white.

### Navigation
- **Rail:** categories in familiar feature names ("Setup", "Core concepts", "Commands and events", "Text and configs", "Player UI", "Items and content", "Reference"), each a 14px 600 white label with a 16px dim grey stroke icon. Links indent under a 1px rule, grey at rest, white on hover, and the current page is white 500 on current grey (6px radius). The rail scrolls the current page into view.
- **Breadcrumb:** category / page in 13px dim grey, separated by a hairline-grey slash.
- **On this page:** links in a column with a 1px left rule; the section being read turns white and takes a white left rule.
- **Section links:** each h2 and h3 gains a dim grey # link after its text, shown on hover or focus (always at 60% on touch screens).
- **Previous and next:** two outlined 10px boxes at the foot of a page, label in dim grey, page name in 600.

### Support Mark (signature)
An 8px circle whose shape carries the meaning and whose colour follows it: filled green for full, half-filled yellow (diagonal) for partial, hollow red ring for none, a short grey dash where the platform does not apply. Each mark has a spoken label. In the margin's Support table, the row for the selected platform fills current grey.

### Where It Runs
A short note in the margin naming the thread that runs a page's code, the lead in white 600 and the rest in grey: "The player's thread. The main thread on Paper; the region that owns the player on Folia and Canvas."

### Page Summary
Every guide's description appears under its title at 18px in grey, with Copy page on the title's row and the edit link at the foot of the page.

### Code Blocks
Catppuccin Mocha syntax on code black (#121212), with minimum syntax contrast raised to 7.5 so comments read clearly. 10px radius, strong hairline border, no shadow, JetBrains Mono 14px at 1.7. Title tabs share the block's fill, with a near-white underline on the active tab. Long lines wrap at word breaks and keep their indent; on phones they scroll instead.

## Do's and Don'ts

### Do:
- **Do** paint every frame region true black (#000) and separate regions with 1px hairlines.
- **Do** keep interface text white and grey: pure white for headings, #ededed for body, #a1a1a1 and #8f8f8f for secondary text.
- **Do** use green, yellow and red only for support level and the matching aside types, and always with a shape or word that carries the meaning without colour.
- **Do** set every word of interface and prose in Poppins, and code, versions and keys in JetBrains Mono.
- **Do** keep Catppuccin Mocha inside code blocks, set upright, at a minimum contrast of 7.5.
- **Do** name navigation and features with the words developers search for ("Commands", "GUIs and menus", "Scoreboards and HUD", "Configs").
- **Do** pair every icon or mark with a word label or a spoken label.
- **Do** keep the frame centred as rail, column and margin.

### Don't:
- **Don't** tint the ground or any panel with a hue, or give the reading column a different ground from the frame.
- **Don't** use Catppuccin's blue-tinted text or its accent colours outside code blocks; only its green, yellow and red appear, as support signals.
- **Don't** use purple or mauve accents anywhere in the interface.
- **Don't** bring back the Atlas palette or style: bone text, terracotta or orange accents, teal and violet region colours, Archivo or Atkinson type, a chunk-grid page background, square support marks.
- **Don't** set labels in uppercase with wide letter spacing.
- **Don't** use a symbol the reader has to learn: no bare glyphs without a word or a spoken label.
- **Don't** put internal jargon in UI copy; say what a reader would search for.
- **Don't** add shadows to cards, code or buttons; the search dialog is the only lifted surface.
