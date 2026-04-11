---
title: Pagination
description: Distribute items across multiple GUI pages with the pagination DSL.
---

When you have more items than slots, use `pagination { }` to distribute them across pages automatically.

## Basic pagination

```kotlin
val items: List<ItemStack> = getShopItems()

val shopMenu = gui("Shop") {
    size = 54

    pagination {
        slots += (0 until 45).toList()  // items fill the top 5 rows
    }

    // Navigation buttons
    item(45) {
        material = Material.ARROW
        name = "<gray>Previous"
        onClick { previousPage() }
    }
    item(53) {
        material = Material.ARROW
        name = "<gray>Next"
        onClick { nextPage() }
    }
}
```

## Supplying page entries

```kotlin
val session = shopMenu.openFor(player)
session.setEntries(items)
```

Entries are automatically distributed across the defined slots. Navigating with `previousPage()` / `nextPage()` re-renders the current page in place.

## Dynamic refresh

To refresh a single item slot without reopening the GUI:

```kotlin
session.refresh(slot)
```

To re-render all slots:

```kotlin
session.refreshAll()
```

## Use cases

- Shop browsers, auction houses, player list menus
- Admin item selectors
- Any list longer than a single page of slots
