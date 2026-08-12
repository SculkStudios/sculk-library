package studio.sculk.integrations

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.flatMap
import studio.sculk.map
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Adapters for plugins Sculk does not depend on.
 *
 * Every call goes through reflection so none of these become hard dependencies — a server without
 * Vault should not fail to load a plugin that merely supports it. An adapter is handed out only
 * once its plugin is present and enabled, so the reflection is reached only on a path already
 * proven to work.
 *
 * Experimental because it is untyped reflection against third-party APIs on their own release
 * cadence: an upstream signature change surfaces as a failed [SculkResult] at runtime, and Sculk
 * cannot promise otherwise across its own versions.
 */
@SculkExperimental
public class SculkIntegrations public constructor(private val plugin: Plugin) {
    public fun placeholderApi(): SculkResult<PlaceholderApiIntegration> = requirePlugin("PlaceholderAPI").map { PlaceholderApiIntegration }

    public fun vaultEconomy(): SculkResult<VaultEconomyIntegration> = requirePlugin("Vault").flatMap { VaultEconomyIntegration.create() }

    public fun luckPerms(): SculkResult<LuckPermsIntegration> = requirePlugin("LuckPerms").map { LuckPermsIntegration }

    /**
     * Custom items from Nexo, Oraxen or ItemsAdder, addressed as `nexo:ruby_sword`.
     *
     * Succeeds when at least one of them is installed, because the three are alternatives rather
     * than a set — a server has one. [CustomItems] itself is reachable statically, since a config
     * value resolves through the item DSL where no plugin is in scope; this is the presence check
     * for a plugin that wants to report at start-up whether the feature is live at all.
     */
    public fun customItems(): SculkResult<CustomItems> {
        val installed = CustomItems.pluginNames.filter { requirePlugin(it).isSuccess }
        return if (installed.isEmpty()) {
            SculkResult.failure("No custom-item plugin is installed or enabled; looked for ${CustomItems.pluginNames.joinToString()}.")
        } else {
            SculkResult.success(CustomItems)
        }
    }

    private fun requirePlugin(name: String): SculkResult<Plugin> {
        val dependency = plugin.server.pluginManager.getPlugin(name)
        return if (dependency != null && dependency.isEnabled) {
            SculkResult.success(dependency)
        } else {
            SculkResult.failure("$name is not installed or is not enabled.")
        }
    }
}

/**
 * Resolved reflective lookups.
 *
 * `Class.forName` and `getMethod` both walk and copy on every call. The placeholder adapter is
 * called per sidebar line per player per refresh, which is exactly where that cost lands.
 */
internal object Reflect {
    private val methods = ConcurrentHashMap<String, Method>()

    fun method(owner: String, name: String, vararg parameters: Class<*>): Method = methods.getOrPut("$owner#$name/${parameters.size}") {
        Class.forName(owner).getMethod(name, *parameters)
    }

    /**
     * The same cache for a class that is already in hand.
     *
     * Custom-item plugins are reached through their own class loader rather than `Class.forName`,
     * and the builder a lookup hands back is a type this module cannot name at all.
     */
    fun method(owner: Class<*>, name: String, vararg parameters: Class<*>): Method =
        methods.getOrPut("${owner.name}#$name/${parameters.size}") { owner.getMethod(name, *parameters) }
}

/** Runs text through PlaceholderAPI, returning it unchanged if anything goes wrong. */
@SculkExperimental
public object PlaceholderApiIntegration {
    public fun parse(player: Player?, text: String): String = runCatching {
        Reflect
            .method("me.clip.placeholderapi.PlaceholderAPI", "setPlaceholders", OfflinePlayer::class.java, String::class.java)
            .invoke(null, player, text) as String
    }.getOrElse { text }
}

/** Vault's economy service, if a provider is registered. */
@SculkExperimental
public class VaultEconomyIntegration private constructor(private val economy: Any) {
    public fun deposit(player: OfflinePlayer, amount: Double): SculkResult<Unit> = call("depositPlayer", player, amount)

    public fun withdraw(player: OfflinePlayer, amount: Double): SculkResult<Unit> = call("withdrawPlayer", player, amount)

    public fun deposit(uuid: UUID, amount: Double): SculkResult<Unit> = deposit(Bukkit.getOfflinePlayer(uuid), amount)

    public fun withdraw(uuid: UUID, amount: Double): SculkResult<Unit> = withdraw(Bukkit.getOfflinePlayer(uuid), amount)

    public fun balance(player: OfflinePlayer): SculkResult<Double> = SculkResult.catching("read the Vault balance") {
        economy.javaClass.getMethod("getBalance", OfflinePlayer::class.java).invoke(economy, player) as Double
    }

    public fun balance(uuid: UUID): SculkResult<Double> = balance(Bukkit.getOfflinePlayer(uuid))

    private fun call(method: String, player: OfflinePlayer, amount: Double): SculkResult<Unit> =
        SculkResult.catching("call Vault's $method") {
            economy.javaClass
                .getMethod(method, OfflinePlayer::class.java, Double::class.javaPrimitiveType)
                .invoke(economy, player, amount)
            @Suppress("UNUSED_EXPRESSION")
            Unit
        }

    public companion object {
        internal fun create(): SculkResult<VaultEconomyIntegration> {
            val provider = runCatching {
                Bukkit.getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"))
            }.getOrNull()?.provider
            return if (provider != null) {
                SculkResult.success(VaultEconomyIntegration(provider))
            } else {
                SculkResult.failure("Vault is installed, but no economy provider is registered.")
            }
        }
    }
}

/** LuckPerms metadata lookups. */
@SculkExperimental
public object LuckPermsIntegration {
    public fun primaryGroup(uuid: UUID): SculkResult<String?> = SculkResult.catching("read the LuckPerms primary group") {
        val provider = Reflect.method("net.luckperms.api.LuckPermsProvider", "get").invoke(null)
        val userManager = provider.javaClass.getMethod("getUserManager").invoke(provider)
        val user = userManager.javaClass.getMethod("getUser", UUID::class.java).invoke(userManager, uuid)
        user?.javaClass?.getMethod("getPrimaryGroup")?.invoke(user) as? String
    }
}
