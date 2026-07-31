package studio.sculk.data

import kotlinx.serialization.Serializable

enum class Rank { DEFAULT, VIP, MVP }

@Serializable
@Table("players")
data class PlayerRow(
    @Id val id: String,
    @Index val name: String = "",
    val coins: Long = 0,
    val level: Int = 1,
    val ratio: Double = 0.0,
    val banned: Boolean = false,
    val rank: Rank = Rank.DEFAULT,
    val note: String? = null,
    @Json val homes: List<String> = emptyList(),
)

/** Exercises identifier quoting: every one of these is a reserved word somewhere. */
@Serializable
@Table("orders")
data class ReservedRow(@Id val id: String, val order: Int = 0, val group: String = "", val key: String = "")

/** The same table as [PlayerRow] but with one extra field, for the migration test. */
@Serializable
@Table("players")
data class PlayerRowV2(
    @Id val id: String,
    @Index val name: String = "",
    val coins: Long = 0,
    val level: Int = 1,
    val ratio: Double = 0.0,
    val banned: Boolean = false,
    val rank: Rank = Rank.DEFAULT,
    val note: String? = null,
    @Json val homes: List<String> = emptyList(),
    val prestige: Int = 7,
)

@Serializable
@Table("broken")
data class NoKeyRow(val a: String = "")

@Serializable
@Table("broken")
data class TwoKeyRow(@Id val a: String = "", @Id val b: String = "")
