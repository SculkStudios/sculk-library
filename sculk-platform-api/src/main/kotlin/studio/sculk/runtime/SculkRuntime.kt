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

    /** The server or proxy this runtime runs on, detected once when the plugin attached. */
    public val platform: PlatformInfo

    /** The runtime's phase, and the actions registered to run as it advances. */
    public val lifecycle: Lifecycle

    /** The plugin scope; coroutines and resources in it end when the plugin disables. */
    public val scope: SculkScope

    /** Fixed once [lifecycle] reaches READY. */
    public val capabilities: Capabilities

    /** This plugin's services, one per type; never shared with another plugin. */
    public val services: ServiceRegistry

    /** The clock Sculk's time-based features read; a fake one in tests. */
    public val clock: SculkClock

    /** The plugin's log, adapted to the platform's logger. */
    public val log: SculkLog

    /**
     * True when the operator or the build enabled development checks ([DEVELOPMENT_PROPERTY]).
     * Only the checks modules have built so far run, such as the Canvas region-data callback budget;
     * the development thread checker that verifies ownership on guarded calls is planned (THREADING.md).
     */
    public val developmentMode: Boolean

    /** Installs [module] once; installing a module twice is a wiring bug and throws. */
    public fun install(module: SculkModule)

    /** What the runtime knows about itself, for support requests. Never sent anywhere automatically. */
    public fun diagnostics(): DiagnosticReport

    @SculkStable
    public companion object {
        /** System property that turns development checks on for every plugin on the JVM: `-Dsculk.development=true`. */
        public const val DEVELOPMENT_PROPERTY: String = "sculk.development"

        /** True when [DEVELOPMENT_PROPERTY] is `true`; the default every platform uses for [developmentMode]. */
        public fun developmentModeByDefault(): Boolean = System.getProperty(DEVELOPMENT_PROPERTY).toBoolean()
    }
}

/**
 * The server or proxy the runtime is running on, detected once at startup. A plain class rather
 * than a data class, so detection can learn a new fact in a minor release without breaking callers.
 */
@SculkStable
public class PlatformInfo(
    /** Paper, Folia, Canvas or Velocity. */
    public val kind: Kind,
    /** The brand and build the server reports, for diagnostics. */
    public val brand: String,
    /** The Minecraft version; null on a proxy, which serves several. */
    public val minecraft: MinecraftVersion?,
    public val javaVersion: Int = Runtime.version().feature(),
) {
    override fun equals(other: Any?): Boolean =
        other is PlatformInfo && other.kind == kind && other.brand == brand && other.minecraft == minecraft &&
            other.javaVersion == javaVersion

    override fun hashCode(): Int = listOf(kind, brand, minecraft, javaVersion).hashCode()

    override fun toString(): String = "PlatformInfo($kind, $brand, minecraft=${minecraft ?: "proxy"}, java=$javaVersion)"

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
    /** Logs routine information. */
    public fun info(message: String)

    /** Logs something unexpected that Sculk worked around, with the [cause] when there is one. */
    public fun warn(message: String, cause: Throwable? = null)

    /** Logs a failure that lost work or disabled a feature, with the [cause] when there is one. */
    public fun error(message: String, cause: Throwable? = null)
}
