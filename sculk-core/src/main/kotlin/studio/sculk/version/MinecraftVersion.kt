package studio.sculk.version

import studio.sculk.annotation.SculkStable

/**
 * A Minecraft release or pre-release, in either the `1.21.4` or the year-based `26.3` format.
 *
 * The only place Sculk parses a version string. Ordering is numeric component by component, which
 * places every `26.x` release after every `1.x` release, and a pre-release before its release
 * (`26.3-pre-2 < 26.3-rc-1 < 26.3`). A missing patch component equals zero, so `1.21` and `1.21.0`
 * are the same version and both render as `1.21`.
 */
@SculkStable
public class MinecraftVersion private constructor(
    public val major: Int,
    public val minor: Int,
    public val patch: Int,
    public val preRelease: PreRelease?,
) : Comparable<MinecraftVersion> {
    /** A pre-release or release candidate of the version it is attached to. */
    @SculkStable
    public data class PreRelease(public val kind: Kind, public val number: Int) : Comparable<PreRelease> {
        init {
            require(number >= 1) { "A pre-release number starts at 1, but was $number." }
        }

        /** Declared in release order: every pre-release precedes every release candidate. */
        @SculkStable
        public enum class Kind(internal val token: String) {
            PRE("pre"),
            RC("rc"),
        }

        override fun compareTo(other: PreRelease): Int = compareValuesBy(this, other, PreRelease::kind, PreRelease::number)

        override fun toString(): String = "${kind.token}-$number"
    }

    public val isPreRelease: Boolean get() = preRelease != null

    /** The same version without its pre-release qualifier. */
    public fun release(): MinecraftVersion = if (preRelease == null) this else MinecraftVersion(major, minor, patch, null)

    override fun compareTo(other: MinecraftVersion): Int {
        val numeric = compareValuesBy(this, other, MinecraftVersion::major, MinecraftVersion::minor, MinecraftVersion::patch)
        if (numeric != 0) return numeric
        val mine = preRelease
        val theirs = other.preRelease
        return when {
            mine == null && theirs == null -> 0
            mine == null -> 1
            theirs == null -> -1
            else -> mine.compareTo(theirs)
        }
    }

    override fun equals(other: Any?): Boolean = other is MinecraftVersion && compareTo(other) == 0

    override fun hashCode(): Int = ((major * 31 + minor) * 31 + patch) * 31 + preRelease.hashCode()

    override fun toString(): String = buildString {
        append(major).append('.').append(minor)
        if (patch != 0) append('.').append(patch)
        preRelease?.let { append('-').append(it) }
    }

    @SculkStable
    public companion object {
        private val PATTERN = Regex("""(\d{1,4})\.(\d{1,4})(?:\.(\d{1,4}))?(?:-(pre|rc)-?(\d{1,4}))?""")

        /** Builds a version from its components; throws when a component is negative. */
        public fun of(major: Int, minor: Int, patch: Int = 0, preRelease: PreRelease? = null): MinecraftVersion {
            require(major >= 0 && minor >= 0 && patch >= 0) { "Version components cannot be negative: $major.$minor.$patch." }
            return MinecraftVersion(major, minor, patch, preRelease)
        }

        /**
         * Parses `1.21.4`, `26.3`, `26.3-pre-2` or `26.3-rc-1` (the hyphen before the number is
         * optional, matching both Mojang's and Paper's spellings). Returns null for anything else,
         * including snapshots such as `26w41a`, which have no place in a release ordering.
         */
        public fun parse(text: String): MinecraftVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, kind, number) = match.destructured
            val preRelease = kind.takeIf(String::isNotEmpty)?.let { token ->
                val parsedNumber = number.toInt()
                if (parsedNumber < 1) return null
                PreRelease(PreRelease.Kind.entries.first { it.token == token }, parsedNumber)
            }
            return MinecraftVersion(major.toInt(), minor.toInt(), patch.ifEmpty { "0" }.toInt(), preRelease)
        }
    }
}
