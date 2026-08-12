package studio.sculk.data

import kotlinx.serialization.Serializable
import studio.sculk.annotation.SculkStable
import studio.sculk.config.Comment
import studio.sculk.config.ConfigFile
import studio.sculk.config.Max
import studio.sculk.config.Min
import studio.sculk.config.NotEmpty

/** `storage.yml`. Dogfoods the config system rather than parsing its own file. */
@Serializable
@ConfigFile("storage.yml")
@Comment("Where this plugin keeps its data.", "Edit while the server is stopped.")
@SculkStable
public data class StorageSettings(
    @Comment(
        "One of: sqlite, mysql, mariadb, postgres.",
        "mysql and mariadb are the same setting - both use the MariaDB driver, which talks to",
        "either server. Write whichever one you actually run; nothing else changes.",
    )
    @NotEmpty
    public val backend: String = "sqlite",
    @Comment("The SQLite file, relative to the plugin folder. Ignored for the remote backends.")
    public val file: String = "data.db",
    @Comment("Connection details for the remote backends. Ignored by sqlite.")
    public val remote: RemoteSettings = RemoteSettings(),
    @Comment(
        "How many connections to keep open.",
        "More is not faster: a Minecraft server has one thread doing the asking.",
    )
    @Min(1)
    @Max(64)
    public val poolSize: Int = 10,
)

@Serializable
@SculkStable
public data class RemoteSettings(
    public val host: String = "localhost",
    @Comment("3306 for mysql/mariadb, 5432 for postgres. The default is the MySQL one.")
    @Min(1)
    @Max(65535)
    public val port: Int = 3306,
    public val database: String = "minecraft",
    public val username: String = "root",
    // Reads from the environment so the shipped file stays a working example and the real secret
    // never lands in a config anyone might paste into a support channel.
    public val password: String = "\${DB_PASSWORD:-}",
    @Comment(
        "Encrypt the connection. Leave off for a database on this machine or a private network.",
        "Turn it on if the database is reachable from the internet - and note that on MySQL 8 that",
        "also removes the public-key-retrieval fallback its default login needs when TLS is off.",
    )
    public val useSsl: Boolean = false,
)
