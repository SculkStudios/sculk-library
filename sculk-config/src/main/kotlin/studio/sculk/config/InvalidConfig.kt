package studio.sculk.config

import studio.sculk.SculkFailure
import studio.sculk.annotation.SculkStable

/**
 * A configuration file that does not match its schema. Says which file, which key, which line,
 * what was expected and what was found, so a server owner can fix it without reading code:
 *
 * `config.yml: storage.mysql.port (line 12): expected an integer between 1 and 65535, found 90000.`
 */
@SculkStable
public class InvalidConfig(
    public val file: String,
    public val path: String,
    public val line: Int?,
    public val expected: String,
    public val received: String,
    override val hint: String? = null,
) : SculkFailure() {
    override val message: String
        get() {
            val where = if (path.isEmpty()) file else "$file: $path"
            val at = line?.let { " (line $it)" }.orEmpty()
            return "$where$at: expected $expected, found $received."
        }
}

/**
 * A save or update refused because [file] changed on disk since it was last read or written, so
 * writing would overwrite an operator's edit. Nothing was written and the value did not change.
 */
@SculkStable
public class ConfigConflict(public val file: String) : SculkFailure() {
    override val message: String get() = "$file changed on disk since it was last loaded, so it was not saved."
    override val hint: String get() = "Reload it to take the change, or fix it if the reload fails, then save again."
}

/** Thrown inside the decoder and converted to [InvalidConfig] at the boundary; never escapes the module. */
internal class ConfigDecodingException(val failure: InvalidConfig) : RuntimeException(failure.message, null, false, false)
