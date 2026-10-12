package studio.sculk.gradle

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.zip.ZipFile

/**
 * Scans one `paper-api` jar per anchor and writes every public class whose presence, kind or
 * experimental status is not the same on all anchors, and for the classes in [memberProbes], every
 * public method and field that differs. Anything identical everywhere is omitted: it needs no
 * capability.
 *
 * `ApiStatus` annotations have class retention, so bytecode is the only place they can be read.
 */
@CacheableTask
abstract class CompatibilityScan : DefaultTask() {
    @get:Input
    abstract val anchorNames: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val anchorJars: ConfigurableFileCollection

    /** Classes whose members are compared one by one, such as `io.papermc.paper.command.brigadier.argument.ArgumentTypes`. */
    @get:Input
    abstract val memberProbes: ListProperty<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun scan() {
        val names = anchorNames.get()
        val jars = anchorJars.files.toList()
        if (names.size != jars.size) throw GradleException("Expected ${names.size} anchor jars, resolved ${jars.size}: $jars")
        val probes = memberProbes.get().toSet()

        val scans = names.zip(jars).associate { (name, jar) -> name to scanJar(jar, probes) }
        val allClasses = scans.values.flatMap { it.keys }.toSortedSet()
        val varying = allClasses.mapNotNull { className ->
            val perAnchor = names.map { scans.getValue(it)[className]?.withoutMembers() }
            if (perAnchor.distinct().size == 1) return@mapNotNull null
            className to mapOf(
                "present" to names.filterIndexed { i, _ -> perAnchor[i] != null },
                "kind" to names.zip(perAnchor).mapNotNull { (n, c) -> c?.let { n to it.kind } }.toMap().collapse(),
                "experimental" to names.filterIndexed { i, _ -> perAnchor[i]?.experimental == true },
                "deprecated" to names.filterIndexed { i, _ -> perAnchor[i]?.deprecated == true },
            )
        }.toMap()
        val members = probes.sorted().associateWith { probe -> varyingMembers(names) { scans.getValue(it)[probe]?.members.orEmpty() } }
        val missingProbes = probes.filter { probe -> scans.values.none { probe in it } }
        if (missingProbes.isNotEmpty()) throw GradleException("Probe classes exist on no anchor: $missingProbes")

        val document = mapOf(
            "schema" to 2,
            "anchors" to names.map { name -> mapOf("minecraft" to name, "classes" to scans.getValue(name).size) },
            "classes" to varying,
            "members" to members.filterValues { it.isNotEmpty() },
        )
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(document)) + "\n")
        logger.lifecycle(
            "Compatibility scan: ${allClasses.size} classes across ${names.size} anchors, ${varying.size} vary; " +
                "${members.values.sumOf { it.size }} members of ${probes.size} probed classes vary",
        )
    }

    private fun varyingMembers(names: List<String>, membersOf: (String) -> Map<String, MemberFacts>): Map<String, Any> {
        val perAnchor = names.associateWith(membersOf)
        return perAnchor.values.flatMap { it.keys }.toSortedSet().mapNotNull { member ->
            val facts = names.map { perAnchor.getValue(it)[member] }
            if (facts.distinct().size == 1) return@mapNotNull null
            member to mapOf(
                "present" to names.filterIndexed { i, _ -> facts[i] != null },
                "experimental" to names.filterIndexed { i, _ -> facts[i]?.experimental == true },
                "deprecated" to names.filterIndexed { i, _ -> facts[i]?.deprecated == true },
            )
        }.toMap()
    }

    private fun Map<String, String>.collapse(): Any = if (values.distinct().size == 1) values.first() else this

    private data class MemberFacts(val experimental: Boolean, val deprecated: Boolean)

    private data class ClassFacts(val kind: String, val experimental: Boolean, val deprecated: Boolean, val members: Map<String, MemberFacts>) {
        fun withoutMembers(): ClassFacts = copy(members = emptyMap())
    }

    private fun scanJar(jar: File, probes: Set<String>): Map<String, ClassFacts> = ZipFile(jar).use { zip ->
        zip.entries().asSequence()
            .filter { it.name.endsWith(".class") && !it.name.endsWith("module-info.class") && !it.name.endsWith("package-info.class") }
            .mapNotNull { entry -> zip.getInputStream(entry).use { readFacts(ClassReader(it), probes) } }
            .toMap()
    }

    private fun readFacts(reader: ClassReader, probes: Set<String>): Pair<String, ClassFacts>? {
        var facts: ClassFacts? = null
        var name = ""
        reader.accept(
            object : ClassVisitor(Opcodes.ASM9) {
                var access = 0
                val status = Status()
                val members = HashMap<String, MemberFacts>()
                val probed get() = name in probes

                override fun visit(version: Int, access: Int, className: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                    this.access = access
                    name = className.replace('/', '.')
                }

                override fun visitAnnotation(descriptor: String, visible: Boolean): AnnotationVisitor? {
                    status.read(descriptor)
                    return null
                }

                override fun visitMethod(access: Int, methodName: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                    if (!probed || !isPublicApi(access)) return null
                    val memberStatus = Status(deprecated = access and Opcodes.ACC_DEPRECATED != 0)
                    return object : MethodVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(annotation: String, visible: Boolean): AnnotationVisitor? {
                            memberStatus.read(annotation)
                            return null
                        }

                        override fun visitEnd() {
                            members["$methodName$descriptor"] = memberStatus.facts()
                        }
                    }
                }

                override fun visitField(access: Int, fieldName: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                    if (!probed || !isPublicApi(access)) return null
                    val memberStatus = Status(deprecated = access and Opcodes.ACC_DEPRECATED != 0)
                    return object : FieldVisitor(Opcodes.ASM9) {
                        override fun visitAnnotation(annotation: String, visible: Boolean): AnnotationVisitor? {
                            memberStatus.read(annotation)
                            return null
                        }

                        override fun visitEnd() {
                            members[fieldName] = memberStatus.facts()
                        }
                    }
                }

                override fun visitEnd() {
                    if (!isPublicApi(access)) return
                    val kind = when {
                        access and Opcodes.ACC_ANNOTATION != 0 -> "annotation"
                        access and Opcodes.ACC_ENUM != 0 -> "enum"
                        access and Opcodes.ACC_INTERFACE != 0 -> "interface"
                        else -> "class"
                    }
                    facts = ClassFacts(kind, status.experimental, status.deprecated || access and Opcodes.ACC_DEPRECATED != 0, members.toMap())
                }
            },
            ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES,
        )
        return facts?.let { name to it }
    }

    private fun isPublicApi(access: Int): Boolean = access and Opcodes.ACC_PUBLIC != 0 && access and Opcodes.ACC_SYNTHETIC == 0

    private class Status(var experimental: Boolean = false, var deprecated: Boolean = false) {
        fun read(descriptor: String) {
            when (descriptor) {
                "Lorg/jetbrains/annotations/ApiStatus\$Experimental;" -> experimental = true
                "Ljava/lang/Deprecated;" -> deprecated = true
            }
        }

        fun facts(): MemberFacts = MemberFacts(experimental, deprecated)
    }
}
