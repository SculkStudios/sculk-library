package studio.sculk.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * Every whole-number sample CLDR lists for every locale's categories lands in that category. The
 * samples are data separate from the rule text the rules are generated from, so they check the
 * generator as well as the rules.
 */
class CldrPluralSamplesTest {
    private val plurals = Path.of(checkNotNull(System.getProperty("sculk.cldr.plurals")) { "The build passes the CLDR file." })

    @Test
    fun `every CLDR integer sample of every locale is in its category`() {
        val mismatches = mutableListOf<String>()
        var checked = 0
        samples().forEach { (locale, category, count) ->
            checked++
            val actual = PluralCategory.of(count, Locale.forLanguageTag(locale))
            if (actual != category) mismatches += "$locale $count: expected $category, got $actual"
        }
        assertTrue(checked > 5_000, "only $checked samples were read")
        assertEquals(emptyList<String>(), mismatches.take(20), "${mismatches.size} of $checked samples disagree")
    }

    @Test
    fun `a language CLDR does not list has only other`() {
        listOf(0L, 1L, 2L, 5L, 1_000_000L).forEach {
            assertEquals(PluralCategory.OTHER, PluralCategory.of(it, Locale.forLanguageTag("tlh")))
        }
    }

    @Test
    fun `negative counts take the category of their size`() {
        assertEquals(PluralCategory.ONE, PluralCategory.of(-1, Locale.ENGLISH))
        assertEquals(PluralCategory.OTHER, PluralCategory.of(Long.MIN_VALUE, Locale.ENGLISH))
    }

    private data class Sample(val locale: String, val category: PluralCategory, val count: Long)

    /** The `@integer` samples of the cardinal rules, skipping compact exponents (`1c6`) and the trailing ellipsis. */
    private fun samples(): List<Sample> {
        val text = Files.readString(plurals)
        val cardinal = text.substringAfter("\"plurals-type-cardinal\"")
        val block = Regex(""""([A-Za-z-]+)":\s*\{([^}]*)}""")
        val rule = Regex(""""pluralRule-count-(\w+)":\s*"([^"]*)"""")
        return block.findAll(cardinal).flatMap { locale ->
            rule.findAll(locale.groupValues[2]).flatMap { match ->
                val category = PluralCategory.valueOf(match.groupValues[1].uppercase())
                val integers = match.groupValues[2].substringAfter("@integer", "").substringBefore("@decimal")
                integers.split(',').map(String::trim)
                    .filter { it.isNotEmpty() && it.none { c -> c == 'c' || c == 'e' || c == '…' } }
                    .flatMap { item ->
                        val bounds = item.split('~').map(String::toLong)
                        (bounds.first()..bounds.last()).asSequence()
                    }
                    .map { Sample(locale.groupValues[1], category, it) }
            }
        }.toList()
    }
}
