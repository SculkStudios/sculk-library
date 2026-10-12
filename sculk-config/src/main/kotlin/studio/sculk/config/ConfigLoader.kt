package studio.sculk.config

import kotlinx.serialization.KSerializer
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.Tag
import studio.sculk.SculkFailure
import studio.sculk.SculkResult
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

/** Reading, upgrading, validating and writing one file. Every path ends in a [SculkResult]. */
internal object ConfigLoader {
    class Loaded<T>(val value: T, val text: String)

    fun <T : Any> read(path: Path, serializer: KSerializer<T>, options: ConfigOptions<T>): SculkResult<Loaded<T>> = guarded(path, "read") {
        val context = DecodeContext(path.name, options.environment)
        val loaded = if (path.exists()) upgrade(path, serializer, options, context) else generate(path, serializer, options, context)
        context.unknownKeys.forEach { options.onUnknownKey(it.path, it.line) }
        loaded
    }

    /** Merges [value] into the file and writes it only if the merged file loads back as valid. */
    fun <T : Any> save(path: Path, serializer: KSerializer<T>, options: ConfigOptions<T>, value: T): SculkResult<Loaded<T>> =
        guarded(path, "save") {
            val exists = path.exists()
            val tree = if (exists) YamlTree.parse(path.readText()) else YamlTree.emptyMapping()
            YamlTree.merge(tree, YamlEncoding.encode(serializer, value)) { text ->
                Placeholders.resolveLeniently(text, options.environment)
            }
            setVersion(tree, options.version, first = !exists)
            val saved = decode(tree, serializer, options, DecodeContext(path.name, options.environment))
            val text = YamlTree.print(tree)
            write(path, text)
            Loaded(saved, text)
        }

    private inline fun <T> guarded(path: Path, operation: String, block: () -> T): SculkResult<T> {
        val file = path.name
        return try {
            SculkResult.success(block())
        } catch (invalid: ConfigDecodingException) {
            val failure = invalid.failure
            InvalidConfig(file, failure.path, failure.line, failure.expected, failure.received, failure.hint)
        } catch (syntax: MarkedYamlEngineException) {
            // The exception's message quotes the offending line, which may hold a secret; only the problem is kept.
            val line = syntax.problemMark.map { it.line + 1 }.orElse(null)
            InvalidConfig(
                file,
                "",
                line,
                "valid YAML",
                syntax.problem ?: "a syntax error",
                "Check indentation and quoting around that line.",
            )
        } catch (syntax: YamlEngineException) {
            InvalidConfig(file, "", null, "valid YAML", syntax.message ?: "a syntax error")
        } catch (io: IOException) {
            SculkFailure.OperationFailed("$operation $file", io)
        }
    }

    private fun <T : Any> generate(path: Path, serializer: KSerializer<T>, options: ConfigOptions<T>, context: DecodeContext): Loaded<T> {
        val defaults = try {
            decode(YamlTree.emptyMapping(), serializer, options, context)
        } catch (invalid: ConfigDecodingException) {
            throw IllegalStateException(
                "${serializer.descriptor.serialName} needs a valid default for every property so ${path.name} can be generated: " +
                    invalid.failure.message,
                invalid,
            )
        }
        val tree = YamlEncoding.encode(serializer, defaults) as MappingNode
        setVersion(tree, options.version, first = true)
        tree.value.getOrNull(1)?.keyNode?.let { first -> first.blockComments = YamlEncoding.blankLine() + first.blockComments.orEmpty() }
        if (options.header.isNotEmpty()) {
            val version = tree.value.first().keyNode
            version.blockComments = YamlEncoding.comments(options.header) + YamlEncoding.blankLine() + version.blockComments.orEmpty()
        }
        val text = YamlTree.print(tree)
        write(path, text)
        return Loaded(defaults, text)
    }

