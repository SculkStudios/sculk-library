package studio.sculk.config

import studio.sculk.annotation.SculkStable

/** Mutable YAML document passed to config migrations. */
@SculkStable
public class ConfigDocument internal constructor(
    internal val values: MutableMap<String, Any?>,
) {
    /** Returns the raw value at [key]. */
    public operator fun get(key: String): Any? = values[key]

    /** Sets [key] to [value]. */
    public operator fun set(
        key: String,
        value: Any?,
    ) {
        values[key] = value
    }

    /** Removes [key]. */
    public fun remove(key: String): Any? = values.remove(key)

    /** Renames [from] to [to] when the old key exists and the new key does not. */
    public fun rename(
        from: String,
        to: String,
    ) {
        if (from in values && to !in values) {
            values[to] = values.remove(from)
        }
    }

    /** Sets [key] only when missing. */
    public fun default(
        key: String,
        value: Any?,
    ) {
        values.putIfAbsent(key, value)
    }
}

/** Builder for versioned config migrations. */
@SculkStable
public class ConfigMigrationBuilder internal constructor() {
    internal val steps: MutableList<ConfigMigrationStep> = mutableListOf()

    /** Starts a migration at config version [version]. */
    public fun from(version: Int): ConfigMigrationFrom = ConfigMigrationFrom(version, steps)
}

/** Intermediate migration builder. */
@SculkStable
public class ConfigMigrationFrom internal constructor(
    private val fromVersion: Int,
    private val steps: MutableList<ConfigMigrationStep>,
) {
    /** Adds a migration from the source version to [version]. */
    public fun to(
        version: Int,
        block: ConfigDocument.() -> Unit,
    ) {
        require(version > fromVersion) { "Config migration target version must be greater than source version." }
        steps += ConfigMigrationStep(fromVersion, version, block)
    }
}

internal data class ConfigMigrationStep(
    val from: Int,
    val to: Int,
    val block: ConfigDocument.() -> Unit,
)
