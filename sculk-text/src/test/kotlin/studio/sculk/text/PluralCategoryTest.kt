package studio.sculk.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.text.PluralCategory.FEW
import studio.sculk.text.PluralCategory.MANY
import studio.sculk.text.PluralCategory.ONE
import studio.sculk.text.PluralCategory.OTHER
import studio.sculk.text.PluralCategory.TWO
import studio.sculk.text.PluralCategory.ZERO
import java.util.Locale

/** Samples are the whole-number examples from the CLDR plural rules chart. */
class PluralCategoryTest {
    private fun assertCategories(language: String, vararg samples: Pair<PluralCategory, List<Long>>) {
        val locale = Locale.forLanguageTag(language)
        for ((category, counts) in samples) {
            counts.forEach { count -> assertEquals(category, PluralCategory.of(count, locale), "$language $count") }
        }
    }

    @Test
    fun `English, Dutch and German have one and other`() {
        listOf("en", "nl", "de").forEach { language ->
            assertCategories(language, ONE to listOf(1), OTHER to listOf(0, 2, 11, 21, 1_000_000))
        }
    }

    @Test
    fun `French counts zero as one and round millions as many`() {
        assertCategories("fr", ONE to listOf(0, 1), MANY to listOf(1_000_000, 2_000_000), OTHER to listOf(2, 17, 1_000_001))
    }

    @Test
    fun `Spanish and Italian count round millions as many`() {
        listOf("es", "it").forEach { language ->
            assertCategories(language, ONE to listOf(1), MANY to listOf(1_000_000), OTHER to listOf(0, 2, 1_500_000))
        }
    }

    @Test
    fun `Brazilian Portuguese counts zero as one and European Portuguese does not`() {
        assertCategories("pt", ONE to listOf(0, 1), MANY to listOf(1_000_000), OTHER to listOf(2))
        assertCategories("pt-PT", ONE to listOf(1), MANY to listOf(1_000_000), OTHER to listOf(0, 2))
    }

    @Test
    fun `Polish has one, few and many`() {
        assertCategories("pl", ONE to listOf(1), FEW to listOf(2, 4, 22, 102), MANY to listOf(0, 5, 11, 12, 14, 21, 112))
    }

    @Test
    fun `Russian and Ukrainian have one, few and many`() {
        listOf("ru", "uk").forEach { language ->
            assertCategories(language, ONE to listOf(1, 21, 101), FEW to listOf(2, 22), MANY to listOf(0, 5, 11, 12, 111))
        }
    }

    @Test
    fun `Czech has one and few for whole numbers`() {
        assertCategories("cs", ONE to listOf(1), FEW to listOf(2, 4), OTHER to listOf(0, 5, 22))
    }

    @Test
    fun `Arabic has all six categories`() {
        assertCategories(
            "ar",
            ZERO to listOf(0),
            ONE to listOf(1),
            TWO to listOf(2),
            FEW to listOf(3, 10, 103),
            MANY to listOf(11, 99, 111),
            OTHER to listOf(100, 102),
        )
    }

    @Test
    fun `negative counts take the category of their magnitude`() {
        assertEquals(ONE, PluralCategory.of(-1, Locale.ENGLISH))
        assertEquals(FEW, PluralCategory.of(-3, Locale.forLanguageTag("pl")))
    }

    @Test
    fun `a language without a table uses English's rule`() {
        assertCategories("sv", ONE to listOf(1), OTHER to listOf(0, 2))
    }

    @Test
    fun `the suffix is the category's lower-case name`() {
        assertEquals("few", FEW.suffix)
    }
}
