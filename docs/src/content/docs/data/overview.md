---
title: Data Overview
description: Repository pattern over SQLite and MySQL with async-first operations.
---

`sculk-data` provides a repository pattern for persistent storage. SQLite is the default; MySQL/MariaDB is supported via config.

## Defining an entity

```kotlin
@Table("player_data")
data class PlayerData(
    @PrimaryKey @Column("uuid") val uuid: UUID,
    @Column("coins") val coins: Long,
    @Column("homes") val homes: Int,
)
```

## Creating a repository

```kotlin
val repo = sculk.data.repository<PlayerData, UUID>()
```

## Operations

All operations are synchronous and return `SculkResult<T>`:

```kotlin
// Find by primary key
val result = repo.find(player.uniqueId)
when (result) {
    is SculkResult.Success -> println("Coins: ${result.value?.coins}")
    is SculkResult.Failure -> logger.warning(result.message)
}

// Save (insert or update)
repo.save(PlayerData(player.uniqueId, coins = 100, homes = 0))

// Delete
repo.delete(player.uniqueId)

// Check existence
if (repo.exists(player.uniqueId)) { ... }

// Get all rows
val all = repo.findAll()
```

## Running async

Always call repository methods off the main thread:

```kotlin
sculk.scheduler.runAsync {
    val result = repo.find(player.uniqueId)
    sculk.scheduler.runSync {
        // back on main thread — safe to call Paper APIs
        if (result is SculkResult.Success) {
            player.sendMessage("Your coins: ${result.value?.coins}")
        }
    }
}
```

## Storage configuration

`storage.yml` is auto-generated in the plugin data folder:

```yaml
type: sqlite
sqlite:
  file: data.db
mysql:
  host: localhost
  port: 3306
  database: sculk
  username: root
  password: ""
```

Switch to MySQL by setting `type: mysql`.

## Java API

```java
JavaRepository<PlayerData, UUID> repo = JavaRepository.wrap(
    sculk.getData().repository(PlayerData.class, UUID.class)
);

CompletableFuture<SculkResult<PlayerData>> future = repo.find(player.getUniqueId());
future.thenAccept(result -> { ... });
```
