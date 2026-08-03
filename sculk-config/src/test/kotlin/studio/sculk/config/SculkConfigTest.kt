package studio.sculk.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import studio.sculk.annotation.SculkInternal
import java.io.File
import java.util.logging.Logger

@OptIn(SculkInternal::class)
class SculkConfigTest {
    @TempDir
    lateinit var folder: File

    private fun config(env: Map<String, String> = emptyMap()) = SculkConfig(folder, Logger.getLogger("test")) { env[it] }

    private fun file(name: String) = File(folder, name)

    @Test
    fun `a missing file is generated from the defaults`() {
        val loaded = config().load<Settings>().getOrThrow()

        assertEquals(5, loaded.maxHomes)
        assertTrue(file("settings.yml").exists())
    }

    @Test
    fun `an existing value is kept and a new key is added`() {
        file("storage.yml").writeText("backend: mysql\n")

        val loaded = config().load<StorageSettings>().getOrThrow()

        assertEquals("mysql", loaded.backend, "the user's value must survive")
        assertEquals(10, loaded.poolSize, "a key the file did not have takes its default")
        assertTrue(file("storage.yml").readText().contains("pool-size: 10"), "and is written back into the file")
        assertTrue(file("storage.yml").readText().contains("backend: mysql"))
    }

    @Test
    fun `an up-to-date file is not rewritten`() {
        config().load<Settings>().getOrThrow()
        val stamp = file("settings.yml").lastModified()
        val text = file("settings.yml").readText()

        file("settings.yml").setLastModified(stamp - 10_000)
        config().load<Settings>().getOrThrow()

        assertEquals(text, file("settings.yml").readText())
        assertEquals(stamp - 10_000, file("settings.yml").lastModified(), "an unchanged file must not be touched")
    }

    @Test
    fun `a windows-authored file is not rewritten just for its line endings`() {
        file("settings.yml").writeText("max-homes: 5\r\nallow-flight: false\r\n")
        val stamp = file("settings.yml").lastModified() - 10_000
        file("settings.yml").setLastModified(stamp)

        config().load<Settings>().getOrThrow()

        assertEquals(stamp, file("settings.yml").lastModified())
    }

    @Test
    fun `an unknown key is ignored rather than failing the load`() {
        file("settings.yml").writeText("max-homes: 9\nremoved-in-v4: true\n")

        assertEquals(9, config().load<Settings>().getOrThrow().maxHomes)
    }

    @Test
    fun `load caches so two calls return the same instance`() {
        val config = config()

        assertTrue(config.load<Settings>().getOrThrow() === config.load<Settings>().getOrThrow())
    }

    @Test
    fun `reload picks up an edit that load would have served from cache`() {
        val config = config()
        assertEquals(5, config.load<Settings>().getOrThrow().maxHomes)

        file("settings.yml").writeText("max-homes: 42\n")

        assertEquals(42, config.reload<Settings>().getOrThrow().maxHomes)
        assertEquals(42, config.load<Settings>().getOrThrow().maxHomes, "the cache is updated too")
    }

    @Test
    fun `a reload listener runs on reload`() {
        val config = config()
        var runs = 0
        config.onReload<Settings> { runs++ }
        config.load<Settings>().getOrThrow()

        config.reload<Settings>().getOrThrow()

        assertEquals(1, runs)
    }

    @Test
    fun `a bumped revision backs the old file up and regenerates`() {
        file("versioned.yml").writeText("greeting: outdated\n")

        val loaded = config().load<Versioned>().getOrThrow()

        assertEquals("hello", loaded.greeting, "the shipped default replaces the superseded value")
        assertEquals("greeting: outdated\n", file("versioned.yml.1.bak").readText())
        assertTrue(file("versioned.yml").readText().contains("# revision: 2"))
    }

    @Test
    fun `a file already at the declared revision is left alone`() {
        config().load<Versioned>().getOrThrow()
        file("versioned.yml").writeText("# revision: 2\ngreeting: mine\n")

        assertEquals("mine", config().reload<Versioned>().getOrThrow().greeting)
        assertFalse(file("versioned.yml.2.bak").exists())
    }

    @Test
    fun `an environment variable is substituted into a value`() {
        file("storage.yml").writeText("backend: \${DB_BACKEND}\n")

        val loaded = config(mapOf("DB_BACKEND" to "postgres")).load<StorageSettings>().getOrThrow()

        assertEquals("postgres", loaded.backend)
    }

