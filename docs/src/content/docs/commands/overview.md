---
title: Commands Overview
description: DSL-first command system with sender routing, typed arguments, and auto tab-completion.
---

Sculk Studio's command system is built around a tree of `CommandNode`s. You describe the tree with the `command {}` DSL and register it via `sculk.commands.register()`.

## Basic command

```kotlin
sculk.commands.register(
    command("sculk") {
        permission = "sculk.admin"
        description = "Main Sculk admin command."

        sub("reload") {
            player {
                reply("<green>Config reloaded.")
            }
        }

        sub("ping") {
            executes {  // works for both players and console
                reply("<gray>Pong!")
            }
        }
    }
)
```

## Sender-type routing

| Block | Sender | Rejects |
|---|---|---|
| `player { }` | Online player | Console (with error) |
| `console { }` | Server console | Players (with error) |
| `executes { }` | Any | — |

Inside a `player { }` block, `player` is non-null.

## Auto tab-completion

Tab-completion is generated automatically from the subcommand tree and registered argument names. No extra code needed.

## Error handling

If a player lacks permission, they receive `<red>You don't have permission to use this command.` automatically. If argument parsing fails, the user gets a typed error showing the expected argument name and type.

## Best practices

- Put top-level permission checks on the root command node.
- Use `executes { }` for commands that behave identically for all senders.
- Prefer `sub("name") { }` over deeply nested logic — each subcommand should do one thing.
