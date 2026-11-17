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
import studio.sculk.flatMap
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
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

    /**
     * Merges [value] into the file and writes it only if the merged file loads back as valid, and only
     * if the file still holds [lastWritten]: anything else is an operator's edit, refused as a [ConfigConflict].
     * A missing file is written anew.
     */
    fun <T : Any> save(
        path: Path,
        serializer: KSerializer<T>,
        options: ConfigOptions<T>,
        value: T,
        lastWritten: String,
    ): SculkResult<Loaded<T>> {
        val onDisk = guarded(path, "save") {
            try {
                path.readText()
            } catch (_: NoSuchFileException) {
                null
            }
        }
        return onDisk.flatMap { original ->
            if (original != null && original != lastWritten) return@flatMap ConfigConflict(path.name)
            guarded(path, "save") {
                val tree = original?.let(YamlTree::parse) ?: YamlTree.emptyMapping()
                YamlTree.merge(tree, YamlEncoding.encode(serializer, value)) { text ->
                    Placeholders.resolveLeniently(text, options.environment)
                }
                setVersion(tree, options.version, first = original == null)
                val saved = decode(tree, serializer, options, DecodeContext(path.name, options.environment))
                val text = YamlTree.print(tree)
                write(path, text)
                Loaded(saved, text)
            }
        }
    }

    /** [current] with each path in [changes] set to its text, decoded and checked as a load would; nothing is written. */
    fun <T : Any> edit(
        path: Path,
        serializer: KSerializer<T>,
        options: ConfigOptions<T>,
        current: T,
        changes: Map<String, String>,
    ): SculkResult<T> = guarded(path, "edit") {
        val tree = YamlEncoding.encode(serializer, current) as MappingNode
        changes.forEach { (key, text) -> YamlTree.set(tree, key, YamlEncoding.scalar(text)) }
        decode(tree, serializer, options, DecodeContext(path.name, options.environment))
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
            val problem = rejected.message?.trimEnd('.')?.let { "a problem: $it" } ?: "one a validation rule rejects"
            throw ConfigDecodingException(InvalidConfig(context.file, "", null, "a configuration the plugin accepts", problem))
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
     * replaced through [replace], so a crash leaves one or the other. Only a file no rename can
     * replace is rewritten in place, without that guarantee; any other failure leaves it untouched.
     */
    fun write(path: Path, text: String, replace: (from: Path, to: Path) -> Unit = ::replaceAtomically) {
        path.toAbsolutePath().parent?.let(Files::createDirectories)
        val temporary = path.resolveSibling(".${path.name}.tmp")
        try {
            writeDurably(temporary, text)
            try {
                replace(temporary, path)
            } catch (refused: FileSystemException) {
                if (!cannotRename(refused)) throw refused
                writeDurably(path, text)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun replaceAtomically(from: Path, to: Path) {
        Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    // A single-file bind mount (Docker) is a mount point: renaming onto it fails with EXDEV or EBUSY,
    // which the JDK reports as these two; EBUSY has no subtype, so only its reason tells it apart.
    private fun cannotRename(error: FileSystemException): Boolean {
        if (error is AtomicMoveNotSupportedException) return true
        return error.javaClass == FileSystemException::class.java && error.reason?.contains("busy", ignoreCase = true) == true
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
