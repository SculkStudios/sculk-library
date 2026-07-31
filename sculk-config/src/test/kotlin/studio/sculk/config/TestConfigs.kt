package studio.sculk.config

import kotlinx.serialization.Serializable

@Serializable
@ConfigFile("storage.yml")
@Comment("Where player data is kept.", "Edit while the server is stopped.")
data class StorageSettings(
    @Comment("Which backend to use: sqlite, mysql or postgres.")
    val backend: String = "sqlite",
    @Comment("Connection details for the remote backends.")
    val mysql: MysqlSettings = MysqlSettings(),
    @Comment("How many connections to keep open.")
    @Min(1)
    @Max(64)
    val poolSize: Int = 10,
    val tables: List<String> = listOf("players", "homes"),
)

@Serializable
data class MysqlSettings(
    @Comment("Host name or IP of the database server.")
    val host: String = "localhost",
    @Comment("The port MySQL is listening on.")
    @Min(1)
    @Max(65535)
    val port: Int = 3306,
    @NotEmpty
    val username: String = "root",
    val password: String = "",
)

@Serializable
@ConfigFile("settings.yml")
data class Settings(val maxHomes: Int = 5, val allowFlight: Boolean = false)

@Serializable
@ConfigFile("bounded.yml")
data class Bounded(@Min(10) val tooSmall: Int = 1, @Max(5) val tooBig: Int = 99, @NotEmpty val blank: String = "  ")

@Serializable
@ConfigFile("versioned.yml", revision = 2)
data class Versioned(val greeting: String = "hello")
