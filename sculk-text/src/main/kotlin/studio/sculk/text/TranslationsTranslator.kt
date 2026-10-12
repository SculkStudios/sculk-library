package studio.sculk.text

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.minimessage.translation.Argument
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator
import net.kyori.adventure.translation.Translator
import java.text.MessageFormat
import java.util.Locale

/**
 * [Translations] as an Adventure translator. Templates are parsed and theme-expanded; arguments
 * never are. A key without a translation returns null, so the client shows it as plain text.
 */
internal class TranslationsTranslator(private val name: Key, private val translations: Translations, text: Text) : Translator {
    // MiniMessageTranslationStore has no plural selection or theme expansion, so the bundles stay
    // Sculk's and MiniMessageTranslator only parses the templates and fills in the arguments.
    private val templates = object : MiniMessageTranslator(text.miniMessage) {
        override fun name(): Key = name

        override fun getMiniMessageString(key: String, locale: Locale): String? = translations.expandedTemplate(key, locale)
    }

    override fun name(): Key = name

    override fun translate(key: String, locale: Locale): MessageFormat? = null

    override fun translate(component: TranslatableComponent, locale: Locale): Component? {
        val count = component.arguments().firstNotNullOfOrNull { it.value() as? Number }?.toLong()
        val form = count?.let { translations.pluralForm(component.key(), it, locale) } ?: return templates.translate(component, locale)
        val counted = component.key(form.key).arguments(component.arguments() + Argument.numeric("count", count))
        return templates.translate(counted, locale)
    }
}
