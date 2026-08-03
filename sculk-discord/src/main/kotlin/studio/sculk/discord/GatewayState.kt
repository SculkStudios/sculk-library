package studio.sculk.discord

import studio.sculk.annotation.SculkStable

/**
 * Where the connection is.
 *
 * [Degraded] is separate from [Disconnected] because they call for opposite responses: degraded means
 * a retry is already scheduled and the caller should fall back for now, while disconnected means
 * nothing is going to happen until something calls connect. Collapsing them is how a bot ends up
 * either hammering reconnects or waiting forever for one that was never scheduled.
 */
@SculkStable
public enum class GatewayState {
    /** No token configured, or the operator turned it off. Nothing will connect. */
    Disabled,

    /** Connecting, or reconnecting after a drop. */
    Connecting,

    /** Connected and usable. */
    Ready,

    /** Dropped, with a retry scheduled. Sends fail; fall back to a webhook if there is one. */
    Degraded,

    /** Shut down deliberately. Terminal. */
    Disconnected,
    ;

    /** True only when a send has a chance of arriving. */
    public val usable: Boolean get() = this == Ready
}
