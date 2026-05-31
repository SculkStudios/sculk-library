package studio.sculk.example

import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import studio.sculk.SculkResult
import studio.sculk.flatMap
import studio.sculk.items.ItemDescriptor
import studio.sculk.items.ItemKeys
import studio.sculk.items.toItemStack
import studio.sculk.map
import kotlin.random.Random

public class CrateService(
    private val settings: () -> CrateSettings,
    private val random: (Int) -> Int = { Random.nextInt(it) },
) {
    public fun validate(): List<String> =
        settings().crates.flatMap { (id, crate) ->
            buildList {
                if (id.isBlank()) add("Crate id cannot be blank.")
                if (crate.rewards.isEmpty()) add("Crate '$id' has no rewards.")
                crate.rewards.forEach { reward ->
                    if (reward.id.isBlank()) add("Crate '$id' has a reward with a blank id.")
                    if (reward.weight <= 0) add("Reward '${reward.id}' in crate '$id' must have positive weight.")
                }
            }
        }

    public fun crate(id: String): SculkResult<CrateDefinition> =
        settings()
            .crates[id.normalizeId()]
            ?.let { SculkResult.success(it) }
            ?: SculkResult.failure("Unknown crate '$id'.")

    public fun keyDescriptor(crateId: String): SculkResult<ItemDescriptor> =
        crate(crateId).map { crate ->
            crate.keyItem.copy(data = crate.keyItem.data + (CRATE_KEY to crateId.normalizeId()))
        }

    public fun keyItem(crateId: String): SculkResult<ItemStack> =
        keyDescriptor(crateId).flatMap { descriptor ->
            descriptor.toItemStack()?.let { SculkResult.success(it) }
                ?: SculkResult.failure("Crate key material '${descriptor.material}' is invalid.")
        }

    public fun isCrateKey(
        stack: ItemStack?,
        crateId: String,
    ): Boolean {
        val meta = stack?.itemMeta ?: return false
        val stored = meta.persistentDataContainer.get(ItemKeys.of(CRATE_KEY), PersistentDataType.STRING)
        return stored == crateId.normalizeId()
    }

    public fun roll(crateId: String): SculkResult<CrateReward> =
        crate(crateId).flatMap { crate ->
            val validRewards = crate.rewards.filter { it.weight > 0 }
            val total = validRewards.sumOf { it.weight }
            if (total <= 0) return@flatMap SculkResult.failure("Crate '$crateId' has no weighted rewards.")
            var ticket = random(total).coerceIn(0, total - 1)
            for (reward in validRewards) {
                ticket -= reward.weight
                if (ticket < 0) return@flatMap SculkResult.success(reward)
            }
            SculkResult.failure("Failed to roll crate '$crateId'.")
        }

    public fun chanceLore(crate: CrateDefinition): List<String> {
        val total =
            crate.rewards
                .filter { it.weight > 0 }
                .sumOf { it.weight }
                .coerceAtLeast(1)
        return crate.rewards.map { reward ->
            val chance = reward.weight * 100.0 / total
            "<gray>${reward.id}: <yellow>${"%.1f".format(chance)}%"
        }
    }

    private fun String.normalizeId(): String = trim().lowercase()

    public companion object {
        public const val CRATE_KEY: String = "crate_id"
    }
}
