package studio.sculk.example

import studio.sculk.core.SculkResult
import studio.sculk.data.cache.SculkCache
import studio.sculk.items.ItemDescriptor
import java.util.UUID

public class KitService(
    private val settings: () -> KitSettings,
    private val cooldowns: SculkCache<KitCooldown, String>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    public fun kit(id: String): SculkResult<KitDefinition> =
        settings().kits[id.normalizeId()]?.let { SculkResult.success(it) }
            ?: SculkResult.failure("Unknown kit '$id'.")

    public fun validate(): List<String> =
        settings().kits.flatMap { (id, kit) ->
            buildList {
                if (id.isBlank()) add("Kit id cannot be blank.")
                if (kit.items.isEmpty()) add("Kit '$id' has no items.")
                if (kit.cooldownSeconds < 0) add("Kit '$id' has a negative cooldown.")
            }
        }

    public suspend fun claimStatus(
        uuid: UUID,
        kitId: String,
    ): SculkResult<KitClaimStatus> {
        val kit =
            when (val result = kit(kitId)) {
                is SculkResult.Success -> result.value
                is SculkResult.Failure -> return result
            }
        return cooldowns.find(cooldownId(uuid, kitId)).mapStatus { cooldown ->
            if (cooldown == null) {
                KitClaimStatus(true, 0)
            } else {
                val expiresAt = cooldown.lastClaimedAt + kit.cooldownSeconds * 1000L
                val remaining = (expiresAt - clock()).coerceAtLeast(0)
                KitClaimStatus(remaining == 0L, remaining)
            }
        }
    }

    public suspend fun recordClaim(
        uuid: UUID,
        kitId: String,
    ): SculkResult<Unit> =
        cooldowns.save(
            KitCooldown(
                id = cooldownId(uuid, kitId),
                uuid = uuid,
                kitId = kitId.normalizeId(),
                lastClaimedAt = clock(),
            ),
        )

    public fun kitItems(kitId: String): SculkResult<List<ItemDescriptor>> =
        kit(kitId).mapStatus { kit ->
            kit.items.map { descriptor ->
                descriptor.copy(data = descriptor.data + (KIT_ID_KEY to kitId.normalizeId()))
            }
        }

    public fun permissionFor(
        kitId: String,
        kit: KitDefinition,
    ): String = kit.permission ?: "kits.use.${kitId.normalizeId()}"

    public fun formatRemaining(millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }

    private fun cooldownId(
        uuid: UUID,
        kitId: String,
    ): String = "$uuid:${kitId.normalizeId()}"

    private fun String.normalizeId(): String = trim().lowercase()

    private inline fun <T, R> SculkResult<T>.mapStatus(transform: (T) -> R): SculkResult<R> =
        when (this) {
            is SculkResult.Success -> SculkResult.success(transform(value))
            is SculkResult.Failure -> this
        }

    public companion object {
        public const val KIT_ID_KEY: String = "kit_id"
    }
}
