---
title: Commands Overview
description: DSL-first command system with sender routing, typed arguments, and auto tab-completion.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

Commands are defined with the `command {}` DSL and registered via `sculk.commands.registerAll()`.
Each command lives in its own file and returns a `CommandBuilder`.

## Defining a command

<Tabs>
<TabItem label="Kotlin">
```kotlin
// commands/AdminCommand.kt
fun adminCommand() = command("sculk") {
    description = "Sculk admin commands."
    permission = "sculk.admin"

    sub("reload") {
        player {
            sculk.config.reload()
            reply("<green>Config reloaded.")
        }
    }

    sub("ping") {
        executes {
            reply("<gray>Pong!")
        }
    }
}
```
</TabItem>
<TabItem label="Java">
```java
public class AdminCommand {
    public static CommandBuilder build(SculkPlatform sculk) {
        return JavaCommand.builder("sculk")
            .description("Sculk admin commands.")
            .permission("sculk.admin")
            .sub("reload", sub -> sub
                .player(ctx -> {
                    sculk.getConfig().reload();
                    ctx.reply("<green>Config reloaded.");
                })
            )
            .sub("ping", sub -> sub
                .executes(ctx -> ctx.reply("<gray>Pong!"))
            );
    }
}
```
</TabItem>
</Tabs>

## Registering commands

<Tabs>
<TabItem label="Kotlin">
```kotlin
sculk.commands.registerAll(
    adminCommand(),
    homeCommand(),
    shopCommand(),
)
```
</TabItem>
<TabItem label="Java">
```java
sculk.getCommands().registerAll(
    AdminCommand.build(sculk),
    HomeCommand.build(),
    ShopCommand.build()
);
```
</TabItem>
</Tabs>

## Sender-type routing

| Block | Who runs it | Rejects |
| --- | --- | --- |
| `player { }` | Online player | Console (auto error) |
| `console { }` | Server console | Players (auto error) |
| `executes { }` | Anyone | — |

`player` is non-null inside a `player { }` block — no null checks needed.

## Auto tab-completion

Generated automatically from the subcommand tree and registered argument names. No extra code needed.

## Permission checks

Set `permission` on any node. Players without the permission get a clean error message automatically.
Root-level permissions cascade — a player denied at the root never reaches subcommands.

## Best practices

- One command per file, returning a `CommandBuilder` from a top-level function.
- Register everything in `onEnable` with `registerAll`.
- Use `executes { }` when behaviour is identical for players and console.
- Keep handlers thin — delegate heavy logic to a service class.
