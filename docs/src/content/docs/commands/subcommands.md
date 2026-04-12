---
title: Subcommands & Arguments
description: Multi-level commands with typed arguments and tab-completion.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

## Subcommands

Nest `sub { }` calls to any depth. Each level has its own permission, executor, and arguments.

<Tabs>
<TabItem label="Kotlin">
```kotlin
fun homeCommand() = command("home") {
    description = "Manage your homes."
    permission = "homes.use"

    sub("set") {
        string("name")
        player {
            val name = argument<String>("name")
            reply("<green>Home <white>$name <green>set.")
        }
    }

    sub("go") {
        string("name")
        player {
            val name = argument<String>("name")
            reply("<aqua>Teleporting to <white>$name<aqua>…")
        }
    }

    sub("delete") {
        string("name")
        player {
            val name = argument<String>("name")
            reply("<red>Home <white>$name <red>deleted.")
        }
    }
}
```
</TabItem>
<TabItem label="Java">
```java
public class HomeCommand {
    public static CommandBuilder build() {
        return JavaCommand.builder("home")
            .description("Manage your homes.")
            .permission("homes.use")
            .sub("set", sub -> sub
                .string("name")
                .player(ctx -> {
                    String name = ctx.argument("name");
                    ctx.reply("<green>Home <white>" + name + " <green>set.");
                })
            )
            .sub("go", sub -> sub
                .string("name")
                .player(ctx -> {
                    String name = ctx.argument("name");
                    ctx.reply("<aqua>Teleporting to <white>" + name + "<aqua>…");
                })
            )
            .sub("delete", sub -> sub
                .string("name")
                .player(ctx -> {
                    String name = ctx.argument("name");
                    ctx.reply("<red>Home <white>" + name + " <red>deleted.");
                })
            );
    }
}
```
</TabItem>
</Tabs>

## Argument types

| Method | Parsed type | Notes |
| --- | --- | --- |
| `string("name")` | `String` | Single token |
| `int("amount")` | `Int` | Whole numbers |
| `double("value")` | `Double` | Decimal numbers |
| `boolean("flag")` | `Boolean` | `true` / `false` |
| `player("target")` | `Player` | Online only — tab-completes player names |
| `choice("mode", "a", "b")` | `String` | Fixed set — tab-completes choices |

Pass `optional = true` to make an argument optional:

```kotlin
int("amount", optional = true)
```

## Reading arguments

<Tabs>
<TabItem label="Kotlin">
```kotlin
player {
    val target = argument<Player>("target")
    val amount = argument<Int>("amount")
    val page   = argumentOrNull<Int>("page") ?: 1
}
```
</TabItem>
<TabItem label="Java">
```java
.player(ctx -> {
    Player target = ctx.argument("target");
    int amount    = ctx.argument("amount");
    int page      = ctx.<Integer>argumentOrNull("page") != null
                        ? ctx.<Integer>argumentOrNull("page")
                        : 1;
})
```
</TabItem>
</Tabs>

If the argument is missing or the wrong type, `argument<T>()` throws with a descriptive error automatically shown to the sender.
