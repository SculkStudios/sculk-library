package studio.sculk.text

import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Writes numbers compactly, `1.5k` or `2.34b`, and reads them back, for balances, prices and counts.
 *
 * Each suffix stands for the next power of 1,000; by default `k`, `m`, `b`, `t`, `qa`, `qi`, `sx`,
 * `sp`, `oc`, `no` and `dc`, thousand to decillion on the short scale. Formatting keeps at most
 * [maxFractionDigits] decimals and rounds with [rounding], toward zero by default, so a balance of
 * 1,999 shows as `1.99k` and never as more than it holds. Reading is exact: `1.5k` is 1500, with no
 * floating-point error. Immutable and thread-safe.
 */
@SculkExperimental
public class Amounts(
    suffixes: List<String> = DEFAULT_SUFFIXES,
    /** The most decimals a compact number shows; trailing zeros are dropped. */
    public val maxFractionDigits: Int = 2,
    /** How a compact number drops the decimals it does not show. */
    public val rounding: RoundingMode = RoundingMode.DOWN,
) {
    /** The suffix for 1,000, then 1,000,000, and so on, as written by [compact]. */
    public val suffixes: List<String> = suffixes.toList()

    private val tiers: Map<String, Int> = this.suffixes.withIndex().associate { (index, suffix) -> suffix.lowercase() to index + 1 }

    init {
        require(maxFractionDigits in 0..MAX_FRACTION_DIGITS) {
            "maxFractionDigits must be from 0 to $MAX_FRACTION_DIGITS, not $maxFractionDigits."
        }
        require(this.suffixes.all { SUFFIX.matches(it) }) { "Suffixes must be ASCII letters: ${this.suffixes}." }
        require(tiers.size == this.suffixes.size) { "Suffixes must differ ignoring case: ${this.suffixes}." }
    }

    /** [value] written compactly: `999`, `1.5k`, `-2.34m`. A non-finite double or float throws. */
    public fun compact(value: Number): String = compact(exact(value))

    /** [value] written compactly: `999`, `1.5k`, `-2.34m`. */
    public fun compact(value: BigDecimal): String {
        var shown = value.abs()
        var tier = 0
        while (tier < suffixes.size && shown >= THOUSAND) {
            shown = shown.movePointLeft(3)
            tier++
        }
        shown = shown.setScale(maxFractionDigits, rounding)
        // Rounding up can reach the next tier: 999.999k shown as 1000k reads better as 1m.
        if (shown >= THOUSAND && tier < suffixes.size) {
            shown = shown.movePointLeft(3).setScale(maxFractionDigits, rounding)
            tier++
        }
        val digits = shown.stripTrailingZeros().toPlainString()
        val sign = if (value.signum() < 0 && shown.signum() != 0) "-" else ""
        return sign + digits + (if (tier == 0) "" else suffixes[tier - 1])
    }

    /**
     * Reads a plain or compact number such as `250`, `1.5k`, `.5m` or `-2B`; suffixes match ignoring
     * case. Anything else, including grouping commas, spaces inside the number and unknown suffixes,
     * is an [SculkFailure.InvalidInput].
     */
    public fun parse(text: String): SculkResult<BigDecimal> {
        val match = NUMBER.matchEntire(text.trim()) ?: return invalid(text)
        val (digits, suffix) = match.destructured
        val tier = if (suffix.isEmpty()) 0 else tiers[suffix.lowercase()] ?: return invalid(text)
        return SculkResult.success(BigDecimal(digits).movePointRight(3 * tier))
    }

    private fun invalid(text: String): SculkResult<BigDecimal> =
        SculkFailure.InvalidInput("amount", "a number such as 250, 1.5k or 2m", "'$text'")

    @SculkExperimental
    public companion object {
        /** `k`, `m`, `b`, `t`, `qa`, `qi`, `sx`, `sp`, `oc`, `no`, `dc`: thousand to decillion. */
        public val DEFAULT_SUFFIXES: List<String> = listOf("k", "m", "b", "t", "qa", "qi", "sx", "sp", "oc", "no", "dc")

        // Declared before DEFAULT: companion properties initialise in order, and DEFAULT's init reads them.
        private const val MAX_FRACTION_DIGITS = 10
        private val THOUSAND = BigDecimal(1000)
        private val SUFFIX = Regex("[A-Za-z]+")
        private val NUMBER = Regex("""(-?(?:\d+(?:\.\d*)?|\.\d+))([A-Za-z]*)""")

        /** Amounts with the default suffixes, two decimals and rounding toward zero. */
        public val DEFAULT: Amounts = Amounts()

        // A double's shortest decimal form, not its binary expansion: 0.1 stays 0.1.
        private fun exact(value: Number): BigDecimal = when (value) {
            is BigDecimal -> value
            is BigInteger -> BigDecimal(value)
            is Long, is Int, is Short, is Byte -> BigDecimal.valueOf(value.toLong())
            is Double -> BigDecimal(value.also { require(it.isFinite()) { "Cannot write $it as an amount." } }.toString())
            is Float -> BigDecimal(value.also { require(it.isFinite()) { "Cannot write $it as an amount." } }.toString())
            else -> BigDecimal(value.toString())
        }
    }
}

/** This number written compactly with [Amounts.DEFAULT]: `1500.compact()` is `1.5k`. */
@SculkExperimental
public fun Number.compact(): String = Amounts.DEFAULT.compact(this)
