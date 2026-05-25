package studio.sculk.core.command

import studio.sculk.core.annotation.SculkStable

/** Structured result type for command execution and command-adjacent services. */
@SculkStable
public sealed interface CommandResult {
    public data object Success : CommandResult

    public data class Failure(
        public val message: String,
    ) : CommandResult
}