    private fun <T : Any> upgrade(path: Path, serializer: KSerializer<T>, options: ConfigOptions<T>, context: DecodeContext): Loaded<T> {
        val original = path.readText()
        val tree = YamlTree.parse(original)
        val fileVersion = (YamlTree.get(tree, SculkConfig.VERSION_KEY) as? ScalarNode)?.value?.toIntOrNull() ?: 1
        if (fileVersion > options.version) {
            val line = tree.value.firstOrNull { YamlTree.keyOf(it) == SculkConfig.VERSION_KEY }?.keyNode?.line()
            val newer = "This file was written by a newer version of the plugin."
            throw ConfigDecodingException(
                InvalidConfig(path.name, SculkConfig.VERSION_KEY, line, "at most ${options.version}", "$fileVersion", newer),
            )
        }
        for (from in fileVersion until options.version) {
            options.migrations[from]?.steps?.forEach { step -> step(tree) }
        }
        val value = decode(tree, serializer, options, context)
        YamlTree.fillMissing(tree, YamlEncoding.encode(serializer, value) as MappingNode)
        setVersion(tree, options.version, first = false)
        val text = YamlTree.print(tree)
        if (text != original) {
            if (fileVersion < options.version) {
                Files.copy(path, path.resolveSibling("${path.name}.v$fileVersion.bak"), StandardCopyOption.REPLACE_EXISTING)
            }
            write(path, text)
        }
        return Loaded(value, text)
    }

    /** Decodes [tree] and runs every check a load runs: annotations while decoding, then the validation rules. */
    private fun <T : Any> decode(tree: MappingNode, serializer: KSerializer<T>, options: ConfigOptions<T>, context: DecodeContext): T {
        val value = ValueDecoder(context, tree, "", ignoredKeys = setOf(SculkConfig.VERSION_KEY)).decodeSerializableValue(serializer)
        val violations = Violations()
        try {
            options.validations.forEach { rule -> violations.rule(value) }
        } catch (rejected: IllegalArgumentException) {
            throw ConfigDecodingException(
                InvalidConfig(context.file, "", null, rejected.message ?: "a configuration the plugin accepts", "something else"),
            )
        }
        val first = violations.problems.firstOrNull() ?: return value
        val more = (violations.problems.size - 1).takeIf { it > 0 }?.let { "$it more problem(s) after this one." }
        throw ConfigDecodingException(
            InvalidConfig(context.file, first.path, YamlTree.find(tree, first.path)?.line(), first.expected, first.received, more),
        )
    }

    /** Sets the version key; a new one goes [first] in a new file and after the operator's keys otherwise. */
    private fun setVersion(tree: MappingNode, version: Int, first: Boolean) {
        val index = tree.value.indexOfFirst { YamlTree.keyOf(it) == SculkConfig.VERSION_KEY }
        val node = YamlEncoding.scalar(version.toString(), Tag.INT)
        if (index >= 0) {
            tree.value[index] = NodeTuple(tree.value[index].keyNode, node)
            return
        }
        val key = YamlEncoding.scalar(SculkConfig.VERSION_KEY).apply {
            blockComments = YamlEncoding.comments(listOf(VERSION_COMMENT), blankLineFirst = !first)
        }
        tree.value.add(if (first) 0 else tree.value.size, NodeTuple(key, node))
    }

    /**
     * Replaces [path] with [text] durably: the new contents reach the disk before the old file is
     * replaced, so a crash leaves one or the other.
     */
    fun write(path: Path, text: String) {
        path.toAbsolutePath().parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling(".${path.name}.tmp")
        try {
            writeDurably(temporary, text)
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (_: FileSystemException) {
            // A single-file bind mount (Docker) cannot be replaced by a rename, only rewritten in place.
            writeDurably(path, text)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun writeDurably(path: Path, text: String) {
        FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE).use { channel ->
            val buffer = Charsets.UTF_8.encode(text)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
    }

    private const val VERSION_COMMENT = "Used by the plugin to upgrade this file. Do not change it."
}
