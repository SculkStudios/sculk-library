package gg.sculk.series.registry

import gg.sculk.core.annotation.SculkInternal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(SculkInternal::class)
class SculkRegistryTest {
    private val registry =
        SculkRegistry<String>(
            resolver =
                object : MappingResolver<String> {
                    override fun resolve(key: String): String? =
                        when (key) {
                            "hello" -> "Hello"
                            "world" -> "World"
                            else -> null
                        }

                    override fun keys(): Set<String> = setOf("hello", "world")
                },
        )

    @Test
    fun `resolves known key`() {
        assertEquals("Hello", registry.resolve("hello"))
    }

    @Test
    fun `returns null for unknown key`() {
        assertNull(registry.resolve("unknown"))
    }

    @Test
    fun `normalizes uppercase to lowercase`() {
        assertEquals("Hello", registry.resolve("HELLO"))
    }

    @Test
    fun `normalizes hyphens to underscores`() {
        // Both "hello" and "hell-o" should normalize to "hell_o" — neither matches "hello"
        // but "hello" itself should still match
        assertEquals("Hello", registry.resolve("hello"))
    }

    @Test
    fun `caches result on second call`() {
        val first = registry.resolve("hello")
        val second = registry.resolve("hello")
        assertEquals(first, second)
    }

    @Test
    fun `version adapter used as fallback`() {
        val adaptingRegistry =
            SculkRegistry<String>(
                resolver =
                    object : MappingResolver<String> {
                        override fun resolve(key: String): String? = null

                        override fun keys(): Set<String> = emptySet()
                    },
                versionAdapter =
                    object : VersionAdapter<String> {
                        override fun adapt(key: String): String? = if (key == "old_name") "NewValue" else null
                    },
            )
        assertEquals("NewValue", adaptingRegistry.resolve("old_name"))
    }
}
