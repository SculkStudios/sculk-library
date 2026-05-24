package studio.sculk.core.version

import studio.sculk.core.annotation.SculkStable

/**
 * Parsed Minecraft version that supports both legacy `1.x.y` versions and Mojang's
 * newer calendar-style versions such as `26.1.2`.
 */
@SculkStable
public data class MinecraftVersion(
    public val major: Int,
    public val minor: Int,
    public val patch: Int = 0,
    public val qualifier: String? = null,
) : Comparable<MinecraftVersion> {
    override fun compareTo(other: MinecraftVersion): Int =
        compareValuesBy(
            this,
            other,
            MinecraftVersion::major,
            MinecraftVersion::minor,
            MinecraftVersion::patch,
        )

    override fun toString(): String =
        buildString {
            append(major)
                .append('.')
                .append(minor)
                .append('.')
                .append(patch)
            if (qualifier != null) append('.').append(qualifier)
        }

    public companion object {
        private val versionPattern = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:\.(.+))?$""")

        /**
         * Parses a Minecraft version string such as `1.21.11`, `26.1`, or
         * `26.1.2.build.64-stable`.
         *
         * @throws IllegalArgumentException when [value] is not a supported version string.
         */
        @JvmStatic
        public fun parse(value: String): MinecraftVersion {
            val input = value.trim()
            val match =
                versionPattern.matchEntire(input)
                    ?: throw IllegalArgumentException("Unsupported Minecraft version: '$value'")

            return MinecraftVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 0,
                qualifier = match.groupValues[4].takeIf { it.isNotEmpty() },
            )
        }
    }
}

/**
 * Parses this string as a [MinecraftVersion].
 */
@SculkStable
public fun String.toMinecraftVersion(): MinecraftVersion = MinecraftVersion.parse(this)
