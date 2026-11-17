package studio.sculk.world

import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkExperimental

/** Another plugin cancelled [change] from an event listener; the world is as it was. */
@SculkExperimental
public class ChangeCancelled(public val change: String) : SculkFailure() {
    override val message: String get() = "Another plugin cancelled $change."

    override val hint: String get() = "Look for a plugin that listens to WorldGameRuleChangeEvent and cancels it."
}
