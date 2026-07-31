package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.annotation.SculkInternal
import java.io.File
import java.util.logging.Logger

/**
 * Pins the exact bytes of a generated file.
 *
 * Formatting is not cosmetic here: this file is the primary interface a server owner has with the
 * plugin. A change in quoting, indentation or comment placement is a change to a user-facing
 * document, and it should have to be made on purpose.
 */
@OptIn(SculkInternal::class)
class GeneratedFileFormatTest {
    @TempDir
    lateinit var folder: File

    private fun config() = SculkConfig(folder, Logger.getLogger("test")) { null }

    @Test
    fun `the generated file matches the golden copy exactly`() {
        config().load<StorageSettings>().getOrThrow()

        val expected = checkNotNull(javaClass.getResourceAsStream("/golden/storage.yml")) {
            "golden file missing from test resources"
        }.readBytes().decodeToString().replace("\r\n", "\n")

        assertEquals(expected, File(folder, "storage.yml").readText().replace("\r\n", "\n"))
    }

    @Test
    fun `a config with no comments generates without a leading blank line`() {
        config().load<Settings>().getOrThrow()

        assertEquals("max-homes: 5\nallow-flight: false\n", File(folder, "settings.yml").readText().replace("\r\n", "\n"))
    }
}
