package studio.sculk.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkInternal
import java.io.File

/**
 * The JDBC URL, which decides whether a server can connect at all.
 *
 * This is pinned separately because the integration tests cannot cover it: they build their own URL
 * from an environment variable and never call [SculkData.urlFor], so a connection-string regression
 * would sail past a fully green database matrix.
 */
@OptIn(SculkInternal::class)
class SculkDataUrlTest {
    private fun url(dialect: SqlDialect, useSsl: Boolean = false) = SculkData.urlFor(
        dialect,
        StorageSettings(remote = RemoteSettings(host = "db.example", port = 3306, database = "mc", useSsl = useSsl)),
        File("."),
    )

    /**
     * MySQL 8 authenticates with `caching_sha2_password` by default, and that exchange needs either
     * TLS or permission to fetch the server's public key. With neither, the driver fails with "RSA
     * public key is not available client side" *before a single statement is sent* — so a server
     * owner on a stock MySQL 8 could not start at all, and the error named nothing they had
     * configured. Found by pointing CI at a real `mysql:8` rather than at MariaDB.
     */
    @Test
    fun `mysql without tls can still complete the default handshake`() {
        val url = url(SqlDialect.MYSQL, useSsl = false)

        assertTrue(url.startsWith("jdbc:mariadb://db.example:3306/mc?"), "unexpected url: $url")
        assertTrue("useSSL=false" in url, "unexpected url: $url")
        assertTrue("allowPublicKeyRetrieval=true" in url, "a stock MySQL 8 cannot be reached without this: $url")
    }

    /**
     * With TLS on, the key exchange is protected and the fallback is neither needed nor wanted —
     * it is the half that carries the man-in-the-middle caveat.
     */
    @Test
    fun `mysql with tls does not ask for the key fallback`() {
        val url = url(SqlDialect.MYSQL, useSsl = true)

        assertTrue("useSSL=true" in url, "unexpected url: $url")
        assertFalse("allowPublicKeyRetrieval" in url, "the fallback must not be sent once TLS is on: $url")
    }

    @Test
    fun `postgres carries its own ssl flag and nothing else`() {
        assertEquals("jdbc:postgresql://db.example:3306/mc?ssl=false", url(SqlDialect.POSTGRES))
        assertEquals("jdbc:postgresql://db.example:3306/mc?ssl=true", url(SqlDialect.POSTGRES, useSsl = true))
    }

    @Test
    fun `sqlite resolves against the data folder and ignores the remote settings`() {
        val url = SculkData.urlFor(SqlDialect.SQLITE, StorageSettings(file = "data.db"), File("plugins/Thing"))

        assertTrue(url.startsWith("jdbc:sqlite:"), "unexpected url: $url")
        assertTrue(url.endsWith("data.db"), "unexpected url: $url")
        assertFalse("db.example" in url, "sqlite must not pick up remote settings: $url")
    }
}
