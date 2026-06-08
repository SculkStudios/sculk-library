package studio.sculk.example.java;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import studio.sculk.config.SculkConfig;
import studio.sculk.data.SculkData;
import studio.sculk.data.cache.JavaCache;
import studio.sculk.data.repository.JavaProfileStore;
import studio.sculk.data.repository.JavaRepository;
import studio.sculk.data.repository.SculkRepository;
import studio.sculk.effects.SculkParticles;
import studio.sculk.effects.SculkSequences;
import studio.sculk.effects.SculkSounds;
import studio.sculk.effects.SculkTimelines;
import studio.sculk.gui.GuiBorderStyle;
import studio.sculk.gui.SculkGui;
import studio.sculk.gui.SculkGuiSlots;
import studio.sculk.items.SculkItems;
import studio.sculk.platform.SculkPlatform;
import studio.sculk.series.SculkBlocks;
import studio.sculk.series.SculkSeries;
import studio.sculk.text.SculkText;

/**
 * Compile-only proof that the full Java surface is reachable across every module.
 *
 * <p>Nothing here runs; it exists so {@code ./gradlew build} fails if any of these APIs stops being
 * callable from idiomatic Java. Each method takes its dependencies as parameters so no live server is
 * needed. Builder lambdas use block bodies — see the Java Interop docs.
 */
@SuppressWarnings("unused")
final class JavaApiSurface {

    private JavaApiSurface() {}

    record PlayerData(UUID uuid, int coins) {}

    static void platform(org.bukkit.plugin.java.JavaPlugin plugin) {
        SculkPlatform.create(plugin, b -> {
            b.gui();
            b.config();
            b.data();
            b.text();
            b.packets(p -> { /* configure */ });
        });
    }

    static void items() {
        SculkItems.item(Material.DIAMOND_SWORD, b -> {
            b.name("<red>Excalibur");
            b.glint();
            b.lore("<gray>Legendary");
            b.meta(meta -> {
                meta.setUnbreakable(true);
            });
        });
        SculkItems.skull(b -> {
            b.owner("Notch");
            b.name("<aqua>A head");
        });
    }

    static void effects(Player player) {
        SculkSounds.sound(Sound.ENTITY_PLAYER_LEVELUP, s -> {
            s.setVolume(1.0f);
            s.playTo(player);
        });
        SculkParticles.particle(Particle.FLAME, p -> {
            p.setLocation(player.getLocation());
            p.setCount(10);
            p.spawn();
        });
        SculkTimelines.timeline(tl -> {
            tl.at(0, () -> player.sendMessage("start"));
            tl.at(20, () -> player.sendMessage("later"));
            tl.loop(2);
        });
        SculkSequences.sequence(seq -> {
            seq.step(() -> player.sendMessage("step"));
            seq.delay(10);
            seq.step(() -> player.sendMessage("next"));
        });
    }

    static void seriesAndBlocks(Player player) {
        Material sword = SculkSeries.material("diamond_sword");
        boolean isSculk = SculkBlocks.isSculkBlock(Material.SCULK_CATALYST);
        var sensor = SculkBlocks.sculkSensorAt(player.getLocation());
    }

    static void text(SculkText text, Player player) {
        text.send(player, "welcome", Map.of("name", player.getName()));
        text.component(player, "greeting", Map.of("count", "3"));
    }

    static void config(SculkConfig config) {
        config.onReload(java.util.Map.class, () -> {
            // reloaded
        });
        config.watch(runnable -> {
            runnable.run();
        });
    }

    static void data(SculkData data, Player player) {
        UUID id = player.getUniqueId();
        SculkRepository<PlayerData, UUID> repo = data.repository(PlayerData.class, UUID.class);
        var cached = data.cached(repo, PlayerData::uuid, b -> {
            b.setMaxSize(500);
        });

        // Java repositories: suspend operations exposed as CompletableFutures.
        JavaRepository<PlayerData, UUID> jrepo = data.javaRepository(PlayerData.class, UUID.class);
        jrepo.find(id).thenAccept(result -> {
            PlayerData pd = result.getOrNull();   // null if absent or failed
            result.ifSuccess(v -> { /* loaded */ })
                  .ifFailure((msg, err) -> { /* log */ });
        });
        jrepo.save(new PlayerData(id, 10));

        // Java caches: base ops + findOrCreate / findTopBy / invalidate.
        JavaCache<PlayerData, UUID> jcache = data.javaCache(cached);
        jcache.findOrCreate(id, () -> new PlayerData(id, 0)).thenAccept(r -> { var v = r.getOrNull(); });
        jcache.findTopBy(10, PlayerData::coins).thenAccept(r -> { var top = r.getOrNull(); });
        jcache.invalidateAll();

        // Java player profiles: getOrCreate / save as CompletableFutures.
        JavaProfileStore<PlayerData, UUID> profiles = data.javaPlayerProfiles(repo, pid -> new PlayerData(pid, 0));
        profiles.getOrCreate(id).thenAccept(r -> { var v = r.getOrNull(); });

        // Raw transaction bridge.
        data.transactionAsync(conn -> null).thenAccept(result -> {
            boolean ok = result.isSuccess();
        });
    }

    static void styledBorder() {
        SculkGui.gui("<dark_gray>Rewards", menu -> {
            menu.setSize(36);
            SculkGuiSlots.border(menu, Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                GuiBorderStyle.Horizontal, java.util.Set.of(27, 31),
                slot -> {
                    slot.setName("<gray> ");
                });
        });
    }
}
