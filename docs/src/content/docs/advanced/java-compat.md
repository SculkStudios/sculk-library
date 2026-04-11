---
title: Java Compatibility
description: Using Sculk Studio from Java plugins.
---

Every Kotlin DSL entry point has a paired Java fluent builder. Java builders live in `gg.sculk.*.java` sub-packages.

## Bootstrap

```java
public class MyPlugin extends JavaPlugin {
    private SculkPlatform sculk;

    @Override
    public void onEnable() {
        sculk = JavaSculkPlatform.create(this, cfg -> cfg
            .gui()
            .config()
            .data()
        );

        sculk.getCommands().register(
            JavaCommand.builder("greet")
                .permission("myplugin.greet")
                .player(ctx -> ctx.reply("<green>Hello, " + ctx.getPlayer().getName() + "!"))
                .build()
        );
    }

    @Override
    public void onDisable() {
        sculk.close();
    }
}
```

## Commands

```java
JavaCommand.builder("homes")
    .permission("homes.use")
    .sub("set", sub -> sub
        .string("name")
        .player(ctx -> {
            String name = ctx.argument("name");
            ctx.reply("<green>Home '" + name + "' set.");
        })
    )
    .build();
```

## GUIs

```java
Gui menu = JavaGui.builder("Main Menu")
    .size(27)
    .item(13, item -> item
        .material(Material.DIAMOND)
        .name("<aqua>Click Me")
        .onClick(ctx -> {
            ctx.reply("<green>Clicked!");
            ctx.close();
        })
    )
    .build();

menu.openFor(player);
```

## Data — async via CompletableFuture

```java
JavaRepository<PlayerData, UUID> repo = JavaRepository.wrap(
    sculk.getData().repository(PlayerData.class, UUID.class)
);

repo.find(player.getUniqueId()).thenAccept(result -> {
    if (result instanceof SculkResult.Success) {
        PlayerData data = ((SculkResult.Success<PlayerData>) result).getValue();
        // handle result on calling thread
    }
});
```

## Notes for Java users

- `SculkResult<T>` is a sealed interface — use `instanceof` with pattern matching (Java 16+) or `getClass()` checks.
- All async operations run on a fork-join pool by default; pass a custom `Executor` to `JavaRepository.wrap(repo, executor)` if needed.
- Kotlin `Unit` return types appear as `Void` in Java — no action needed.
