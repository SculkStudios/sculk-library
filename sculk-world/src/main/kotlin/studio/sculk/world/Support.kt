package studio.sculk.world

import net.kyori.adventure.key.InvalidKeyException
import net.kyori.adventure.key.Key
import org.bukkit.Bukkit

/** Throws [IllegalStateException] unless the caller is on the global region, the main thread on Paper. */
internal fun requireGlobal(what: String) {
    check(Bukkit.isGlobalTickThread()) {
        "$what must run on the global region (the main thread on Paper); call the suspending variant, or wrap the call in withGlobal."
    }
}

/** Whether the server has the class [name]; probed without initialising it. */
internal fun classExists(name: String): Boolean = try {
    Class.forName(name, false, GameRuleNames::class.java.classLoader)
    true
} catch (missing: ClassNotFoundException) {
    false
}

/** [text] as a key, `minecraft:` when it has no namespace, or null when it is not a valid key. */
internal fun keyOrNull(text: String): Key? = try {
    Key.key(text)
} catch (invalid: InvalidKeyException) {
    null
}
