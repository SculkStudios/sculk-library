---
title: Pagination
description: Distribute items across multiple GUI pages automatically.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

When you have more entries than slots, use `pagination { }` to distribute them across pages.

## Basic pagination

<Tabs>
<TabItem label="Kotlin">
```kotlin
val shopMenu = gui("<green><bold>Shop") {
    size = 54

    pagination {
        slots += (0 until 45).toList()  // top 5 rows hold items
    }

    item(45) {
        material = Material.ARROW
        name = "<gray>← Previous"
        onClick { previousPage() }
    }

    item(53) {
        material = Material.ARROW
        name = "<gray>Next →"
        onClick { nextPage() }
    }
}
```
</TabItem>
<TabItem label="Java">
```java
List<Integer> slots = IntStream.range(0, 45).boxed().toList();

Gui shopMenu = JavaGui.builder("<green><bold>Shop")
    .size(54)
    .pagination(p -> p.slots(slots))
    .item(45, item -> item
        .material(Material.ARROW)
        .name("<gray>← Previous")
        .onClick(GuiContext::previousPage)
    )
    .item(53, item -> item
        .material(Material.ARROW)
        .name("<gray>Next →")
        .onClick(GuiContext::nextPage)
    )
    .build();
```
</TabItem>
</Tabs>

## Supplying entries

<Tabs>
<TabItem label="Kotlin">
```kotlin
player {
    val session = shopMenu.openFor(player!!)
    session.setEntries(getShopItems())
}
```
</TabItem>
<TabItem label="Java">
```java
.player(ctx -> {
    GuiSession session = shopMenu.openFor(ctx.getPlayer());
    session.setEntries(getShopItems());
})
```
</TabItem>
</Tabs>

Entries are distributed across the defined slots automatically. Navigating with `previousPage()` / `nextPage()` re-renders the current page in-place with no inventory flicker.

## Dynamic refresh

Update a single slot without reopening the inventory:

```kotlin
session.refresh(slot)
```

Re-render all slots at once:

```kotlin
session.refreshAll()
```

## Use cases

- Shop browsers, auction houses, player list menus
- Any list longer than a single page of slots
