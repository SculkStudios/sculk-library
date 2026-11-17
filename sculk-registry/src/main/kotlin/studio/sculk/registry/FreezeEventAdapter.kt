package studio.sculk.registry

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.registry.RegistryBuilder
import io.papermc.paper.registry.event.RegistryEventProvider
import io.papermc.paper.registry.event.WritableRegistry

/**
 * Paper 1.21.4: new entries are added in `RegistryFreezeEvent`, which later versions replaced with
 * `RegistryComposeEvent`. Only [RegistryAdapters] instantiates this, and only where `compose()`
 * does not exist.
 */
internal class FreezeEventAdapter : ComposeAdapter {
    override fun <T : Any, B : RegistryBuilder<T>> compose(
        context: BootstrapContext,
        provider: RegistryEventProvider<T, B>,
        action: (WritableRegistry<T, B>) -> Unit,
    ) {
        context.lifecycleManager.registerEventHandler(provider.freeze().newHandler { event -> action(event.registry()) })
    }
}
