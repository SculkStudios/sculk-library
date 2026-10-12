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
        classes().that(PUBLIC_API).should(CARRY_ONE_STABILITY_MARKER)
            .because("API_POLICY.md requires every public declaration to state its stability")
            .allowEmptyShould(true),
    )

    private companion object {
        val MARKERS = setOf("studio.sculk.annotation.SculkStable", "studio.sculk.annotation.SculkExperimental", "studio.sculk.annotation.SculkInternal")

        val RUN_BLOCKING = DescribedPredicate.describe<JavaMethodCall>("runBlocking") { call ->
            call.target.owner.name == "kotlinx.coroutines.BuildersKt" && call.target.name.startsWith("runBlocking")
        }

        val PUBLIC_API = DescribedPredicate.describe<JavaClass>("public API types") { type ->
            type.modifiers.contains(JavaModifier.PUBLIC) &&
                !type.modifiers.contains(JavaModifier.SYNTHETIC) &&
                !type.simpleName.endsWith("Kt") &&
                !type.name.contains("\$DefaultImpls") &&
                !type.name.contains("\$WhenMappings") &&
                type.enclosingClass.map { it.modifiers.contains(JavaModifier.PUBLIC) }.orElse(true)
        }

        val CARRY_ONE_STABILITY_MARKER = object : ArchCondition<JavaClass>("carry exactly one stability marker") {
            override fun check(item: JavaClass, events: ConditionEvents) {
                val count = item.annotations.count { it.rawType.name in MARKERS }
                if (count != 1) {
                    events.add(SimpleConditionEvent.violated(item, "${item.name} carries $count stability markers"))
                }
            }
        }
    }
}
