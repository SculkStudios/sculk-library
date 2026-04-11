---
title: GUIs Overview
description: Chest inventory GUIs with per-player sessions, click routing, and automatic cleanup.
---

Sculk Studio GUIs are **immutable definitions**. A `Gui` describes layout and handlers; a `GuiSession` holds per-player mutable state. Sessions are created automatically when you call `openFor(player)` and cleaned up when the player closes the inventory or disconnects.

## Defining a GUI

```kotlin
val menu = gui("Main Menu") {
    size = 27  // must be a multiple of 9, between 9 and 54

    item(13) {
        material = Material.DIAMOND
        name = "<aqua><bold>Diamonds!"
        lore(
            "<gray>Click to collect.",
            "<dark_gray>Limit: 64 per day.",
        )
        onClick {
            reply("<green>Collected!")
            close()
        }
    }
}
```

## Opening for a player

```kotlin
menu.openFor(player)
```

## Click context

Inside `onClick { }`, `this` is a `GuiContext` which provides:

| Member | Type | Description |
|---|---|---|
| `player` | `Player` | The player who clicked |
| `click` | `ClickType` | LEFT, RIGHT, SHIFT_LEFT, etc. |
| `event` | `InventoryClickEvent` | Raw event (already cancelled) |
| `reply(msg)` | — | Send a MiniMessage message |
| `close()` | — | Close the inventory |
| `open(gui)` | — | Switch to another GUI |

## Switching GUIs

```kotlin
onClick {
    open(settingsMenu)  // closes current, opens settingsMenu for this player
}
```

## Use cases

- Main menus, settings panels, shop interfaces
- Confirmation dialogs (click to confirm / click to cancel)
- Multi-page item browsers (see [Pagination](/gui/pagination/))

## Best practices

- Define GUIs once at startup as top-level `val`s, not per-player.
- Keep `onClick` handlers short — heavy logic belongs in a service.
- Use `close()` or `open(other)` rather than calling `player.closeInventory()` directly.
