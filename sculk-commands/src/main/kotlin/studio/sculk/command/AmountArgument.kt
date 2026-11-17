package studio.sculk.command

import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.text.Amounts
import java.math.BigDecimal

/**
 * An amount written plainly or compactly, `250`, `1.5k` or `2m`, read exactly by [amounts] and kept
 * from [min] to [max]. It travels as one word, so the client accepts it as typed and the server reads it.
 */
@SculkExperimental
public class AmountArgument internal constructor(public val min: BigDecimal?, public val max: BigDecimal?, public val amounts: Amounts) :
    ArgumentType<BigDecimal> {
    override val shape: ArgumentShape get() = ArgumentShape.WORD

    override val expected: String
        get() = "an amount such as 250, 1.5k or 2m" + when {
            min != null && max != null -> " from ${amounts.compact(min)} to ${amounts.compact(max)}"
            min != null -> " of at least ${amounts.compact(min)}"
            max != null -> " of at most ${amounts.compact(max)}"
            else -> ""
        }

    override fun parse(text: String, source: CommandSource): SculkResult<BigDecimal> {
        val value = (amounts.parse(text) as? SculkResult.Success)?.value
        val inRange = value != null && (min == null || value >= min) && (max == null || value <= max)
        return if (inRange) SculkResult.success(value) else SculkFailure.InvalidInput("argument", expected, "'$text'")
    }
}
