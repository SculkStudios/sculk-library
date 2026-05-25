package studio.sculk.integrations

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import studio.sculk.core.SculkResult
import studio.sculk.core.flatMap
import studio.sculk.core.map
import java.util.UUID

/** Optional adapters for common server plugins. */
public class SculkIntegrations public constructor(
    private val plugin: Plugin,
) {
    /** Returns a PlaceholderAPI adapter if PlaceholderAPI is installed. */
    public fun placeholderApi(): SculkResult<PlaceholderApiIntegration> = requirePlugin("PlaceholderAPI").map { PlaceholderApiIntegration }

    /** Returns a Vault economy adapter if Vault and an economy provider are installed. */
    public fun vaultEconomy(): SculkResult<VaultEconomyIntegration> = requirePlugin("Vault").flatMap { VaultEconomyIntegration.create() }

    /** Returns a LuckPerms adapter if LuckPerms is installed. */
    public fun luckPerms(): SculkResult<LuckPermsIntegration> = requirePlugin("LuckPerms").map { LuckPermsIntegration }

    private fun requirePlugin(name: String): SculkResult<Plugin> {
        val dependency = plugin.server.pluginManager.getPlugin(name)
        return if (dependency != null && dependency.isEnabled) {
            SculkResult.success(dependency)
        } else {
            SculkResult.failure("$name is not installed or is not enabled.")
        }
    }
}

/** PlaceholderAPI adapter. */
public object PlaceholderApiIntegration {
    public fun parse(
        player: Player?,
        text: String,
    ): String =
        runCatching {
            val clazz = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
            clazz.getMethod("setPlaceholders", OfflinePlayer::class.java, String::class.java).invoke(null, player, text) as String
        }.getOrElse { text }
}

/** Vault economy adapter. */
public class VaultEconomyIntegration private constructor(
    private val economy: Any,
) {
    public fun deposit(
        player: OfflinePlayer,
        amount: Double,
    ): SculkResult<Unit> = invoke("depositPlayer", player, amount)

    public fun withdraw(
        player: OfflinePlayer,
        amount: Double,
    ): SculkResult<Unit> = invoke("withdrawPlayer", player, amount)

    public fun balance(player: OfflinePlayer): SculkResult<Double> =
        runCatching {
            economy.javaClass.getMethod("getBalance", OfflinePlayer::class.java).invoke(economy, player) as Double
        }.fold(
            onSuccess = { SculkResult.success(it) },
            onFailure = { SculkResult.failure("Vault balance lookup failed.", it) },
        )

    public fun deposit(
        uuid: UUID,
        amount: Double,
    ): SculkResult<Unit> = deposit(Bukkit.getOfflinePlayer(uuid), amount)

    private fun invoke(
        method: String,
        player: OfflinePlayer,
        amount: Double,
    ): SculkResult<Unit> =
        runCatching {
            economy.javaClass.getMethod(method, OfflinePlayer::class.java, Double::class.javaPrimitiveType).invoke(economy, player, amount)
        }.fold(
            onSuccess = { SculkResult.success(Unit) },
            onFailure = { SculkResult.failure("Vault economy call '$method' failed.", it) },
        )

    public companion object {
        internal fun create(): SculkResult<VaultEconomyIntegration> {
            val registration =
                runCatching {
                    val economyClass = Class.forName("net.milkbowl.vault.economy.Economy")
                    Bukkit.getServicesManager().getRegistration(economyClass)
                }.getOrNull()
            val provider = registration?.provider
            return if (provider != null) {
                SculkResult.success(VaultEconomyIntegration(provider))
            } else {
                SculkResult.failure("Vault is installed, but no economy provider is registered.")
            }
        }
    }
}

/** LuckPerms adapter for common metadata lookups. */
public object LuckPermsIntegration {
    public fun primaryGroup(uuid: UUID): SculkResult<String?> =
        runCatching {
            val provider = Class.forName("net.luckperms.api.LuckPermsProvider").getMethod("get").invoke(null)
            val userManager = provider.javaClass.getMethod("getUserManager").invoke(provider)
            val user = userManager.javaClass.getMethod("getUser", UUID::class.java).invoke(userManager, uuid)
            user?.javaClass?.getMethod("getPrimaryGroup")?.invoke(user) as? String
        }.fold(
            onSuccess = { SculkResult.success(it) },
            onFailure = { SculkResult.failure("LuckPerms primary group lookup failed.", it) },
        )
}
