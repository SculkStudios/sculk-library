package studio.sculk.gradle

import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaMethodCall
import com.tngtech.archunit.core.domain.JavaModifier
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/** Enforces the module boundaries and code rules in MODULES.md on compiled classes. */
@CacheableTask
abstract class ArchitectureCheck : DefaultTask() {
    @get:Classpath
    abstract val classes: ConfigurableFileCollection

    @get:Input
    abstract val moduleName: Property<String>

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val imported = ClassFileImporter().importPaths(classes.files.filter { it.exists() }.map { it.toPath() })
        val module = moduleName.get()
        val violations = rules(module).mapNotNull { rule ->
            val result = rule.evaluate(imported)
            if (result.hasViolation()) result.failureReport.toString() else null
        }
        if (violations.isNotEmpty()) {
            throw GradleException("Architecture rules broken in $module:\n\n" + violations.joinToString("\n\n"))
        }
        report.get().asFile.writeText("ok: ${imported.size} classes in $module\n")
    }

    private fun rules(module: String): List<ArchRule> = listOfNotNull(
        noClasses().should().dependOnClassesThat().resideInAnyPackage(*ModuleRules.forbiddenPackages(module).toTypedArray())
            .because("MODULES.md assigns these packages to other modules")
            .allowEmptyShould(true),
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("org.bukkit.scheduler.BukkitScheduler")
            .because("Folia and Canvas throw on the Bukkit scheduler; use execution contexts")
            .allowEmptyShould(true),
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName("kotlinx.coroutines.GlobalScope")
            .because("every coroutine belongs to a resource scope")
            .allowEmptyShould(true),
        noClasses().should().callMethodWhere(RUN_BLOCKING)
            .because("blocking a thread on a coroutine stalls ticks and event loops")
            .allowEmptyShould(true),
        noClasses().should().callMethodWhere(SEQUENCED_REVERSED)
            .because("on Java 21 reversed() resolves to a live SequencedCollection view, not Kotlin's copy; use asReversed() on a copy")
            .allowEmptyShould(true),
        classes().that(PUBLIC_API).should(CARRY_ONE_STABILITY_MARKER)
            .because("API_POLICY.md requires every public declaration to state its stability")
            .allowEmptyShould(true),
        classes().that(FILE_FACADES).should(MARK_EVERY_TOP_LEVEL_MEMBER)
            .because("API_POLICY.md requires every public declaration to state its stability")
            .allowEmptyShould(true),
    )

    private companion object {
        val MARKERS = setOf("studio.sculk.annotation.SculkStable", "studio.sculk.annotation.SculkExperimental", "studio.sculk.annotation.SculkInternal")

        val RUN_BLOCKING = DescribedPredicate.describe<JavaMethodCall>("runBlocking") { call ->
            call.target.owner.name == "kotlinx.coroutines.BuildersKt" && call.target.name.startsWith("runBlocking")
        }

        val SEQUENCED_REVERSED = DescribedPredicate.describe<JavaMethodCall>("java.util reversed()") { call ->
            call.target.name == "reversed" && call.target.rawParameterTypes.isEmpty() && call.target.owner.packageName == "java.util"
        }

        val PUBLIC_API = DescribedPredicate.describe<JavaClass>("public API types") { type ->
            !type.modifiers.contains(JavaModifier.SYNTHETIC) && isDeclaredPublic(type)
        }

        val FILE_FACADES = DescribedPredicate.describe<JavaClass>("Kotlin file facades") { type ->
            KotlinVisibility.of(type) is KotlinVisibility.Facade
        }

        // A public property compiles to a getter, and its annotations land on a synthetic
        // `$annotations` method beside it; either carrying a marker marks the property.
        val MARK_EVERY_TOP_LEVEL_MEMBER = object : ArchCondition<JavaClass>("mark every public top-level function and property") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val facade = KotlinVisibility.of(item) as? KotlinVisibility.Facade ?: return
                val marked = item.methods.filter { method -> method.annotations.any { it.rawType.name in MARKERS } }.map { it.name }.toSet()
                facade.functions
                    .filterNot { name -> marked.any { it == name || it.startsWith("$name-") } }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${item.name}.$it carries no stability marker")) }
                facade.properties
                    .filterNot { name -> propertyMethodNames(name).any { it in marked } }
                    .forEach { events.add(SimpleConditionEvent.violated(item, "${item.name}.$it carries no stability marker")) }
            }
        }

        val CARRY_ONE_STABILITY_MARKER = object : ArchCondition<JavaClass>("carry exactly one stability marker") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val count = item.annotations.count { it.rawType.name in MARKERS }
                if (count != 1) {
                    events.add(SimpleConditionEvent.violated(item, "${item.name} carries $count stability markers"))
                }
            }
        }

        fun isDeclaredPublic(type: JavaClass): Boolean {
            val facts = KotlinVisibility.of(type)
            if (facts !is KotlinVisibility.Declared || !facts.isPublic) return false
            return type.enclosingClass.map(::isDeclaredPublic).orElse(true)
        }

        fun propertyMethodNames(name: String): List<String> {
            val getter = if (name.startsWith("is") && name.length > 2 && name[2].isUpperCase()) name else "get" + name.replaceFirstChar(Char::uppercaseChar)
            return listOf(getter, "$getter\$annotations", "$name\$annotations")
        }
    }
}
