package studio.sculk.config

import studio.sculk.annotation.SculkStable

/**
 * The keys of a config file, before they are decoded.
 *
 * A migration runs on this rather than on the decoded object because the whole point is to fix a
 * file whose shape no longer matches the class — by then, decoding has already dropped the old key.
 */
@SculkStable
public class ConfigDocument internal constructor(internal val values: MutableMap<String, Any?>) {
    @SculkStable
    public operator fun get(key: String): Any? = values[key]

    @SculkStable
    public operator fun set(key: String, value: Any?) {
        values[key] = value
    }

    @SculkStable
    public fun remove(key: String): Any? = values.remove(key)

    /** Moves [from] to [to], leaving an existing [to] alone so a rerun cannot clobber it. */
    @SculkStable
    public fun rename(from: String, to: String) {
        if (from in values && to !in values) values[to] = values.remove(from)
    }

    /** Sets [key] only when it is absent. */
    @SculkStable
    public fun default(key: String, value: Any?) {
        values.putIfAbsent(key, value)
    }
}

/** Declares the versioned migrations for one config file. */
@SculkStable
public class ConfigMigrationBuilder internal constructor() {
    internal val steps: MutableList<ConfigMigrationStep> = mutableListOf()

    @SculkStable
    public fun from(version: Int): ConfigMigrationFrom = ConfigMigrationFrom(version, steps)
}

@SculkStable
public class ConfigMigrationFrom internal constructor(private val fromVersion: Int, private val steps: MutableList<ConfigMigrationStep>) {
    @SculkStable
    public fun to(version: Int, block: ConfigDocument.() -> Unit) {
        require(version > fromVersion) { "A migration must move forwards: $fromVersion to $version." }
        steps += ConfigMigrationStep(fromVersion, version, block)
    }
}

internal data class ConfigMigrationStep(val from: Int, val to: Int, val block: ConfigDocument.() -> Unit)
