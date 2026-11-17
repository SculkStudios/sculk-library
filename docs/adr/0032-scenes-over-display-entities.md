# ADR-0032: Scenes over display entities

**Status**: Proposed · **Date**: 2026-11-17 · **Supersedes**: —

## Context

Holograms, floating labels and small animated models are drawn today with Minecraft's display
entities: `TextDisplay`, `ItemDisplay` and `BlockDisplay`. `MODULES.md` plans `sculk-scenes` on
`sculk-effects` and `sculk-packets-api`, neither of which exists yet; M5 starts with scenes.

The jar scans settle compatibility. `scanPaperApi` lists no display class among the differences
between anchors, and `javap` over the 1.21.4 and 26.3 `paper-api` jars shows `Display`,
`TextDisplay`, `ItemDisplay`, `BlockDisplay`, `Display.Brightness`, `Display.Billboard` and
`Transformation` identical, including `setTeleportDuration` and every text option. Per-player
visibility (`Entity.setVisibleByDefault`, `Player.showEntity` and `hideEntity` with a plugin) and
`EntityRemoveEvent` with its `UNLOAD` cause are on every anchor too. Nothing needs an adapter or a
capability probe.

What the API cannot do is show one entity's text differently to different players: an entity has
one metadata value per field. Paper offers no per-viewer metadata.

Three platform facts shape the runtime. On Folia and Canvas a display lives in the region owning
its chunk and may only be touched there (THREADING.md); a followed entity changes region, and
`Entity.teleport` throws, so moves go through `teleportAsync` from the owner. Paper's per-plugin
show and hide is stored on the `Player` object, which a rejoin replaces. A chunk unload removes
non-persistent entities, and a chunk must never be loaded to show a hologram.

## Decision

- **Scenes are immutable specs.** A `SceneSpec` lists text, item and block nodes by id, each with a
  `Transform`, billboard, brightness, view range, interpolation timing and, for text, its style.
  `Scene.update(spec)` diffs by id and writes only the changed parts: text alone when only text
  changed, a transform with its interpolation so the client slides it, a replacement only when a
  node changes kind. Text is a trusted MiniMessage template rendered through `sculk-text` with the
  plugin's placeholders; a change signal redraws only the texts that used that tag, and nothing is
  sent when the render equals what was sent.
- **Every scene has one driver.** Public calls record the wanted state and post a command; one
  coroutine per scene applies commands in order, hopping with `withRegion` and `withEntity` to the
  chunk, entity or viewer each step touches. A followed entity is read on its own scheduler, which
  follows it across regions; short moves glide with `teleportAsync` over the spec's
  `teleportTicks`, and a move to another world or beyond 16 blocks spawns the displays afresh.
- **Nothing leaks, nothing loads.** Entities are created non-persistent and fully drawn inside the
  spawn callback. A scene whose chunk is unloaded waits for `ChunkLoadEvent`; an unload removes its
  entities and the scene returns with the chunk; an entity removed by anyone else is put back. Each
  scene owns a child `ResourceScope`: closing it removes the entities on their owner, or inline
  during shutdown where every ownership check passes. A followed entity leaving the world closes
  the scene.
- **Visibility is Paper's.** `Visibility.Everyone` spawns visible and hides from listed players;
  `Visibility.only` spawns hidden and shows to chosen players by UUID, re-applied when they rejoin.
  Show and hide run on the viewer's thread.
- **Per-viewer text where the API allows, a seam where it does not.** A text node marked
  `perViewer` gets one entity per chosen online viewer, shown to that viewer alone and rendered
  with their placeholders. For `Visibility.Everyone` that would need one entity per player in
  range, so `spawn` and `update` return `UnsupportedCapability`. The seam is the slot model: a
  scene's entities are keyed by node and optional viewer, and an opt-in packet module on
  `sculk-packets-api`, as `sculk-presentation-packets` is for HUDs, adds a slot kind that keeps one
  shared entity and rewrites its text metadata per viewer. Both packet modules come later in M5,
  after the packets API; this module carries no packet code.
- **The fake shares the rules.** `FakeScenes` lives in the module, in `studio.sculk.scene.testing`,
  because it applies the same internal validation and render path; it refuses `isSpawned`, which
  only a world can answer.
- `sculk-scenes` depends on `sculk-paper`, `sculk-placeholders` and `sculk-items`, and starts
  `@SculkExperimental` throughout.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Packet-only virtual entities | Forces a GPL packet plugin on every server for labels the API draws natively (ADR-0023). |
| Riding the followed entity as a passenger | Smooth for free, but Bukkit refuses to teleport an entity with passengers unless the caller passes `RETAIN_PASSENGERS`, which breaks every other plugin's teleport of that player. |
| One shared entity rendered per viewer by swapping text before each viewer's tick | Paper sends metadata to all trackers at once; there is no per-viewer moment to swap in. |
| Persistent entities tagged in PDC and swept on start | A crash between spawn and sweep leaves them in the world file; non-persistent entities never reach it. |
| A repeating task per scene re-sending everything | Work and packets when nothing changed; on Folia a single task cannot reach every region. |
| Capability keys for display features | The scans show no difference across 1.21.4 to 26.3; a key that is `Native` everywhere is noise. Revisit when a newer anchor adds display API. |

## Consequences

A scene costs one entity per node, plus one per node and chosen viewer for per-viewer text; the
guide states it. Display features need no adapter today; the next `scanPaperApi` that shows a
display difference needs a capability and an anchor test. The integration suite proves spawn,
update, chosen viewers, per-viewer text, following across a far teleport, waiting for a chunk and
despawning with the scope on every Paper anchor, Folia and Canvas, with a real client. `MODULES.md`
changes its `sculk-scenes` row to the dependencies above; `sculk-effects` animations and
interaction entities arrive as later nodes and drivers. Revisit when `sculk-packets-api` lands
(per-viewer text for everyone), or if Paper gains per-viewer entity metadata.
