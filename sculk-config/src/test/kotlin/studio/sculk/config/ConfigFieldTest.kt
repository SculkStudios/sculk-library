package studio.sculk.config

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.SculkResult
import studio.sculk.Secret
import studio.sculk.getOrThrow
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.io.path.readText
import kotlin.io.path.writeText

@Serializable
data class ShopSettings(
    @Comment("Shown above the shop menu.")
    @NotBlank
    val title: String = "Shop",
    @Range(min = 1, max = 54)
    val slots: Int = 27,
    val open: Boolean = true,
    @DecimalRange(min = 0.0, max = 1.0)
    val tax: Double = 0.1,
    val mode: Mode = Mode.SURVIVAL,
    val storage: ShopStorage = ShopStorage(),
    val tags: List<String> = listOf("a"),
)

@Serializable
data class ShopStorage(val host: String = "localhost", val password: ConfigSecret = Secret("hunter2"))

/** The schema an editor sees, and updates that validate before they save. */
class ConfigFieldTest {
    @TempDir
    lateinit var directory: Path

    private val file get() = directory.resolve("shop.yml")

    private fun load(): ConfigFile<ShopSettings> = SculkConfig.load<ShopSettings>(file).getOrThrow()

    @Test
    fun `fields list every scalar setting with its bounds and current value, and never a secret's value`() {
        val fields = load().fields().associateBy { it.path }
        assertEquals(
            listOf("title", "slots", "open", "tax", "mode", "storage.host", "storage.password"),
            fields.keys.toList(),
            "lists are left to the file",
        )
        assertEquals(ConfigField.Kind.TEXT, fields.getValue("title").kind)
        assertEquals(listOf("Shown above the shop menu."), fields.getValue("title").comment)
        assertTrue(fields.getValue("title").notBlank)
        assertEquals(1.0..54.0, fields.getValue("slots").range)
        assertEquals("27", fields.getValue("slots").current)
        assertEquals(ConfigField.Kind.TOGGLE, fields.getValue("open").kind)
        assertEquals(ConfigField.Kind.DECIMAL, fields.getValue("tax").kind)
        assertEquals(ConfigField.Kind.CHOICE, fields.getValue("mode").kind)
        assertEquals("survival", fields.getValue("mode").current)
        assertEquals(listOf("survival", "hard-core"), fields.getValue("mode").choices)
        assertTrue(fields.getValue("storage.password").secret)
        assertEquals("", fields.getValue("storage.password").current)
    }

    @Test
    fun `an update sets the values, saves them and keeps the operator's comments`() {
        val config = load()
        file.writeText(file.readText().replace("slots:", "# my note\nslots:"))
        config.reload().getOrThrow()
        val updated = config.update(
            mapOf("slots" to "45", "open" to "false", "mode" to "hard-core", "storage.host" to "db.local"),
        ).getOrThrow()
        assertEquals(45, updated.slots)
        assertEquals(false, updated.open)
        assertEquals(Mode.HARD_CORE, updated.mode)
        assertEquals("db.local", updated.storage.host)
        assertEquals("hunter2", updated.storage.password.reveal(), "a secret nobody changed stays")
        assertEquals(updated, config.value)
        assertTrue("# my note" in file.readText())
        assertTrue("slots: 45" in file.readText())
    }

    @Test
    fun `an update that breaks a check or names no setting changes nothing`() {
        val config = load()
        val before = file.readText()
        val outOfRange = config.update(mapOf("slots" to "99"))
        assertTrue(outOfRange is InvalidConfig && outOfRange.path == "slots", outOfRange.toString())
        val notANumber = config.update(mapOf("tax" to "lots"))
        assertTrue(notANumber is InvalidConfig, notANumber.toString())
        val unknown = config.update(mapOf("tags" to "b"))
        assertTrue(unknown is InvalidConfig && unknown.path == "tags", unknown.toString())
        assertEquals(before, file.readText())
        assertEquals(27, config.value.slots)
    }

    @Test
    fun `an update refuses to overwrite an edit made on disk since the file was read`() {
        val config = load()
        val edited = file.readText().replace("slots: 27", "slots: 9")
        file.writeText(edited)
        val failure = config.update(mapOf("open" to "false"))
        assertTrue(failure is ConfigConflict, failure.toString())
        assertEquals(edited, file.readText())
        assertEquals(true, config.value.open)
    }

    @Test
    fun `concurrent updates of different settings all survive`() {
        val config = load()
        val changes = listOf("slots" to "45", "open" to "false", "mode" to "hard-core", "storage.host" to "db.local")
        val start = CountDownLatch(1)
        val results = CopyOnWriteArrayList<SculkResult<ShopSettings>>()
        val threads = changes.map { change ->
            thread {
                start.await()
                results += config.update(mapOf(change))
            }
        }
        start.countDown()
        threads.forEach { it.join(TIMEOUT_MILLIS) }
        assertTrue(results.size == changes.size && results.all { it is SculkResult.Success }, results.toString())
        val expected = ShopSettings(slots = 45, open = false, mode = Mode.HARD_CORE, storage = ShopStorage(host = "db.local"))
        assertEquals(expected, config.value)
        assertEquals(expected, SculkConfig.load<ShopSettings>(file).getOrThrow().value)
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
