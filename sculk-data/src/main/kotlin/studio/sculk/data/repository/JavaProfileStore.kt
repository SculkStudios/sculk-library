package studio.sculk.data.repository

import studio.sculk.SculkResult
import studio.sculk.annotation.SculkStable
import java.util.concurrent.CompletableFuture

/**
 * A Java-friendly view over a [PlayerProfileStore], exposing its `suspend` workflow as
 * [CompletableFuture]s that complete on a background thread.
 *
 * ```java
 * JavaProfileStore<PlayerData, UUID> profiles =
 *     sculk.getData().javaPlayerProfiles(repo, id -> new PlayerData(id, 0));
 *
 * profiles.getOrCreate(player.getUniqueId()).thenAccept(result -> {
 *     PlayerData data = result.getOrNull();
 *     sculk.getScheduler().runSync(player, () -> player.sendMessage("Welcome back!"));
 * });
 * ```
 */
@SculkStable
public class JavaProfileStore<T : Any, ID : Any> internal constructor(private val store: PlayerProfileStore<T, ID>) {
    /** Loads the profile for [id], creating and persisting a default via the store's factory if absent. */
    @SculkStable
    public fun getOrCreate(id: ID): CompletableFuture<SculkResult<T>> = dataFuture { store.getOrCreate(id) }

    /** Saves [profile]. */
    @SculkStable
    public fun save(profile: T): CompletableFuture<SculkResult<Unit>> = dataFuture { store.save(profile) }
}
