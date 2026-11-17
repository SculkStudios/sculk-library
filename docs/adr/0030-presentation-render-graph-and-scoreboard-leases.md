# ADR-0030: Presentation as a per-player render graph with scoreboard leases

**Status**: Accepted · **Date**: 2026-11-17 · **Supersedes**: —

## Context

`sculk-presentation` draws action bars, boss bars, titles, tab header and footer, sidebars,
below-name lines and nametags. Placeholders signal changes per value (ADR-0026), and
`PERFORMANCE.md` asks for no packets and no scheduled work when nothing changed. On Folia every
player is owned by a region thread and the scoreboard manager throws; Canvas allows scoreboards
with writes on `Global` (THREADING.md rule 12, ADR-0023). A player sees one scoreboard, which every
sidebar plugin wants for itself; TAB yields its sidebar while another plugin's board is shown and
restores it afterwards (`docs/research/e2-presentation-gui-config-data-packets.md` §A). A real
client in the server suites (ADR-0029) can now observe every surface.

## Decision

- **Layouts are immutable specs.** A `HudLayout` lists surfaces whose content is MiniMessage
  templates rendered through `sculk-text` with the plugin's placeholder resolver, so values never
  become markup. A surface may carry a condition, checked whenever the surface is rendered. Showing
  a layout with a surface the platform cannot draw fails with `UnsupportedCapability` before
  anything is shown.
- **One driver per player, woken on demand.** Each player's HUD runs in the player's own context
  and keeps at most one pending wake: a due interval or a change signal asks for one, wakes within a
  tick coalesce, and an idle HUD schedules nothing.
- **Dependencies are recorded while rendering.** The resolver is wrapped to record which
  placeholder tags a surface used. A change signal marks only the surfaces that used that tag, for
  that player or for everyone. A surface re-renders when marked, when its `Update` is due
  (`OnChange`, `Every(ticks)`, `Manual`), or when its condition flips. Output is compared with what
  was last sent and nothing is sent when it is equal: a sidebar line rewrites one score's custom
  name, a boss bar property is set only when it differs. An action bar is resent every two seconds
  while shown, because the client fades it.
- **Native surfaces everywhere, scoreboards where the platform allows.** Action bar, boss bars,
  titles and tab header and footer use Adventure and Paper API on every platform. The sidebar uses a
  per-player scoreboard on Paper and Canvas (written on `Global` on Canvas) and is `Unavailable` on
  Folia, naming `sculk-presentation-packets`.
- **Scoreboard leases.** Showing a sidebar takes a lease that remembers the board the player had and
  restores it when the HUD closes. A board that is neither the main board nor Sculk's own is
  foreign: the sidebar yields to it and tries again on its next wake, and never replaces it.
- **Nametags and below-name lines are the target's surfaces, on shared boards.** They are rendered
  for the player whose name it is, so every viewer sees the same text: one team and one fixed-format
  score per player on the main board, copied onto each board Sculk gave a player for a sidebar.
  A player already in another plugin's team, or another objective in the below-name slot, is
  foreign in the same sense, and Sculk yields. Text that differs per viewer is left to
  `sculk-presentation-packets`.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| One repeating task per surface or per line | Thousands of tasks, and work when nothing changed. |
| A global task re-rendering every player every N ticks | Cannot touch players across Folia regions; renders what did not change. |
| Declaring dependencies by hand | Drifts from the template; recording them at render time is exact. |
| Taking the scoreboard unconditionally | Silently clobbers another plugin's board, which TAB's experience shows users report as bugs. |
| Per-viewer boards with a team and score for every target | Grows with the square of the player count and gives every player a board of Sculk's, hiding every other plugin's main-board teams from all of them. |

## Consequences

The render engine is unit-tested without a server, and the client suite checks what a player sees
on every anchor, Folia and Canvas. Nametags and below-name lines cost one team and one score per
player on the main board and on each Sculk sidebar board. A player who sees a Sculk sidebar does not
see other plugins' main-board teams; the guide says so. Revisit when `sculk-presentation-packets`
lands, or if Paper gains per-viewer team or score API.
