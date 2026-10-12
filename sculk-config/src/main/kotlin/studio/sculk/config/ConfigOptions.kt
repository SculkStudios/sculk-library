package studio.sculk.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.snakeyaml.engine.v2.nodes.MappingNode
import studio.sculk.Secret
import studio.sculk.annotation.SculkStable

/** How a configuration file is read, upgraded and checked. */
@SculkStable
public class ConfigOptions<T : Any> internal constructor() {
    /**
     * The schema version this plugin writes. Raise it whenever the file changes shape; a [migration]
     * is needed only for a version whose keys move or change meaning, since new keys are added anyway.
     */
    public var version: Int = 1

    /** Lines written at the top of a newly generated file. */
    public var header: List<String> = emptyList()

    /**
     * How `${NAME}` and `${NAME:-default}` placeholders in values and map keys are resolved; the
     * process environment by default. `$${NAME}` is the literal text `${NAME}`, and text saved from
     * code is escaped that way, so it reads back exactly as it was saved.
     */
    public var environment: (String) -> String? = System::getenv

    /**
     * Called for each key the file has and the type does not read, such as a typo like `max-playres`,
     * after every successful load or reload. The key stays in the file. Does nothing by default.
     */
    public var onUnknownKey: (path: String, line: Int?) -> Unit = { _, _ -> }

    internal val migrations = sortedMapOf<Int, MigrationBuilder>()
    internal val validations = mutableListOf<Violations.(T) -> Unit>()

    /**
     * Upgrades a file at version [from] to `from + 1`. Migrations run in order on the file's own
     * tree before it is decoded, and the original file is backed up first.
     */
    public fun migration(from: Int, steps: MigrationBuilder.() -> Unit) {
        require(from >= 1) { "Config versions start at 1." }
        check(from !in migrations) { "There is already a migration from version $from." }
        migrations[from] = MigrationBuilder().apply(steps)
    }

    /**
     * Adds checks that run after decoding, on every load, reload and save; every violation reported
     * through [Violations.ensure] fails it, and so does an [IllegalArgumentException] from `require`.
     */
    public fun validate(rule: Violations.(T) -> Unit) {
        validations += rule
    }
}

/** Edits applied to the file's tree when upgrading. Paths are dotted file keys, such as `storage.mysql.port`. */
@SculkStable
public class MigrationBuilder internal constructor() {
    internal val steps = mutableListOf<(MappingNode) -> Unit>()

    /** Moves a key, keeping its value and comments. Does nothing if [from] is absent. */
    public fun rename(from: String, to: String) {
        steps += { root -> YamlTree.move(root, from, to) }
    }

    public fun remove(path: String) {
        steps += { root -> YamlTree.remove(root, path) }
    }

    /** Sets a scalar to [value] exactly as written, replacing what was there. */
    public fun set(path: String, value: String) {
        steps += { root -> YamlTree.set(root, path, YamlEncoding.scalar(value)) }
    }
}

/** Collects validation problems; see [ConfigOptions.validate]. */
@SculkStable
public class Violations internal constructor() {
    internal class Problem(val path: String, val expected: String, val received: String)

    internal val problems = mutableListOf<Problem>()

    /**
     * Reports the key at [path] unless [condition] holds: `ensure(port != 25565, "port", "a port other than the server's") { port }`.
     * [received] is evaluated only when it does not.
     */
    public fun ensure(condition: Boolean, path: String, expected: String, received: () -> Any?) {
        if (!condition) problems += Problem(path, expected, received().toString())
    }
}

/**
 * A [Secret] in a configuration file. Values typed this way are never echoed in errors:
 * `val password: ConfigSecret = Secret("")`. The default is written to a generated file, so it is a
 * placeholder such as an empty string, never a real credential.
 */
public typealias ConfigSecret =
    @Serializable(with = SecretSerializer::class)
    Secret

/** Reads and writes a [Secret] as plain text in the file and nowhere else. */
@SculkStable
public object SecretSerializer : KSerializer<Secret> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(NodeDecoder.SECRET, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Secret): Unit = encoder.encodeString(value.reveal())

    override fun deserialize(decoder: Decoder): Secret = Secret(decoder.decodeString())
}
