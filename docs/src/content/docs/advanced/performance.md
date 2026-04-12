---
title: Performance
description: How Sculk Studio is built for zero overhead on hot paths — main-thread safety, reflection caching, connection pooling, and benchmarks.
---

import { Tabs, TabItem } from '@astrojs/starlight/components';

Sculk Studio is designed so that using it never makes your plugin slower than hand-writing the equivalent code. Here's how each layer achieves that.

## Main-thread rule: never block

The Paper main thread processes game ticks. Blocking it — even for a few milliseconds — causes TPS drops visible to all players. Sculk Studio enforces a hard rule: **no blocking IO on the main thread**.

Every repository call is synchronous-blocking and must run inside `sculk.scheduler.runAsync { }`. For recurring background work, use `runAsyncRepeating`:

<Tabs>
<TabItem label="Kotlin">
```kotlin
// WRONG — blocks the main thread
val result = repo.find(player.uniqueId) // ← never do this on the main thread

// CORRECT — off the main thread, then back for Paper API calls
sculk.scheduler.runAsync {
    val result = repo.find(player.uniqueId)
    sculk.scheduler.runSync {
        player.sendMessage("Coins: ${(result as? SculkResult.Success)?.value?.coins}")
    }
}
```
</TabItem>
<TabItem label="Java">
```java
// CORRECT — CompletableFuture chains off the main thread
JavaRepository.wrap(repo)
    .find(player.getUniqueId())
    .thenAccept(result -> {
        if (result instanceof SculkResult.Success<PlayerData> ok) {
            sculk.getScheduler().runSync(() ->
                player.sendMessage("Coins: " + ok.getValue().coins())
            );
        }
    });
```
</TabItem>
</Tabs>

Violation is a programming error — Sculk Studio will log a warning if a repository method is called on the main thread.

---

## Reflection: startup only, cached forever

`sculk-data`'s ORM maps entity classes to SQL columns via reflection. This reflection runs **once at startup** when `sculk.data.repository<T, ID>()` is called. The mapping is stored in a `ConcurrentHashMap<Class<*>, TableMetadata>`.

Every subsequent `find`, `save`, or `delete` call uses the cached metadata — zero reflection per operation.

The same principle applies to `sculk-config`: field-to-YAML-key mapping is resolved once when `sculk.config.load<T>()` is first called, then cached for all future loads and reloads.

---

## Connection pooling with HikariCP

`sculk-data` uses [HikariCP](https://github.com/brettwooldridge/HikariCP) — the fastest JDBC connection pool on the JVM. Connections are validated on borrow and recycled automatically. You never manage connections manually.

Default pool sizes:

| Backend | Pool size | Why |
| :--- | :--- | :--- |
| SQLite | 1 | SQLite does not support concurrent writes |
| MySQL / MariaDB | 10 (configurable) | Handles concurrent async reads/writes |

Adjust MySQL pool size in `storage.yml`:

```yaml
# storage.yml
mysql:
  pool-size: 20   # raise if you have many concurrent async DB calls
```

---

## Caffeine cache: non-blocking reads

`SculkCache` is backed by [Caffeine](https://github.com/ben-manes/caffeine). Cache reads are non-blocking — they do not acquire any lock. Under high read concurrency (many players requesting data simultaneously), the cache scales without contention.

Cache misses are the only time a DB call happens — and only once per key per TTL window.

---

## GUI updates: no unnecessary packets

GUI inventory updates are applied directly to the Bukkit `Inventory` object inside the session. Sculk Studio does not send extra packets or re-render slots that haven't changed. Click handlers run synchronously within the same tick, so there is no re-render loop.

`session.refresh(slot)` updates a single slot. `session.refreshAll()` re-renders every slot once. Neither causes flicker because the client sees a contiguous update in a single tick.

---

## sculk-series: first-access caching

`SculkSeries` lookups resolve the key once on first access, then return from a `ConcurrentHashMap`. The worst-case cost is a single name-match scan through Bukkit's registry, which happens once per key per server lifetime.

Pre-warm critical lookups at startup so any missing-key warnings surface immediately rather than mid-game:

<Tabs>
<TabItem label="Kotlin">
```kotlin
override fun onEnable() {
    sculk = SculkPlatform.create(this) { ... }
    SculkSeries.material("diamond_sword")    // pre-warm
    SculkSeries.sound("entity.player.levelup")
}
```
</TabItem>
<TabItem label="Java">
```java
@Override
public void onEnable() {
    SculkSeries.material("diamond_sword");
    SculkSeries.sound("entity.player.levelup");
}
```
</TabItem>
</Tabs>

---

## JMH benchmarks

The `benchmarks/` module contains JMH microbenchmarks for the hottest paths:

| Benchmark | Target |
| :--- | :--- |
| Command dispatch (simple subcommand) | < 1 μs |
| GUI open (session creation) | Zero unnecessary allocations |
| Cache hit path | Zero DB calls |

Run them locally:

```bash
./gradlew :benchmarks:jmh
```

Results and historical runs are documented in `benchmarks/README.md`.

---

## Summary checklist

- Always use `sculk.scheduler.runAsync { }` for one-shot repository calls.
- Use `sculk.scheduler.runAsyncRepeating(delay, period) { }` for recurring background tasks (data flushes, heartbeats). The returned `SculkHandle` cancels the task when closed.
- Create repositories and caches once at startup — never per-request.
- Pre-warm `SculkSeries` keys at `onEnable`.
- Use `SculkCache` for any data that is read more than it is written.
- Adjust `pool-size` in `storage.yml` if you measure DB contention.
