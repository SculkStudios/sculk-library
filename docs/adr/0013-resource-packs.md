# ADR-0013: Resource-pack architecture

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

Plugins increasingly ship visual assets: item models, fonts for HUD glyphs, sounds, sprites. Today
authors duplicate magic identifiers between Kotlin and pack JSON, discover broken references only
in game, and host packs ad hoc. Paper supports pack stacking, removal by UUID and per-pack status
through Adventure's `ResourcePackRequest` on every anchor, and a configuration-phase audience from
1.21.7 (whether packs sent there behave fully is an open question). Sculk 5's glyph measurement
knows only the vanilla ASCII font.

## Decision

Split build time from run time.

At build time the Gradle plugin compiles plugin-owned assets from `src/main/resourcepack`: it
validates namespaces, paths, duplicates, JSON, model and texture references, sound references, font
providers and codepoints, and pack metadata; generates a typed `Assets` object
(`Assets.UI.COINS: Key`) so code never spells an identifier; emits glyph-width metrics for every
custom font so `sculk-text` centres and wraps correctly; and produces a deterministic zip with its
SHA-1.

At run time `sculk-resourcepack` manages a per-player pack stack from descriptors (UUID, URI, hash,
prompt, required), sends during configuration where the capability is native and on join
otherwise, tracks status per pack, retries only failures that are retryable, and removes individual
packs. Hosting is an SPI with static URL and embedded Ktor implementations; S3-compatible and CDN
providers are third-party. Zip reading is entry-by-entry with size caps and path normalisation;
remote URLs are HTTPS by default with private ranges blocked.

Sprites are descriptors resolved by a renderer chosen per use: font glyph, item model, display
entity or map. There is no fake "sprite entity".

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Runtime-only pack helpers | Leaves the identifier duplication and broken-reference problems unsolved. |
| Mandatory embedded HTTP server | Many hosts forbid extra ports; a static URL is often better. |
| Merge every plugin's pack into one server pack | Upstream stacking already does this correctly per pack. |

## Consequences

The Gradle plugin owns a pack compiler and a code generator, which need their own tests and
fixtures. Maven users get the runtime but not the compiler until the Maven plugin exists. Item and
scene APIs accept generated `Key`s, so a renamed asset breaks the build, not the game.
