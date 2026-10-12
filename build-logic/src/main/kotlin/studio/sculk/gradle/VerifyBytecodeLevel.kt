package studio.sculk.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.DataInputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * Fails when any class in [archives] targets a newer class-file version than [maxJavaVersion].
 *
 * Compiler flags fence the API; this fences the output, catching a module that forgot the
 * convention or a dependency task that compiled with its own settings.
 */
@CacheableTask
abstract class VerifyBytecodeLevel : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archives: ConfigurableFileCollection

    @get:Input
    abstract val maxJavaVersion: Property<Int>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun verify() {
        val maxMajor = maxJavaVersion.get() + CLASS_FILE_OFFSET
        val offenders = archives.files.filter(File::isFile).flatMap { jar -> offendersIn(jar, maxMajor) }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "Classes target a Java version above ${maxJavaVersion.get()}:\n" + offenders.joinToString("\n") { "  $it" },
            )
        }
        report.get().asFile.writeText("ok: ${archives.files.size} archives at or below Java ${maxJavaVersion.get()}\n")
    }

    private fun offendersIn(jar: File, maxMajor: Int): List<String> = ZipFile(jar).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.endsWith(".class") && !it.name.startsWith("META-INF/versions/") }
            .mapNotNull { entry ->
                val major = DataInputStream(zip.getInputStream(entry)).use { input ->
                    input.skipNBytes(MAJOR_VERSION_OFFSET)
                    input.readUnsignedShort()
                }
                if (major > maxMajor) "${jar.name}!${entry.name} (Java ${major - CLASS_FILE_OFFSET})" else null
            }
            .toList()
    }

    private companion object {
        const val CLASS_FILE_OFFSET = 44
        const val MAJOR_VERSION_OFFSET = 6L
    }
}
