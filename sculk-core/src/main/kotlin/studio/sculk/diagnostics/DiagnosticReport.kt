package studio.sculk.diagnostics

import studio.sculk.annotation.SculkStable

/**
 * A plain-text report of what the runtime knows about itself: platform, versions, capabilities,
 * backends. Modules contribute sections; nothing in it is ever sent anywhere automatically.
 */
@SculkStable
public class DiagnosticReport private constructor(public val sections: List<Section>) {
    /** One titled block of aligned entries. */
    @SculkStable
    public class Section(public val title: String, entries: List<Pair<String, String>>) {
        public val entries: List<Pair<String, String>> = entries.toList()

        override fun toString(): String = "Section($title, $entries)"
    }

    /** Aligned, human-readable text suitable for a log or a pasted support request. */
    public fun render(): String = buildString {
        sections.forEachIndexed { index, section ->
            if (index > 0) appendLine()
            appendLine(section.title)
            val width = section.entries.maxOfOrNull { it.first.length } ?: 0
            section.entries.forEach { (key, value) -> appendLine("  ${key.padEnd(width)}  $value") }
        }
    }

    @SculkStable
    public class Builder {
        private val sections = mutableListOf<Section>()

        public fun section(title: String, block: SectionBuilder.() -> Unit): Builder = apply {
            sections += Section(title, SectionBuilder().apply(block).entries.toList())
        }

        public fun build(): DiagnosticReport = DiagnosticReport(sections.toList())
    }

    @SculkStable
    public class SectionBuilder internal constructor() {
        internal val entries = mutableListOf<Pair<String, String>>()

        public fun entry(key: String, value: Any?) {
            entries += key to value.toString()
        }
    }

    @SculkStable
    public companion object {
        public fun build(block: Builder.() -> Unit): DiagnosticReport = Builder().apply(block).build()
    }
}
