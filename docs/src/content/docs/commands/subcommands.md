---
title: Subcommands & Arguments
description: How to build multi-level commands with typed arguments.
---

## Subcommands

Nest `sub { }` calls to any depth. Each level is its own `CommandNode` with its own permission, executor, and arguments.

```kotlin
command("homes") {
    permission = "homes.use"

    sub("set") {
        string("name")
        player {
            val name = argument<String>("name")
            reply("<green>Home '$name' set.")
        }
    }

    sub("go") {
        string("name")
        player {
            val name = argument<String>("name")
            reply("<aqua>Teleporting to '$name'…")
        }
    }

    sub("delete") {
        string("name")
        player {
            val name = argument<String>("name")
            reply("<red>Home '$name' deleted.")
        }
    }
}
```

## Argument types

| Method | Type | Notes |
|---|---|---|
| `string("name")` | `String` | Single token |
| `int("amount")` | `Int` | Whole numbers |
| `double("value")` | `Double` | Decimal numbers |
| `boolean("flag")` | `Boolean` | `true`/`false` |
| `player("target")` | `Player` | Online players only; tab-completes player names |
| `choice("mode", "a", "b", "c")` | `String` | Fixed set; tab-completes choices |

All arguments are required by default. Pass `optional = true` to make one optional:

```kotlin
int("amount", optional = true)
```

## Reading arguments

Use `argument<T>("name")` inside any executor block:

```kotlin
player {
    val target = argument<Player>("target")
    val amount = argument<Int>("amount")
    target.sendMessage("You received $amount coins.")
}
```

If the argument is optional:

```kotlin
val amount = argumentOrNull<Int>("amount") ?: 1
```

## Java API

```java
Command.builder("homes")
    .permission("homes.use")
    .sub("set", sub -> sub
        .string("name")
        .player(ctx -> ctx.reply("<green>Home '" + ctx.<String>argument("name") + "' set."))
    )
    .build();
```
