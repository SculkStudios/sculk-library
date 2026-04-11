---
title: Caching
description: Caffeine-backed in-memory cache layer over repositories.
---

`SculkCache` wraps a `SculkRepository` with a Caffeine cache. Cache hits never touch the database.

## Creating a cache

```kotlin
val cache = sculk.data.cached(
    delegate = repo,
    idExtractor = PlayerData::uuid,
) {
    ttl = Duration.ofMinutes(10)
    maxSize = 500
}
```

| Config | Default | Description |
|---|---|---|
| `ttl` | 10 minutes | Time-to-live after last write |
| `maxSize` | 500 | Maximum number of cached entries |

## Using a cache

`SculkCache` implements `SculkRepository`, so all the same methods work:

```kotlin
val result = cache.find(player.uniqueId)   // cache hit → no DB call
cache.save(updatedData)                     // saves to DB + updates cache
cache.delete(player.uniqueId)              // removes from DB + invalidates cache entry
```

## Cache miss behaviour

On a cache miss, `find()` delegates to the underlying repository, then stores the result in the cache before returning. The loader runs on whichever thread called `find()` — run it async.

## Invalidation

Calling `delete()` automatically removes the entry from the cache. If you update data externally (e.g., via console commands that write directly), call:

```kotlin
cache.invalidate(uuid)
```

## Best practices

- Create one cache per entity type at startup; share it across all code that reads that entity.
- Set `maxSize` to a comfortable multiple of your expected concurrent online players.
- Keep `ttl` short for data that can change from external sources; longer for mostly-read data.
- Always read from the cache, never bypass it for hot paths.
