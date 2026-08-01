package studio.sculk.platform

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import java.io.File

/**
 * Every published Sculk type says what it promises.
 *
 * CLAUDE.md rule 6 is a review convention, and review conventions decay — 412 of 814 declarations
 * had drifted unmarked before the rebuild. This makes it a build failure instead. `sculk-platform`
 * hosts it because it depends on every other module, so all of `studio.sculk` is on its classpath.
 *
 * The list of types comes from the committed `.api` dumps rather than from scanning class files.
 * Kotlin `internal` compiles to JVM `public`, so a raw scan reports every internal helper as an
 * unmarked API type; binary-compatibility-validator has already made exactly that distinction, and
 * the dumps are the same thing `apiCheck` gates on.
 *
 * Scoped to types, not members. Kotlin generates public members that cannot carry an annotation
 * (`component1`, `copy$default`, enum `values`), and the exclusion list to filter those costs more
 * than it buys. A file added without a marker is the decay that actually happens.
 */
class StabilityMarkerTest {
    private val markers = setOf(
        "Lstudio/sculk/annotation/SculkStable;",
        "Lstudio/sculk/annotation/SculkExperimental;",
        "Lstudio/sculk/annotation/SculkInternal;",
    )

    @Test
    fun `every type in the committed api dumps carries a stability marker`() {
        val unmarked = declaredApiTypes()
            .mapNotNull { name -> classBytes(name)?.let { name to it } }
            .filterNot { (_, bytes) -> isMarked(bytes) }
            .map { (name, _) -> name.replace('/', '.') }
            .sorted()

        assertTrue(
            unmarked.isEmpty(),
            "${unmarked.size} published types carry no @SculkStable/@SculkExperimental/@SculkInternal:\n" +
                unmarked.joinToString("\n") { "  $it" },
        )
    }

    @Test
    fun `the dumps and the classpath both resolved`() {
        // Either half silently finding nothing would make the check above pass forever, and one
        // already did: Gradle hands Windows test workers a manifest-only pathing jar, so walking
        // java.class.path found no classes at all and every type was skipped as unresolvable.
        val declared = declaredApiTypes()
        val resolved = declared.count { classBytes(it) != null }

        assertTrue(declared.size > 100, "only ${declared.size} types found across the .api dumps")
        assertTrue(
            resolved > declared.size / 2,
            "only $resolved of ${declared.size} declared types were found on the classpath",
        )
    }

    /** Type names declared across every module's committed dump, minus what cannot be annotated. */
    private fun declaredApiTypes(): List<String> = repoRoot().listFiles().orEmpty()
        .filter { it.isDirectory && it.name.startsWith("sculk-") }
        .flatMap { File(it, "api").listFiles().orEmpty().filter { file -> file.extension == "api" } }
        .flatMap { it.readLines() }
        .mapNotNull { CLASS_LINE.find(it)?.groupValues?.get(1) }
        // Kotlin's file facade for top-level declarations. There is no declaration to annotate:
        // the class exists only because the JVM has no top-level functions.
        .filterNot { it.endsWith("Kt") }
        .filterNot { name -> GENERATED.any { name.endsWith(it) } }
        .distinct()

    private fun isMarked(bytes: ByteArray): Boolean {
        var marked = false
        ClassReader(bytes).accept(
            object : ClassVisitor(Opcodes.ASM9) {
                override fun visitAnnotation(descriptor: String, visible: Boolean) = null.also {
                    if (descriptor in markers) marked = true
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return marked
    }

    /** Read through the class loader, which resolves the same way regardless of how Gradle delivers it. */
    private fun classBytes(internalName: String): ByteArray? =
        javaClass.classLoader.getResourceAsStream("$internalName.class")?.use { it.readBytes() }

    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("no settings.gradle.kts above ${System.getProperty("user.dir")}")
        }
        return dir
    }

    private companion object {
        val CLASS_LINE = Regex("""^public [\w\s]*?(?:class|interface) (studio/sculk/[\w$/]+)""")

        /**
         * Nested types the compiler writes, not the author.
         *
         * Only these suffixes — a blanket "skip anything nested" would also stop enforcing real
         * nested types like `SculkResult.Success`, which are exactly the kind of thing that gets
         * added later without a marker.
         */
        val GENERATED = listOf(
            "\$Companion",
            "\$DefaultImpls",
            // Kotlin's implementation class for an annotation class.
            "\$Impl",
            // kotlinx-serialization's generated serializer.
            "\$\$serializer",
            "\$WhenMappings",
        )
    }
}
