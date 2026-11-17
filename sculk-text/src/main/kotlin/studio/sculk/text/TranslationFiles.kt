package studio.sculk.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.translation.GlobalTranslator
import studio.sculk.ResourceScope
import studio.sculk.SculkHandle
import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import studio.sculk.getOrNull
import studio.sculk.map
import java.nio.file.Path
import java.util.Locale

/**
 * A plugin's translations as files: its own bundles with the operator's overrides laid over them
 * (see [Translations.load]), reloadable as a whole, and rendered for each viewer through Adventure's
 * [GlobalTranslator] once [register]ed.
 *
 * Thread-safe. A reload replaces [translations] in one step, so a lookup sees the old set or the
 * new one, never a mixture.
 */
@SculkStable
public class TranslationFiles private constructor(private val loader: () -> SculkResult<Translations>, initial: Translations) {
    @Volatile
    private var current: Translations = initial

    /** The translations as of the last successful load. */
    public val translations: Translations get() = current

    /** Reads every bundle again. On success the result replaces [translations]; on failure nothing changes. */
    @Synchronized
    public fun reload(): SculkResult<Translations> = loader().also { result -> result.getOrNull()?.let { current = it } }

    /**
     * Adds these translations to [GlobalTranslator] as [name], so a `Component.translatable(key, ...)`
     * sent to a player renders in that player's locale with the translations current at that
     * moment. The source is removed when [scope] closes or the returned handle does, whichever
     * comes first.
     */
    public fun register(scope: ResourceScope, name: Key): SculkHandle {
        val translator = TranslationsTranslator(name, current.text) { current }
        GlobalTranslator.translator().addSource(translator)
        val removal = SculkHandle.once { GlobalTranslator.translator().removeSource(translator) }
        val owned = scope.onClose(removal::close)
        return SculkHandle.once {
            owned.close()
            removal.close()
        }
    }

    @SculkStable
    public companion object {
        /** Loads as [Translations.load] does from the classpath, keeping the sources so [reload] can read them again. */
        public fun load(
            classLoader: ClassLoader,
            resources: String,
            overrides: Path,
            defaultLocale: Locale,
            text: Text = Text(),
        ): SculkResult<TranslationFiles> {
            val loader = { Translations.load(classLoader, resources, overrides, defaultLocale, text) }
            return loader().map { TranslationFiles(loader, it) }
        }
    }
}
