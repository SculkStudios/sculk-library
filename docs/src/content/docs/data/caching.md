---
title: Caching
description: Caffeine-backed in-memory cache over repositories — cache hits never touch the database.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

For data that is read far more often than it is written — player profiles, economy balances, settings — wrapping your repository with `SculkCache` eliminates repeated database round-trips. Cache hits are pure in-memory reads backed by [Caffeine](https://github.com/ben-manes/caffeine), one of the fastest JVM caches available.

## Creating a cache

<Tabs>
<TabItem label="Kotlin">
```kotlin
val repo = sculk.data.repository<PlayerData, UUID>()

val cache = sculk.data.cached(
    delegate = repo,
    idExtractor = PlayerData::uuid,
) {
    ttl = Duration.ofMinutes(10)   // evict after 10 minutes of no writes
    maxSize = 500                   // maximum entries before LRU eviction
}
```
</TabItem>
<TabItem label="Java">
```java
SculkRepository<PlayerData, UUID> repo =
    sculk.getData().repository(PlayerData.class, UUID.class);

SculkCache<PlayerData, UUID> cache = sculk.getData().cached(
    repo,
    PlayerData::uuid,
    cfg -> cfg
        .ttl(Duration.ofMinutes(10))
        .maxSize(500)
);
```
</TabItem>
</Tabs>

| Config  | Default    | Description |
| :------ | :--------- | :---------- |
| `ttl` | 10 minutes | How long after the last write before an entry is evicted |
| `maxSize` | 500 | Maximum number of entries — oldest are evicted when exceeded |

## Using the cache

`SculkCache` implements `SculkRepository` — you use the exact same API. No special code paths for cache vs. non-cache:

<Tabs>
<TabItem label="Kotlin">
```kotlin
sculk.scheduler.runAsync {
    // Cache hit → no DB call
    val result = cache.find(player.uniqueId)

    // Save → writes to DB AND updates the cache entry
    cache.save(updatedData)

    // Delete → removes from DB AND invalidates the cache entry
    cache.delete(player.uniqueId)
}
```
</TabItem>
<TabItem label="Java">
```java
sculk.getScheduler().runAsync(() -> {
    // Cache hit → no DB call
    SculkResult<PlayerData> result = cache.find(player.getUniqueId());

    // Save → DB write + cache update
    cache.save(updatedData);

    // Delete → DB delete + cache invalidation
    cache.delete(player.getUniqueId());
});
```
</TabItem>
</Tabs>

## Cache miss behaviour

On a cache miss, `find()` calls through to the repository, stores the result in the cache, then returns it. The load happens on the calling thread — always call from `runAsync`.

```
find(uuid)
  └─ cache hit?  → return cached value immediately
  └─ cache miss? → repo.find(uuid) → store in cache → return value
```

## Manual invalidation

If you write to the database through a path that bypasses the cache (e.g., a console command, external tool, or direct SQL), you must manually invalidate the affected entry:

<Tabs>
<TabItem label="Kotlin">
```kotlin
// Invalidate a single entry
cache.invalidate(uuid)

// Invalidate everything (e.g., after a bulk import)
cache.invalidateAll()
```
</TabItem>
<TabItem label="Java">
```java
// Invalidate a single entry
cache.invalidate(uuid);

// Invalidate everything
cache.invalidateAll();
```
</TabItem>
</Tabs>

## Recommended pattern: load on join, flush on quit

The most common pattern for player data — load into cache when the player joins, write back to DB when they leave:

<Tabs>
<TabItem label="Kotlin">
```kotlin
sculk.events.listen<PlayerJoinEvent> { event ->
    val player = event.player
    sculk.scheduler.runAsync {
        // Pre-load into cache; subsequent reads are instant
        cache.find(player.uniqueId)
    }
}

sculk.events.listen<PlayerQuitEvent> { event ->
    val player = event.player
    sculk.scheduler.runAsync {
        // Flush the current in-memory state to DB
        val result = cache.find(player.uniqueId)
        if (result is SculkResult.Success && result.value != null) {
            cache.save(result.value)
        }
        cache.invalidate(player.uniqueId)
    }
}
```
</TabItem>
<TabItem label="Java">
```java
sculk.getEvents().listen(PlayerJoinEvent.class, event -> {
    UUID uuid = event.getPlayer().getUniqueId();
    sculk.getScheduler().runAsync(() -> cache.find(uuid));
});

sculk.getEvents().listen(PlayerQuitEvent.class, event -> {
    UUID uuid = event.getPlayer().getUniqueId();
    sculk.getScheduler().runAsync(() -> {
        SculkResult<PlayerData> result = cache.find(uuid);
        if (result instanceof SculkResult.Success<PlayerData> ok && ok.getValue() != null) {
            cache.save(ok.getValue());
        }
        cache.invalidate(uuid);
    });
});
```
</TabItem>
</Tabs>

## Best practices

- **One cache per entity type** — create it at startup and share it everywhere.
- **Set `maxSize` to a sensible multiple of max-players** — for a 100-player server, 200–500 is plenty for player-scoped data.
- **Keep `ttl` short for externally-mutable data** — longer (30–60 min) is fine for data only your plugin writes.
- **Always go through the cache for hot paths** — bypassing it defeats the purpose and can cause stale reads.
- **Pre-warm on join** — loading on `PlayerJoinEvent` means first in-game access is always a cache hit.
