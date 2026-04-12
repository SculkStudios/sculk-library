---
title: GUIs Overview
description: Chest inventory GUIs with per-player sessions, click routing, and automatic cleanup.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

GUIs are **immutable definitions**. Define once, open for any player. Sessions are created automatically on `openFor` and cleaned up when the player closes the inventory or disconnects — no manual cleanup ever needed.

## Defining a GUI

<Tabs>
<TabItem label="Kotlin">
```kotlin
// gui/MainMenu.kt
val mainMenu = gui("<dark_aqua><bold>Main Menu") {
    size = 27

    item(13) {
        material = Material.NETHER_STAR
        name = "<gold><bold>Click Me"
        lore(
            "<gray>Does something cool.",
            "<dark_gray>Right-click for more options.",
        )
        onClick {
            reply("<green>Clicked!")
            close()
        }
    }
}
```
</TabItem>
<TabItem label="Java">
```java
// gui/MainMenu.java
public class MainMenu {
    public static final Gui INSTANCE = JavaGui.builder("<dark_aqua><bold>Main Menu")
        .size(27)
        .item(13, item -> item
            .material(Material.NETHER_STAR)
            .name("<gold><bold>Click Me")
            .lore("<gray>Does something cool.", "<dark_gray>Right-click for more options.")
            .onClick(ctx -> {
                ctx.reply("<green>Clicked!");
                ctx.close();
            })
        )
        .build();
}
```
</TabItem>
</Tabs>

## Opening for a player

<Tabs>
<TabItem label="Kotlin">
```kotlin
// inside a command handler
player {
    mainMenu.openFor(player!!)
}
```
</TabItem>
<TabItem label="Java">
```java
.player(ctx -> MainMenu.INSTANCE.openFor(ctx.getPlayer()))
```
</TabItem>
</Tabs>

## Click context

Inside `onClick { }`, `this` is a `GuiContext`:

| Property / method | Description |
| --- | --- |
| `player` | The player who clicked |
| `click` | Click type (`LEFT`, `RIGHT`, `SHIFT_LEFT`, …) |
| `event` | Raw `InventoryClickEvent` — already cancelled |
| `reply(msg)` | Send a MiniMessage message to the player |
| `close()` | Close the inventory |
| `open(gui)` | Switch to another GUI without reopening manually |

## Switching GUIs

```kotlin
onClick {
    open(settingsMenu)  // seamless switch — no flicker
}
```

## Dynamic items

Items whose content differs per player — use `dynamicContent { player -> }`. The block runs when the GUI opens, so the displayed stack is always up to date:

<Tabs>
<TabItem label="Kotlin">
```kotlin
item(4) {
    material = Material.PLAYER_HEAD
    dynamicContent { player ->
        name = "<aqua>Welcome, <white>${player.name}"
        lore("<gray>Coins: <gold>${economy.getBalance(player)}")
    }
}
```
</TabItem>
<TabItem label="Java">
```java
.item(4, item -> item
    .material(Material.PLAYER_HEAD)
    .dynamicContent((builder, player) -> {
        builder.name("<aqua>Welcome, <white>" + player.getName());
        builder.lore("<gray>Coins: <gold>" + economy.getBalance(player));
    })
)
```
</TabItem>
</Tabs>

## Item glow

Add an enchantment shimmer to an item without showing any enchantment text:

```kotlin
item(22) {
    material = Material.DIAMOND
    name = "<aqua>Selected"
    glow = true   // shimmer with no tooltip
}
```

## Best practices

- Define GUIs as top-level `val`s or `object` constants — created once, reused for every player.
- Keep `onClick` handlers short. Heavy logic belongs in a service.
- Use `open(other)` rather than calling `player.closeInventory()` directly — it preserves session state.
