package studio.sculk.example

import studio.sculk.core.SculkResult
import studio.sculk.core.flatMap
import studio.sculk.data.repository.PlayerProfileStore
import java.util.Collections
import java.util.UUID

public class ProfileService(
    private val store: PlayerProfileStore<PlayerProfile, UUID>,
    private val startingCoins: Long = 100,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val loaded = Collections.synchronizedMap(linkedMapOf<UUID, PlayerProfile>())

    public fun loadForJoin(
        uuid: UUID,
        name: String,
    ): SculkResult<PlayerProfile> =
        store
            .getOrCreate(uuid)
            .flatMap { profile ->
                profile.name = name
                profile.joins += 1
                profile.lastSeen = clock()
                if (profile.firstSeen == 0L) profile.firstSeen = profile.lastSeen
                store.save(profile).mapProfile(profile)
            }.also { result ->
                if (result is SculkResult.Success) loaded[uuid] = result.value
            }

    public fun profile(
        uuid: UUID,
        fallbackName: String,
    ): SculkResult<PlayerProfile> =
        loaded[uuid]?.let { SculkResult.success(it) }
            ?: store
                .getOrCreate(uuid)
                .flatMap { profile ->
                    if (profile.name.isBlank()) profile.name = fallbackName
                    store.save(profile).mapProfile(profile)
                }.also { result ->
                    if (result is SculkResult.Success) loaded[uuid] = result.value
                }

    public fun saveAndUnload(uuid: UUID): SculkResult<Unit> {
        val profile = loaded.remove(uuid) ?: return SculkResult.success(Unit)
        profile.lastSeen = clock()
        return store.save(profile)
    }

    public fun flushLoaded(): SculkResult<Unit> {
        val profiles = loaded.values.toList()
        for (profile in profiles) {
            profile.lastSeen = clock()
            val saved = store.save(profile)
            if (saved is SculkResult.Failure) return saved
        }
        return SculkResult.success(Unit)
    }

    public fun createDefault(uuid: UUID): PlayerProfile {
        val now = clock()
        return PlayerProfile(uuid, "", now, now, 0, 0, 0, startingCoins)
    }

    private fun SculkResult<Unit>.mapProfile(profile: PlayerProfile): SculkResult<PlayerProfile> =
        when (this) {
            is SculkResult.Success -> SculkResult.success(profile)
            is SculkResult.Failure -> this
        }
}
