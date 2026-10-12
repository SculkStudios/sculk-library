package studio.sculk.text

import studio.sculk.annotation.SculkStable
import java.util.Locale
import kotlin.math.absoluteValue

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

    public val suffix: String get() = name.lowercase()

    @SculkStable
    public companion object {
        /**
         * The category of the whole number [count] in [locale]'s language, by the CLDR rules for
         * Arabic, Belarusian, Czech, Dutch, English, French, German, Italian, Polish, Portuguese
         * (Brazilian, and European for `pt_PT`), Russian, Slovak, Spanish and Ukrainian. Other
         * languages use English's rule.
         */
        public fun of(count: Long, locale: Locale): PluralCategory {
            val n = count.absoluteValue
            return when (locale.language) {
                "ar" -> arabic(n)
                "cs", "sk" -> czech(n)
                "fr" -> if (n <= 1) ONE else millions(n)
                "pt" -> if (n == 1L || (n == 0L && locale.country != "PT")) ONE else millions(n)
                "es", "it" -> if (n == 1L) ONE else millions(n)
                "pl" -> polish(n)
                "ru", "uk", "be" -> eastSlavic(n)
                else -> if (n == 1L) ONE else OTHER
            }
        }

        private fun millions(n: Long): PluralCategory = if (n != 0L && n % MILLION == 0L) MANY else OTHER

        private fun arabic(n: Long): PluralCategory = when {
            n == 0L -> ZERO
            n == 1L -> ONE
            n == 2L -> TWO
            n % 100 in 3..10 -> FEW
            n % 100 in 11..99 -> MANY
            else -> OTHER
        }

        private fun czech(n: Long): PluralCategory = when (n) {
            1L -> ONE
            in 2..4 -> FEW
            else -> OTHER
        }

        private fun polish(n: Long): PluralCategory = when {
            n == 1L -> ONE
            n % 10 in 2..4 && n % 100 !in 12..14 -> FEW
            else -> MANY
        }

        private fun eastSlavic(n: Long): PluralCategory = when {
            n % 10 == 1L && n % 100 != 11L -> ONE
            n % 10 in 2..4 && n % 100 !in 12..14 -> FEW
            else -> MANY
        }

        private const val MILLION = 1_000_000L
    }
}
