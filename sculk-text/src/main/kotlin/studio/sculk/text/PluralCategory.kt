package studio.sculk.text

import studio.sculk.annotation.SculkStable
import java.util.Locale

/**
 * The CLDR plural categories. A translation names its forms by [suffix]: `coins.one`, `coins.few`,
 * `coins.other`.
 */
@SculkStable
public enum class PluralCategory {
    ZERO,
    ONE,
    TWO,
    FEW,
    MANY,
    OTHER,
    ;

    /** The lower-case name a translation key ends with for this form: `one` in `coins.one`. */
    public val suffix: String = name.lowercase()

    @SculkStable
    public companion object {
        /**
         * The category of the whole number [count] in [locale], by the CLDR cardinal rules for the
         * locale's language and region where CLDR distinguishes them (`pt-PT`), else its language.
         * A language CLDR does not list has only [OTHER]. The sign of [count] is ignored.
         */
        public fun of(count: Long, locale: Locale): PluralCategory {
            // Long.MIN_VALUE has no positive counterpart; every CLDR rule treats it like MAX_VALUE.
            val n = if (count == Long.MIN_VALUE) Long.MAX_VALUE else kotlin.math.abs(count)
            return CldrPluralRules.category(locale.toLanguageTag(), n)
                ?: CldrPluralRules.category(locale.language, n)
                ?: OTHER
        }
    }
}
