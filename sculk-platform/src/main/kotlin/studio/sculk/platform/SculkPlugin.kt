package studio.sculk.platform

import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.annotation.SculkStable

/**
 * A `JavaPlugin` base class that removes all Sculk lifecycle boilerplate.
 *
 * Pass the subsystems you want to the constructor, then put your registration in [setup] and any
 * teardown in [shutdown]. The platform is created before [setup] runs and closed after [shutdown],
 * so you never write `onEnable`/`onDisable` or manage [sculk] yourself.
 *
 * ```kotlin
 * class MyPlugin : SculkPlugin({ gui(); data() }) {
 *     override fun setup() {
 *         sculk.commands.register(command("hi") { player { reply("<green>hi") } })
 *     }
 * }
 * ```
 *
 * Prefer this over extending `JavaPlugin` directly unless you need full control of the enable/disable
 * sequence — in which case use [SculkPlatform.create] manually.
 */
@SculkStable
public abstract class SculkPlugin(private val configure: SculkPlatformBuilder.() -> Unit = {}) : JavaPlugin() {
    /**
     * Java-friendly constructor taking a [java.util.function.Consumer] of the platform builder.
     *
     * ```java
     * public final class MyPlugin extends SculkPlugin {
     *     public MyPlugin() { super(b -> { b.gui(); b.data(); }); }
     *     @Override protected void setup() { /* register commands/listeners */ }
     * }
     * ```
     */
    public constructor(configure: java.util.function.Consumer<SculkPlatformBuilder>) : this({ configure.accept(this) })

    /** Java-friendly no-subsystem constructor. Equivalent to enabling nothing in the builder. */
    public constructor() : this({})

    /** The Sculk platform for this plugin. Available from [setup] onward. */
    public lateinit var sculk: SculkPlatform
        private set

    final override fun onEnable() {
        sculk = SculkPlatform.create(this, configure)
        setup()
    }

    final override fun onDisable() {
        if (::sculk.isInitialized) {
            shutdown()
            sculk.close()
        }
    }

    /**
     * Called once after the platform is created. Register commands and listeners, load configs, and
     * wire up your plugin here.
     */
    protected open fun setup() {}

    /**
     * Called once before the platform is closed. Flush data and save state here. The platform (and
     * its coroutine scope, data pool, etc.) is still available; it is closed immediately after.
     */
    protected open fun shutdown() {}
}
