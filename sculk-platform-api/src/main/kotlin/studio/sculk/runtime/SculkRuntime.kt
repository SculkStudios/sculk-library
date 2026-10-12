package studio.sculk.runtime

import studio.sculk.SculkHandle
import studio.sculk.annotation.SculkStable
import studio.sculk.capability.Capabilities
import studio.sculk.diagnostics.DiagnosticReport
import studio.sculk.execution.SculkScope
import studio.sculk.lifecycle.Lifecycle
import studio.sculk.service.ServiceRegistry
import studio.sculk.time.SculkClock
import studio.sculk.version.MinecraftVersion

/**
 * One plugin's view of Sculk. Created by the platform when a plugin attaches; closed when it
 * disables. Everything the plugin creates through Sculk belongs to [scope] and closes with it.
 */
@SculkStable
public interface SculkRuntime : SculkHandle {
    /** The owning plugin's name. */
    public val name: String

    public val platform: PlatformInfo

    public val lifecycle: Lifecycle

    /** The plugin scope; coroutines and resources in it end when the plugin disables. */
    public val scope: SculkScope

    /** Fixed once [lifecycle] reaches READY. */
    public val capabilities: Capabilities

    public val services: ServiceRegistry

    public val clock: SculkClock

    public val log: SculkLog

    /**
     * True when the operator or the build enabled development checks. Ownership is then verified
     * on every guarded call and violations fail loudly; in production the checks cost a branch.
     */
    public val developmentMode: Boolean

    /** Installs [module] once; installing a module twice is a wiring bug and throws. */
    public fun install(module: SculkModule)

    /** What the runtime knows about itself, for support requests. Never sent anywhere automatically. */
    public fun diagnostics(): DiagnosticReport
}

/** The server or proxy the runtime is running on, detected once at startup. */
@SculkStable
public data class PlatformInfo(
    /** Paper, Folia, Canvas or Velocity. */
    public val kind: Kind,
    /** The brand and build the server reports, for diagnostics. */
    public val brand: String,
    /** The Minecraft version; null on a proxy, which serves several. */
    public val minecraft: MinecraftVersion?,
    public val javaVersion: Int = Runtime.version().feature(),
) {
    @SculkStable
    public enum class Kind {
        PAPER,
        FOLIA,
        CANVAS,
        VELOCITY,
        ;

        /** True for Folia and Canvas, where regions tick on separate threads. */
        public val isRegionized: Boolean get() = this == FOLIA || this == CANVAS

        public val isProxy: Boolean get() = this == VELOCITY
    }
}

/**
 * A feature contributed to a runtime: commands, menus, presentation. Installed explicitly; Sculk
 * never scans the classpath for modules.
 */
@SculkStable
public interface SculkModule {
    /** A stable identifier, such as `sculk-commands`. */
    public val id: String

    /** Called once, in the LOAD phase or later. Register services and lifecycle actions here. */
    public fun install(runtime: SculkRuntime)
}

/** Where Sculk writes what it has to say. Platforms adapt it to their logger. */
@SculkStable
public interface SculkLog {
    public fun info(message: String)

    public fun warn(message: String, cause: Throwable? = null)

    public fun error(message: String, cause: Throwable? = null)
}
