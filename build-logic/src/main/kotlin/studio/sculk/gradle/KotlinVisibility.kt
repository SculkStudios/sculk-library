package studio.sculk.gradle

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaModifier
import kotlin.metadata.Visibility
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.Metadata
import kotlin.metadata.visibility

/**
 * Declared Kotlin visibility, read from `@kotlin.Metadata`. Bytecode alone cannot tell public from
 * internal (both compile to public) or a declared type from a compiler-generated lambda class.
 */
internal object KotlinVisibility {
    sealed interface Facts

    /** A class or object the source declares; [isPublic] is the declared visibility. */
    data class Declared(val isPublic: Boolean) : Facts

    /** A file facade: the public top-level functions and properties it declares, by JVM name. */
    data class Facade(val functions: Set<String>, val properties: Set<String>) : Facts

    /** Compiler output with no declared visibility: lambdas, anonymous objects, `$WhenMappings`. */
    data object Generated : Facts

    fun of(type: JavaClass): Facts {
        val annotation = type.tryGetAnnotationOfType("kotlin.Metadata").orElse(null)
            ?: return Declared(type.modifiers.contains(JavaModifier.PUBLIC))
        val metadata = Metadata(
            kind = annotation.get("k").orElse(1) as Int,
            metadataVersion = annotation.get("mv").orElse(IntArray(0)) as IntArray,
            data1 = (annotation.get("d1").orElse(emptyArray<String>()) as Array<*>).map { it as String }.toTypedArray(),
            data2 = (annotation.get("d2").orElse(emptyArray<String>()) as Array<*>).map { it as String }.toTypedArray(),
            extraString = annotation.get("xs").orElse("") as String,
            packageName = annotation.get("pn").orElse("") as String,
            extraInt = annotation.get("xi").orElse(0) as Int,
        )
        return when (val parsed = KotlinClassMetadata.readLenient(metadata)) {
            is KotlinClassMetadata.Class -> Declared(parsed.kmClass.visibility == Visibility.PUBLIC || parsed.kmClass.visibility == Visibility.PROTECTED)
            is KotlinClassMetadata.FileFacade -> facade(parsed.kmPackage.functions, parsed.kmPackage.properties)
            is KotlinClassMetadata.MultiFileClassPart -> facade(parsed.kmPackage.functions, parsed.kmPackage.properties)
            else -> Generated
        }
    }

    private fun facade(functions: List<kotlin.metadata.KmFunction>, properties: List<kotlin.metadata.KmProperty>) = Facade(
        functions = functions.filter { it.visibility == Visibility.PUBLIC }.map { it.name }.toSet(),
        properties = properties.filter { it.visibility == Visibility.PUBLIC }.map { it.name }.toSet(),
    )
}
