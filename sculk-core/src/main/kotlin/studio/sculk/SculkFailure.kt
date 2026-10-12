package studio.sculk

import studio.sculk.annotation.SculkStable
import kotlin.time.Duration

/**
 * An expected failure. Every failure answers what happened ([message]), what can be done about it
 * ([hint]) and what caused it ([cause]); subtypes add where, what was expected and what was received.
 *
 * Modules define their own subtypes (a database failure, an invalid config file). [message] and
 * [hint] are shown to server owners, so they never contain secrets; wrap sensitive values in
 * [Secret] before they reach a failure.
 */
@SculkStable
public abstract class SculkFailure : SculkResult<Nothing> {
    public abstract val message: String
    public open val hint: String? get() = null
    public open val cause: Throwable? get() = null

    /** The full sentence a log line or a chat message shows. */
    public fun describe(): String = hint?.let { "$message $it" } ?: message

    override fun toString(): String = "${this::class.simpleName}(${describe()})"

    /** A feature the running server does not provide. */
    @SculkStable
    public class UnsupportedCapability(public val capability: String, public val reason: String, override val hint: String? = null) :
        SculkFailure() {
        override val message: String get() = "$capability is not available on this server: $reason."
    }

    /** An optional plugin or library Sculk integrates with is not installed or not enabled. */
    @SculkStable
    public class MissingIntegration(
        public val integration: String,
        override val hint: String? = "Install and enable $integration, then restart the server.",
    ) : SculkFailure() {
        override val message: String get() = "$integration is not installed or not enabled."
    }

    /** Input from a player, a file or the network that does not have the expected shape. */
    @SculkStable
    public class InvalidInput(
        public val location: String,
        public val expected: String,
        public val received: String,
        override val hint: String? = null,
    ) : SculkFailure() {
        override val message: String get() = "$location: expected $expected, found $received."
    }

    /** An operation that did not complete in time. */
    @SculkStable
    public class Timeout(public val operation: String, public val limit: Duration, override val hint: String? = null) : SculkFailure() {
        override val message: String get() = "$operation did not complete within $limit."
    }

    /** An operation that threw; produced by [SculkResult.catching]. */
    @SculkStable
    public class OperationFailed(public val operation: String, override val cause: Throwable, override val hint: String? = null) :
        SculkFailure() {
        override val message: String get() = "Could not $operation: ${cause.message ?: cause::class.simpleName}."
    }
}
