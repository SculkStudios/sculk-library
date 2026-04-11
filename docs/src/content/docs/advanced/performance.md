---
title: Performance
description: How Sculk Studio achieves zero-overhead on hot paths.
---

## Zero blocking on the main thread

Every database and IO operation in Sculk Studio is async by design. Repository methods block their calling thread — always call them from `sculk.scheduler.runAsync { }` or a `CompletableFuture`.

```kotlin
sculk.scheduler.runAsync {
    val data = cache.find(player.uniqueId)
    sculk.scheduler.runSync {
        // back on main thread
        player.sendMessage("Loaded: $data")
    }
}
```

## Reflection at startup only

`sculk-data`'s ORM mapper reflects on entity classes once at startup and caches the result in a `ConcurrentHashMap`. There is zero reflection overhead per save/load call.

## GUI batching

GUI inventory updates are applied directly to the Bukkit `Inventory` object in the session. No extra packet sending, no double-update logic. Updates triggered by click handlers run within the same tick.

## Connection pooling

`sculk-data` uses [HikariCP](https://github.com/brettwooldridge/HikariCP) for connection pooling:

- **SQLite** — single connection (pool size 1); SQLite does not support concurrent writes.
- **MySQL/MariaDB** — default pool size 10; configurable via `storage.yml`.

HikariCP validates connections on borrow and recycles stale ones automatically.

## Cache layer

`SculkCache` is backed by [Caffeine](https://github.com/ben-manes/caffeine), one of the highest-throughput JVM caches available. Cache lookups are non-blocking reads from a concurrent hash structure.

## JMH benchmarks

The `benchmarks/` module contains JMH microbenchmarks for:

- Command dispatch (target: < 1 μs)
- GUI open (target: zero unnecessary allocations)
- Cache hit path (target: zero DB calls)

Run them with:

```
./gradlew :benchmarks:jmh
```

Results are documented in `benchmarks/README.md`.
