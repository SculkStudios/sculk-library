package studio.sculk.items

import org.bukkit.NamespacedKey

/**
 * Creates stable namespaced keys for item persistent data.
 *
 * Plain keys use the `sculk` namespace. Namespaced strings such as
 * `myplugin:item_id` keep their explicit namespace.
 */
public object ItemKeys {
    public const val DEFAULT_NAMESPACE: String = "sculk"

    @JvmStatic
    public fun of(key: String): NamespacedKey {
        val trimmed = key.trim().lowercase()
        require(trimmed.isNotBlank()) { "Persistent data key cannot be blank." }

        return if (':' in trimmed) {
            NamespacedKey(trimmed.substringBefore(':'), trimmed.substringAfter(':'))
        } else {
            NamespacedKey(DEFAULT_NAMESPACE, trimmed)
        }
    }
}
