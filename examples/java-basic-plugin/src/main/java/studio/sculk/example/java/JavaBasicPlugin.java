package studio.sculk.example.java;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import studio.sculk.command.SculkCommands;
import studio.sculk.gui.SculkGui;
import studio.sculk.items.SculkItems;
import studio.sculk.platform.SculkPlugin;
import studio.sculk.series.SculkBlocks;
import studio.sculk.series.SculkSeries;

/**
 * A complete Sculk Studio plugin written entirely in idiomatic Java.
 *
 * <p>This example is also the project's Java compile-gate: it exercises the Java-facing overloads
 * across the platform, command, event, task, GUI, item, and series modules, so a regression that
 * breaks Java ergonomics fails {@code ./gradlew build}.
 */
public final class JavaBasicPlugin extends SculkPlugin {

    // Java-friendly Consumer constructor — no Unit.INSTANCE, no Function1, no .Companion.
    public JavaBasicPlugin() {
        super(builder -> {
            builder.gui();
            builder.config();
        });
    }

    @Override
    protected void setup() {
        // --- Commands: Consumer DSL, Class-based enum, Java executors ---------------------
        getSculk().getCommands().register(
            SculkCommands.command("hello", cmd -> {
                cmd.setDescription("Say hello and open a demo GUI.");
                cmd.setPermission("basic.hello");

                cmd.player(ctx -> {
                    Player player = ctx.getPlayer();
                    ctx.reply("<gradient:aqua:blue><bold>Hello, " + player.getName() + "!</bold></gradient>");
                    openDemoGui(player);
                });

                cmd.sub("give", sub -> {
                    sub.material("type");
                    sub.player(ctx -> {
                        Material type = ctx.argument("type", Material.class);
                        ctx.getPlayer().getInventory().addItem(SculkItems.item(type));
                        ctx.reply("<green>Gave you " + type + ".");
                    });
                });
            })
        );

        // --- Events: Class token + Consumer handler ---------------------------------------
        getSculk().getEvents().listen(PlayerJoinEvent.class, event ->
            event.getPlayer().sendMessage("Welcome to a Java-first Sculk server!")
        );

        // --- Tasks: Runnable overload (fire-and-forget on the main thread) ----------------
        getSculk().getTasks().repeating(20L * 300L, () ->
            getServer().broadcast(net.kyori.adventure.text.Component.text("Sculk uptime tick"))
        );

        getLogger().info("Sculk material lookup works from Java: " + SculkSeries.material("diamond_sword"));
    }

    private void openDemoGui(Player player) {
        SculkGui.gui("<dark_aqua><bold>Sculk Demo (Java)", menu -> {
            menu.setSize(27);

            menu.item(13, slot -> {
                slot.setMaterial(Material.SCULK_CATALYST);
                slot.setName("<aqua>A Sculk block!");
                slot.lore("<gray>isSculkBlock = " + SculkBlocks.isSculkBlock(Material.SCULK_CATALYST));
                slot.onClick(ctx -> {
                    ctx.reply("<green>Clicked the catalyst!");
                    ctx.close();
                });
            });

            menu.border(Material.BLACK_STAINED_GLASS_PANE, slot -> {
                slot.setName("<gray> ");
            });
        }).openFor(player);
    }
}
