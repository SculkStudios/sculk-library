package gg.sculk.platform.java

import gg.sculk.core.annotation.SculkStable
import gg.sculk.platform.SculkPlatform
import gg.sculk.platform.SculkPlatformBuilder
import org.bukkit.plugin.java.JavaPlugin
import java.util.function.Consumer

/**
 * Java-friendly factory for [SculkPlatform].
 *
 * ```java
 * SculkPlatform sculk = JavaSculkPlatform.create(this, cfg -> {
 *     cfg.gui();
 *     cfg.config();
 *     cfg.data();
 * });
 * ```
 */
@SculkStable
public object JavaSculkPlatform {
    /**
     * Creates a [SculkPlatform] configured via a [Consumer<SculkPlatformBuilder>].
     */
    @JvmStatic
    public fun create(
        plugin: JavaPlugin,
        configure: Consumer<SculkPlatformBuilder>,
    ): SculkPlatform = SculkPlatform.create(plugin) { configure.accept(this) }

    /** Creates a [SculkPlatform] with default configuration (no modules enabled). */
    @JvmStatic
    public fun create(plugin: JavaPlugin): SculkPlatform = SculkPlatform.create(plugin)
}
