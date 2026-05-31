package studio.sculk.data.repository

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable

/**
 * Small workflow helper for UUID-first player profile data.
 */
@SculkStable
public class PlayerProfileStore<T : Any, ID : Any> public constructor(
    private val repository: SculkRepository<T, ID>,
    private val create: (ID) -> T,
) {
    public suspend fun getOrCreate(id: ID): SculkResult<T> =
        when (val result = repository.find(id)) {
            is SculkResult.Success -> {
                val existing = result.value
                if (existing != null) {
                    SculkResult.Success(existing)
                } else {
                    val created = create(id)
                    when (val saved = repository.save(created)) {
                        is SculkResult.Success -> SculkResult.Success(created)
                        is SculkResult.Failure -> SculkResult.Failure(saved.message, saved.cause)
                    }
                }
            }
            is SculkResult.Failure -> SculkResult.Failure(result.message, result.cause)
        }

    public suspend fun save(profile: T): SculkResult<Unit> = repository.save(profile)
}
