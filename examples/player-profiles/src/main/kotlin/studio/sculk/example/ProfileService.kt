package studio.sculk.example

import studio.sculk.SculkResult
import studio.sculk.data.repository.PlayerProfileStore
import java.util.Collections
import java.util.UUID

public class ProfileService(
    private val store: PlayerProfileStore<PlayerProfile, UUID>,
    private val startingCoins: Long = 100,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val loaded = Collections.synchronizedMap(linkedMapOf<UUID, PlayerProfile>())

    public suspend fun loadForJoin(
        uuid: UUID,
        name: String,
    ): SculkResult<PlayerProfile> {
        val profile = store.getOrCreate(uuid).valueOrReturn { return it }
        profile.name = name
        profile.joins += 1
        profile.lastSeen = clock()
        if (profile.firstSeen == 0L) profile.firstSeen = profile.lastSeen
        return saveLoaded(uuid, profile)
    }

    public suspend fun profile(
        uuid: UUID,
        fallbackName: String,
    ): SculkResult<PlayerProfile> {
        loaded[uuid]?.let { return SculkResult.success(it) }
        val profile = store.getOrCreate(uuid).valueOrReturn { return it }
        if (profile.name.isBlank()) profile.name = fallbackName
        return saveLoaded(uuid, profile)
    }

    public suspend fun saveAndUnload(uuid: UUID): SculkResult<Unit> {
        val profile = loaded.remove(uuid) ?: return SculkResult.success(Unit)
        profile.lastSeen = clock()
        return store.save(profile)
    }

    public suspend fun flushLoaded(): SculkResult<Unit> {
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

    private suspend fun saveLoaded(
        uuid: UUID,
        profile: PlayerProfile,
    ): SculkResult<PlayerProfile> =
        when (val saved = store.save(profile)) {
            is SculkResult.Success -> {
                loaded[uuid] = profile
                SculkResult.success(profile)
            }
            is SculkResult.Failure -> saved
        }

    private inline fun <T> SculkResult<T>.valueOrReturn(onFailure: (SculkResult.Failure) -> Nothing): T =
        when (this) {
            is SculkResult.Success -> value
            is SculkResult.Failure -> onFailure(this)
        }
}
