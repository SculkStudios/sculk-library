package studio.sculk.registry.adapter

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.registry.RegistryBuilder
import io.papermc.paper.registry.event.RegistryEventProvider
import io.papermc.paper.registry.event.WritableRegistry
import studio.sculk.registry.ComposeAdapter

/** Paper 1.21.11 and later: new entries are added in `RegistryComposeEvent`. Instantiated by name only. */
internal class ComposeEventAdapter : ComposeAdapter {
    override fun <T : Any, B : RegistryBuilder<T>> compose(
        context: BootstrapContext,
        provider: RegistryEventProvider<T, B>,
        action: (WritableRegistry<T, B>) -> Unit,
    ) {
        context.lifecycleManager.registerEventHandler(provider.compose().newHandler { event -> action(event.registry()) })
    }
}
