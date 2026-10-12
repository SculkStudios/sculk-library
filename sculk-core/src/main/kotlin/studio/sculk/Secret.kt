package studio.sculk

import studio.sculk.annotation.SculkStable

/**
 * A value that must never appear in logs, failures, diagnostics or generated files. Every string
 * conversion prints a placeholder; only [reveal] returns the value.
 */
@SculkStable
@JvmInline
public value class Secret(private val value: String) {
    public fun reveal(): String = value

    public val isEmpty: Boolean get() = value.isEmpty()

    override fun toString(): String = REDACTED

    @SculkStable
    public companion object {
        public const val REDACTED: String = "<redacted>"
    }
}
