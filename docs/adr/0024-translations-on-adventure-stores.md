# ADR-0024: Translations on Adventure's translation stores

**Status**: Accepted · **Date**: 2026-10-12 · **Supersedes**: —

## Context

`sculk-text` today loads `messages_<locale>.properties` bundles into its own `Translations` map,
resolves the locale chain itself, renders the template on the server, and chooses plurals with a
zero / one / other rule. That rule is wrong for most languages Minecraft ships (Polish, Russian,
Czech, Arabic and others have `few` and `many` forms), and a template rendered before sending
cannot follow the viewer's locale.

Adventure already solves the part that is not Sculk's to own. Since 4.20.0 it ships
`TranslationStore`, `MiniMessageTranslationStore` (MiniMessage templates with arguments as tags)
and `GlobalTranslator`; all three exist unchanged in 5.2.0 (checked in the 4.20.0 and 5.2.0 jars),
while `TranslationRegistry` is gone in 5. Paper and Velocity render `Component.translatable` through
`GlobalTranslator` per viewer locale, and 4.20.0 is exactly the floor Adventure (Paper 1.21.4).

## Decision

`sculk-text` builds translations on Adventure:

- Each plugin gets one `MiniMessageTranslationStore`, registered as a source on `GlobalTranslator`
  and owned by the plugin's `ResourceScope`, so closing the scope removes it.
- Messages are sent as translatable components and rendered per viewer by the platform; rendering
  to a fixed locale stays available for logs, consoles and tests.
- Sculk adds what Adventure lacks: bundle loading (UTF-8 files from the jar overlaid by operator
  files in the data folder, reloaded transactionally like configuration), the fallback chain
  exact locale → language → default with a visible key when everything misses, CLDR plural
  categories (`zero`, `one`, `two`, `few`, `many`, `other`) from rules generated at build time from
  CLDR data, and theme expansion so semantic style tags work inside translated templates.
- Templates stay trusted and values stay values: arguments enter through MiniMessage translator
  arguments as components or unparsed text, never by string replacement.

## Implementation note (2026-11-17)

Built as decided, with one change: each plugin's source is a `MiniMessageTranslator` over Sculk's
own bundles rather than a `MiniMessageTranslationStore`, because the store selects no plural form
and expands no theme style. It is still one `GlobalTranslator` source per plugin, owned by a
`ResourceScope`. The CLDR rules are generated from CLDR 49 `plurals.json` and tested against every
integer sample it lists. Adventure 5's `GlobalTranslator` asks a source `canTranslate` before
translating, which the source answers for plural keys too.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep Sculk's own map and server-side rendering | Duplicates Adventure, ignores per-viewer locale, and keeps the wrong plural rule. |
| ICU4J for plurals | Over 10 MB on every plugin's classpath for one table. |
| `TranslationRegistry` | Removed in Adventure 5. |

## Consequences

The `Translations` API in `sculk-text` changes before 6.0 ships; its ABI dump changes in the same
commit. The fallback chain is tested against the store's own lookup on Adventure 4.20.0 and 5.2.0
through `testAdventureLatest`. The build gains a small CLDR plural-rule generator; CLDR data is under
the Unicode licence, which permits this. Plugins on Velocity use the same stores unchanged.
