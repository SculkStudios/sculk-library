---
title: Your First Plugin
description: Build a minimal Sculk Studio plugin with a command and a GUI.
---

This guide walks through building a plugin that registers a command, opens a GUI, and loads a config.

## Bootstrap

Create a `JavaPlugin` subclass and initialise `SculkPlatform` in `onEnable`:

```kotlin
class MyPlugin : JavaPlugin() {
    lateinit var sculk: SculkPlatform

    override fun onEnable() {
        sculk = SculkPlatform.create(this) {
            gui()     // enable GUI lifecycle routing
            config()  // enable typed config loading
        }

        sculk.commands.register(
            command("greet") {
                description = "Greet the player."
                permission = "myplugin.greet"
                player {
                    reply("<green>Hello, ${player!!.name}!")
                }
            }
        )
    }

    override fun onDisable(): Unit = sculk.close()
}
```

## Adding a GUI

```kotlin
val menu = gui("Main Menu") {
    size = 27

    item(13) {
        material = Material.NETHER_STAR
        name = "<gold><bold>Click me!"
        lore("<gray>Opens something cool.")
        onClick {
            reply("<aqua>You clicked the star!")
            close()
        }
    }
}

// Open it for a player inside a command
player {
    menu.openFor(player!!)
}
```

## Loading a config

```kotlin
@ConfigFile("settings.yml")
data class Settings(
    val prefix: String = "<gray>[<aqua>MyPlugin<gray>]",
    val maxHomes: Int = 5,
)

val settings = sculk.config.load<Settings>()
```

## Java equivalent

```java
SculkPlatform sculk = SculkPlatform.create(this, cfg -> cfg.gui().config());

sculk.getCommands().register(
    Command.builder("greet")
        .permission("myplugin.greet")
        .player(ctx -> ctx.reply("<green>Hello, " + ctx.getPlayer().getName() + "!"))
        .build()
);
```

## Next steps

- [Commands — subcommands and arguments](/commands/subcommands/)
- [GUIs — pagination](/gui/pagination/)
- [Config — hot reload](/config/hot-reload/)