    @Test
    fun `an unset variable falls back to its default`() {
        file("storage.yml").writeText("backend: \${DB_BACKEND:-mysql}\n")

        assertEquals("mysql", config().load<StorageSettings>().getOrThrow().backend)
    }

    @Test
    fun `a file that reads from the environment is never rewritten`() {
        // Otherwise the merged render -- built from substituted text -- would write the resolved
        // secret back into the file on disk.
        val source = "backend: \${DB_BACKEND}\n"
        file("storage.yml").writeText(source)

        config(mapOf("DB_BACKEND" to "postgres")).load<StorageSettings>().getOrThrow()

        assertEquals(source, file("storage.yml").readText(), "the placeholder must still be there")
        assertFalse(file("storage.yml").readText().contains("postgres"))
    }

    @Test
    fun `a constraint violation is reported with its full path and does not fail the load`() {
        file("storage.yml").writeText("mysql:\n  port: 70000\n")
        val logged = mutableListOf<String>()
        val logger = Logger.getLogger("capture").apply {
            useParentHandlers = false
            addHandler(
                object : java.util.logging.Handler() {
                    override fun publish(record: java.util.logging.LogRecord) {
                        logged += record.message
                    }

                    override fun flush() = Unit

                    override fun close() = Unit
                },
            )
        }

        val loaded = SculkConfig(folder, logger) { null }.load<StorageSettings>().getOrThrow()

        assertEquals(70000, loaded.mysql.port, "the value is still used; the server keeps booting")
        assertTrue(
            logged.any { it.contains("mysql.port") && it.contains("65535") },
            "the warning must name the nested path, got: $logged",
        )
    }

    @Test
    fun `violations reports the constraints a shipped default breaks`() {
        val found = config().violations<Bounded>()

        assertEquals(3, found.size, "got: $found")
        assertTrue(found.any { it.startsWith("too-small") })
        assertTrue(found.any { it.startsWith("too-big") })
        assertTrue(found.any { it.startsWith("blank") })
    }

    @Test
    fun `clean shipped defaults report no violations`() {
        assertEquals(emptyList<String>(), config().violations<StorageSettings>())
    }

    @Test
    fun `a migration renames a key before the file is decoded`() {
        file("settings.yml").writeText("config-version: 1\nhomes-limit: 12\n")
        val config = config()
        config.migrations<Settings> {
            from(1).to(2) { rename("homes-limit", "max-homes") }
        }

        assertEquals(12, config.load<Settings>().getOrThrow().maxHomes)
    }

    @Test
    fun `migrations registered after a load are rejected`() {
        val config = config()
        config.load<Settings>().getOrThrow()

        val failure = kotlin.runCatching {
            config.migrations<Settings> { from(1).to(2) { } }
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "got: $failure")
    }

    @Test
    fun `a generated file whose default names an environment variable can be loaded again`() {
        // The first boot writes the file, the second reads it back. A shipped `${VAR:-}` default
        // that renders unquoted substitutes to `password:` -- an empty YAML value, which is null --
        // so the file the framework itself generated no longer parses. Every boot after the first
        // one failed, which reads as "the plugin broke overnight" rather than as a rendering bug.
        config().load<Secrets>().getOrThrow()

        val reloaded = config().load<Secrets>()

        assertTrue(reloaded.isSuccess, "the generated file must round-trip, got: $reloaded")
        assertEquals("", reloaded.getOrThrow().password, "an unset variable with an empty default is an empty string")
    }

    @Test
    fun `a placeholder default is quoted so substituting it cannot empty the key`() {
        config().load<Secrets>().getOrThrow()

        val text = file("secrets.yml").readText()

        assertTrue(text.contains("password: \"\${DB_PASSWORD:-}\""), "got: $text")
        assertTrue(text.contains("token: \"\${API_TOKEN}\""), "got: $text")
        assertFalse(text.contains("plain: \"keep me\""), "an ordinary string must not gain quotes: $text")
    }

    @Test
    fun `a set variable still wins over the default in a generated file`() {
        config().load<Secrets>().getOrThrow()

        val loaded = config(mapOf("DB_PASSWORD" to "hunter2")).load<Secrets>().getOrThrow()

        assertEquals("hunter2", loaded.password)
    }

    @Test
    fun `save writes the given value out`() {
        config().save(Settings(maxHomes = 77)).getOrThrow()

        assertTrue(file("settings.yml").readText().contains("max-homes: 77"))
    }
}
