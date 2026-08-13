package studio.sculk.integrations

import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkExperimental
import studio.sculk.coroutine.await
import studio.sculk.flatMap
import studio.sculk.map
import java.lang.reflect.Method
import java.util.UUID
import java.util.concurrent.CompletableFuture
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

/**
 * LuckPerms metadata lookups.
 *
 * Reflective, so nothing here is a compile or load dependency: a server without LuckPerms gets a
 * named failure rather than a `NoClassDefFoundError`.
 *
 * The reads that matter suspend, because the honest way to get a user who is not currently online is
 * `loadUser`, which returns a future. Blocking on it is what turned a slow or remote LuckPerms backend
 * into a stalled server tick in the plugin this was written for — it called `.get()` with no timeout
 * from a join listener.
 */
@SculkExperimental
public object LuckPermsIntegration {
    /**
     * The user's primary group, from cache only.
     *
     * Non-suspending and therefore cache-only: a user who is not loaded reads as null rather than
     * being fetched. Use [groups] for an answer that is correct for anybody.
     */
    public fun primaryGroup(uuid: UUID): SculkResult<String?> = SculkResult.catching("read the LuckPerms primary group") {
        val userManager = userManager()
        val user = userManager.javaClass.getMethod("getUser", UUID::class.java).invoke(userManager, uuid)
        user?.let { PERMISSION_HOLDER.getMethod("getPrimaryGroup").invoke(it) as? String }
    }

    /**
     * Every group the user effectively has, inherited groups included.
     *
     * Not the primary group, and not the raw node list. A rank mapping wants "does this player have
     * `admin`" answered the way LuckPerms itself would answer it, which means inheritance, contexts,
     * temporary grants and negations are all already applied — reading `InheritanceNode`s off the user
     * and taking their names, as the plugin this replaces did, ignores every one of those and reports a
     * group the player does not actually have.
     */
    public suspend fun groups(uuid: UUID): SculkResult<Set<String>> = SculkResult.catching("read the player's LuckPerms groups") {
        val user = loadUser(uuid) ?: return@catching emptySet()
        val options = contextualOptions()
        val inherited = PERMISSION_HOLDER
            .getMethod("getInheritedGroups", QUERY_OPTIONS)
            .invoke(user, options) as Collection<*>

        inherited.mapNotNullTo(mutableSetOf()) { group ->
            group?.let { GROUP.getMethod("getName").invoke(it) as? String }
        }
    }

    /**
     * Whether the user holds [node], as LuckPerms would decide it.
     *
     * Uses the cached permission data rather than scanning nodes, so an explicitly negated permission
     * reads as absent — which is the whole point of being able to negate one.
     */
    public suspend fun hasPermission(uuid: UUID, node: String): SculkResult<Boolean> =
        SculkResult.catching("check the LuckPerms permission '$node'") {
            val user = loadUser(uuid) ?: return@catching false
            val options = contextualOptions()
            val cached = PERMISSION_HOLDER.getMethod("getCachedData").invoke(user)
            val permissions = CACHED_DATA.getMethod("getPermissionData", QUERY_OPTIONS).invoke(cached, options)
            val tristate = CACHED_PERMISSIONS.getMethod("checkPermission", String::class.java).invoke(permissions, node)
            TRISTATE.getMethod("asBoolean").invoke(tristate) as Boolean
        }

    /**
     * Loads a user, whether or not they are online.
     *
     * Awaited rather than blocked on. LuckPerms may be backed by a remote database, and a stall here
     * would otherwise be a stall on whatever thread asked.
     */
    private suspend fun loadUser(uuid: UUID): Any? {
        val userManager = userManager()
        val future = userManager.javaClass
            .getMethod("loadUser", UUID::class.java)
            .invoke(userManager, uuid) as CompletableFuture<*>
        return future.await()
    }

    private fun userManager(): Any {
        val provider = Reflect.method("net.luckperms.api.LuckPermsProvider", "get").invoke(null)
        return provider.javaClass.getMethod("getUserManager").invoke(provider)
    }

    /** Contexts as LuckPerms resolves them for the server the player is on. */
    private fun contextualOptions(): Any = Reflect.method("net.luckperms.api.query.QueryOptions", "defaultContextualOptions").invoke(null)

    // Methods are looked up on the declaring interfaces, not on the object in hand. LuckPerms'
    // implementation classes are not public, and invoking a method resolved from one of those throws
    // IllegalAccessException on some JVMs even though the method itself is public.
    private val PERMISSION_HOLDER: Class<*> get() = Class.forName("net.luckperms.api.model.PermissionHolder")
    private val GROUP: Class<*> get() = Class.forName("net.luckperms.api.model.group.Group")
    private val QUERY_OPTIONS: Class<*> get() = Class.forName("net.luckperms.api.query.QueryOptions")
    private val CACHED_DATA: Class<*> get() = Class.forName("net.luckperms.api.cacheddata.CachedDataManager")
    private val CACHED_PERMISSIONS: Class<*> get() = Class.forName("net.luckperms.api.cacheddata.CachedPermissionData")
    private val TRISTATE: Class<*> get() = Class.forName("net.luckperms.api.util.Tristate")
}
