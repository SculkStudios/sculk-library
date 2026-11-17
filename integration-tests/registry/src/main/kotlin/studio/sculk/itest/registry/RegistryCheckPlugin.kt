package studio.sculk.itest.registry

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.registry.RegistryKey
import io.papermc.paper.registry.TypedKey
import io.papermc.paper.registry.data.EnchantmentRegistryEntry
import io.papermc.paper.registry.event.RegistryEvents
import io.papermc.paper.registry.keys.EnchantmentKeys
import io.papermc.paper.registry.set.RegistrySet
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.plugin.java.JavaPlugin
import studio.sculk.SculkFailure
import studio.sculk.getOrNull
import studio.sculk.getOrThrow
import studio.sculk.registry.RegistryEntrySet
import studio.sculk.registry.Registries
import studio.sculk.registry.composeRegistry
import studio.sculk.registry.discoverDatapack
import studio.sculk.registry.modifyEntries
import java.io.File

internal val ECHO: Key = Key.key("sculk-itest:echo")

/** Adds an enchantment, raises Sharpness's maximum level and ships a datapack, all at bootstrap. */
class RegistryCheckBootstrap : PluginBootstrap {
    override fun bootstrap(context: BootstrapContext) {
        context.composeRegistry(RegistryEvents.ENCHANTMENT) { registry ->
            registry.register(TypedKey.create(RegistryKey.ENCHANTMENT, ECHO)) { builder ->
                builder.description(Component.text("Echo"))
                    .supportedItems(RegistrySet.keySet(RegistryKey.ITEM, TypedKey.create(RegistryKey.ITEM, Key.key("minecraft:diamond_sword"))))
                    .anvilCost(1)
                    .maxLevel(3)
                    .weight(10)
                    .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(1, 1))
                    .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(3, 1))
                    .activeSlots(EquipmentSlotGroup.MAINHAND)
            }
        }
        context.modifyEntries(RegistryEvents.ENCHANTMENT, listOf(EnchantmentKeys.SHARPNESS)) { _, builder -> builder.maxLevel(10) }
        context.discoverDatapack(javaClass.classLoader, "sculk-pack", "sculk-itest")
    }
}

/** Checks the bootstrap changes once the server runs, writes `results.properties` and stops the server. */
class RegistryCheckPlugin : JavaPlugin() {
    private val results = sortedMapOf<String, String>()

    override fun onEnable() {
        server.globalRegionScheduler.run(this) { check() }
    }

    private fun check() {
        verdict("composed-enchantment") {
            val echo = Registries.get(RegistryKey.ENCHANTMENT, ECHO).getOrNull()
            (echo?.maxLevel == 3) to "echo $echo, max level ${echo?.maxLevel}"
        }
        verdict("modified-entry") {
            val sharpness = Registries.get(RegistryKey.ENCHANTMENT, Key.key("minecraft:sharpness")).getOrThrow()
            (sharpness.maxLevel == 10) to "sharpness max level ${sharpness.maxLevel}"
        }
        verdict("datapack-tag") {
            val tagged = Registries.tag(RegistryKey.BLOCK, Key.key("sculk-itest:tested")).getOrNull()?.map { it.key().asString() }?.toSet()
            (tagged == setOf("minecraft:stone", "minecraft:granite")) to "tag holds $tagged"
        }
        verdict("missing-entries-are-reported") {
            val entry = Registries.get(RegistryKey.ENCHANTMENT, Key.key("sculk-itest:nope"))
            val tag = Registries.tag(RegistryKey.BLOCK, Key.key("sculk-itest:nope"))
            (entry is SculkFailure && tag is SculkFailure) to "entry $entry, tag $tag"
        }
        verdict("renamed-keys-resolve-on-every-version") {
            // chain became iron_chain inside the supported range; either name finds the item on every server.
            val old = Registries.get(RegistryKey.ITEM, Key.key("minecraft:chain")).getOrNull()?.key()?.asString()
            val new = Registries.get(RegistryKey.ITEM, Key.key("minecraft:iron_chain")).getOrNull()?.key()?.asString()
            val potion = Registries.get(RegistryKey.ENTITY_TYPE, Key.key("minecraft:potion"))
            val modern = Registries.get(RegistryKey.ENTITY_TYPE, Key.key("minecraft:splash_potion")).getOrNull()?.key()?.value() == "splash_potion"
            val split = if (modern) potion is SculkFailure.InvalidInput && potion.hint?.contains("split") == true else potion.getOrNull() != null
            (old != null && old == new && split) to "chain finds $old, iron_chain finds $new, potion gives $potion"
        }
        verdict("entry-set") {
            val set = RegistryEntrySet.parse(RegistryKey.BLOCK, listOf("#sculk-itest:tested", "minecraft:dirt", "!minecraft:granite")).getOrThrow()
            val resolved = set.resolve().getOrThrow().map { it.key().asString() }.toSet()
            val typo = RegistryEntrySet.parse(RegistryKey.BLOCK, listOf("minecraft:stoen")).getOrThrow().resolve()
            (resolved == setOf("minecraft:stone", "minecraft:dirt") && typo is SculkFailure) to "resolved $resolved, typo $typo"
        }
        verdict("blocks-resolve-to-materials") {
            val stone = Registries.get(RegistryKey.BLOCK, Key.key("minecraft:stone")).getOrNull()
            (stone != null && Material.STONE.isBlock) to "stone block type $stone"
        }
        dataFolder.mkdirs()
        File(dataFolder, "results.properties").writeText(results.entries.joinToString("\n", postfix = "\n") { (key, value) -> "$key=$value" })
        server.shutdown()
    }

    private fun verdict(name: String, block: () -> Pair<Boolean, String>) {
        results[name] = try {
            val (ok, detail) = block()
            if (ok) "PASS" else "FAIL: $detail"
        } catch (failure: Throwable) {
            "FAIL: ${failure::class.simpleName}: ${failure.message}"
        }
    }
}
