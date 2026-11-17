package studio.sculk.placeholder

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import studio.sculk.annotation.SculkStable
import studio.sculk.text.TagNames

/**
 * A placeholder's name: `shop:balance`, written in a template as the tag `<shop_balance>`.
 *
 * A namespace is lower-case letters, digits and `-`, never `_`, so the first `_` of a tag always
 * ends the namespace and two keys can never share a tag. A name is lower-case letters, digits, `_`
 * and `-`. A key whose tag would hide one of MiniMessage's own tags is refused.
 */
@SculkStable
public class PlaceholderKey private constructor(
    /** The plugin's part, before the first `_` of the tag: `shop` in `shop:balance`. */
    public val namespace: String,
    /** The placeholder's part within the namespace: `balance` in `shop:balance`. */
    public val name: String,
) {
    /** The MiniMessage tag name, without brackets: `shop_balance`. */
    public val tag: String = "${namespace}_$name"

    override fun equals(other: Any?): Boolean = other is PlaceholderKey && other.namespace == namespace && other.name == name

    override fun hashCode(): Int = 31 * namespace.hashCode() + name.hashCode()

    override fun toString(): String = "$namespace:$name"

    @SculkStable
    public companion object {
        private val NAMESPACE = Regex("[a-z0-9-]+")

        /** The key [name] in [namespace]. Throws [IllegalArgumentException] for a malformed or shadowing key. */
        public fun of(namespace: String, name: String): PlaceholderKey {
            require(NAMESPACE.matches(namespace)) { "Placeholder namespace '$namespace' must be lower-case letters, digits or '-'." }
            TagNames.requireValid(name, "Placeholder name")
            val key = PlaceholderKey(namespace, name)
            require(!TagResolver.standard().has(key.tag)) { "Placeholder $key would hide MiniMessage's own <${key.tag}> tag." }
            return key
        }

        /** Reads `namespace:name`. Throws [IllegalArgumentException] for anything else. */
        public fun parse(key: String): PlaceholderKey {
            val separator = key.indexOf(':')
            require(separator > 0) { "Placeholder key '$key' must be namespaced, like shop:balance." }
            return of(key.substring(0, separator), key.substring(separator + 1))
        }
    }
}
