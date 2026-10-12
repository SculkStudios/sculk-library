package studio.sculk.paper

import org.bukkit.plugin.Plugin
import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.runtime.PlatformInfo
import studio.sculk.runtime.SculkLog

/**
 * Adds platform-specific capabilities to a Paper runtime, such as Canvas's regionized data.
 * Passed explicitly to [Sculk.attach]; nothing is discovered from the classpath.
 */
@SculkStable
public interface PaperPlatformExtension {
    /** A stable identifier for diagnostics, such as `sculk-canvas`. */
    public val id: String

    /**
     * Resolves this extension's capabilities. Called once during attach on every platform, so an
     * extension for another platform resolves its keys as unavailable rather than leaving them out.
     */
    public fun resolve(context: Context, capabilities: Capabilities.Builder)

    /** What an extension may use while resolving. */
    @SculkStable
    public class Context internal constructor(
        public val plugin: Plugin,
        public val platform: PlatformInfo,
        public val log: SculkLog,
        public val developmentMode: Boolean,
    )
}
