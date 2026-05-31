package studio.sculk.command

import studio.sculk.annotation.SculkStable

/** Structured result type for command execution and command-adjacent services. */
@SculkStable
public sealed interface CommandResult {
    public data object Success : CommandResult

    public data class Failure(
        public val message: String,
    ) : CommandResult
}
