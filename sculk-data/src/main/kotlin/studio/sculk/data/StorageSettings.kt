package studio.sculk.data

import kotlinx.serialization.Serializable
import studio.sculk.config.Comment
import studio.sculk.config.ConfigFile
import studio.sculk.config.Max
import studio.sculk.config.Min
import studio.sculk.config.NotEmpty

/** `storage.yml`. Dogfoods the config system rather than parsing its own file. */
@Serializable
@ConfigFile("storage.yml")
@Comment("Where this plugin keeps its data.", "Edit while the server is stopped.")
public data class StorageSettings(
    @Comment("One of: sqlite, mysql, postgres.")
    @NotEmpty
    public val backend: String = "sqlite",
    @Comment("The SQLite file, relative to the plugin folder. Ignored for the remote backends.")
    public val file: String = "data.db",
    @Comment("Connection details for mysql and postgres.")
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
public data class RemoteSettings(
    public val host: String = "localhost",
    @Min(1)
    @Max(65535)
    public val port: Int = 3306,
    public val database: String = "minecraft",
    public val username: String = "root",
    // Reads from the environment so the shipped file stays a working example and the real secret
    // never lands in a config anyone might paste into a support channel.
    public val password: String = "\${DB_PASSWORD:-}",
    public val useSsl: Boolean = false,
)
