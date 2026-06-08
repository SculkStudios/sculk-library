# Java Basic Plugin

A complete Sculk Studio plugin written **entirely in idiomatic Java** — no Kotlin, no
`Unit.INSTANCE`, no `.Companion.`, no `Function1`.

It demonstrates the Java-facing surface added in 4.5.0:

| Feature | Java API used |
| --- | --- |
| Bootstrap | `extends SculkPlugin` with the `Consumer<SculkPlatformBuilder>` constructor |
| Commands | `SculkCommands.command(name, Consumer)`, `cmd.player(Consumer)`, `cmd.sub(name, Consumer)` |
| Typed args | `ctx.argument("type", Material.class)` |
| Events | `getEvents().listen(PlayerJoinEvent.class, Consumer)` |
| Tasks | `getTasks().repeating(ticks, Runnable)` |
| GUIs | `SculkGui.gui(title, Consumer)`, `menu.item(slot, Consumer)`, `slot.onClick(Consumer)` |
| Items | `SculkItems.item(Material)` |
| Series | `SculkSeries.material("diamond_sword")` (`@JvmStatic`) |
| Sculk blocks | `SculkBlocks.isSculkBlock(Material)` |

This module doubles as the project's **Java compile gate**: it is compiled by `./gradlew build`,
so any regression that breaks Java ergonomics fails CI.

## Supported version

Paper 26.1.2+ (Java 25). Build with `./gradlew :examples:java-basic-plugin:shadowJar`.
