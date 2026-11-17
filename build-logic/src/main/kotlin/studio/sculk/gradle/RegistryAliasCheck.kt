package studio.sculk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Compares the registry keys each Paper anchor reported, oldest first, and fails when a key that
 * one anchor has is gone from the next without a line in `sculk-registry`'s `aliases.txt`, so the
 * aliases lookups follow are complete for every supported version. Writes what changed, with the
 * keys added alongside each removal as candidates for its new name.
 */
abstract class RegistryAliasCheck : DefaultTask() {
    /** One label per anchor, oldest first, in the order of [dumps]. */
    @get:Input
    abstract val labels: ListProperty<String>

    /** Each anchor's `registry key` lines, in the order of [labels]. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val dumps: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val aliases: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val covered = aliases.get().asFile.readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
            .map { line -> line.split(Regex("\\s+")).let { normalise(it[0]) to normalise(it[1]) } }
            .toSet()
        val names = labels.get()
        val files = dumps.files.toList()
        if (names.size != files.size) throw GradleException("${names.size} anchor labels for ${files.size} registry key dumps.")
        val dumps = names.zip(files).map { (label, file) ->
            if (!file.isFile) throw GradleException("$label wrote no registry keys at $file; run the boot suite first.")
            label to file.readLines().filter { it.isNotBlank() }.map { line -> line.split(' ', limit = 2).let { it[0] to it[1] } }.toSet()
        }
        val report = StringBuilder()
        val missing = mutableListOf<String>()
        dumps.zipWithNext().forEach { (older, newer) ->
            val removed = (older.second - newer.second).sortedWith(compareBy({ it.first }, { it.second }))
            val added = (newer.second - older.second)
            if (removed.isEmpty() && added.isEmpty()) return@forEach
            report.appendLine("${older.first} -> ${newer.first}: ${removed.size} removed, ${added.size} added")
            removed.forEach { (registry, key) ->
                val candidates = added.filter { it.first == registry && shares(it.second, key) }.map { it.second }.sorted()
                val known = (registry to key) in covered
                report.appendLine("  ${if (known) "covered" else "MISSING"} $registry $key" + candidates.joinToString(", ", prefix = "  candidates: ").takeIf { candidates.isNotEmpty() }.orEmpty())
                if (!known) missing += "$registry $key (${older.first} -> ${newer.first})" + candidates.joinToString(", ", prefix = "; maybe ").takeIf { candidates.isNotEmpty() }.orEmpty()
            }
        }
        this.report.get().asFile.apply { parentFile.mkdirs() }.writeText(report.toString())
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Registry keys disappear between supported versions without an alias. Add each to " +
                    "sculk-registry/src/main/resources/studio/sculk/registry/aliases.txt as renamed or removed:\n" +
                    missing.joinToString("\n") { "  $it" },
            )
        }
    }

    private fun normalise(key: String): String = if (':' in key) key else "minecraft:$key"

    // A rename usually keeps a distinctive word of the old path (chain -> iron_chain); category words
    // and common verbs say nothing about which entry it became.
    private fun shares(candidate: String, removed: String): Boolean {
        fun words(key: String) = key.substringAfter(':').split('.', '_', '/').filter { it.length > 2 && it !in GENERIC }.toSet()
        return (words(candidate) intersect words(removed)).isNotEmpty()
    }

    private companion object {
        val GENERIC = setOf("entity", "block", "item", "ambient", "break", "place", "death", "hurt", "step", "eat", "swim", "land")
    }
}
