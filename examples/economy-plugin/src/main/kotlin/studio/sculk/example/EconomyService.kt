package studio.sculk.example

import studio.sculk.core.SculkResult
import studio.sculk.core.flatMap
import studio.sculk.data.cache.SculkCache
import java.util.Collections
import java.util.UUID

public class EconomyService(
    private val repository: SculkCache<EconomyAccount, UUID>,
    private val startingCoins: () -> Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val knownAccounts = Collections.synchronizedMap(linkedMapOf<UUID, EconomyAccount>())

    public fun balance(
        uuid: UUID,
        name: String,
    ): SculkResult<EconomyAccount> = account(uuid, name)

    public fun set(
        uuid: UUID,
        name: String,
        amount: Long,
    ): SculkResult<EconomyAccount> {
        if (amount < 0) return SculkResult.failure("Balance cannot be negative.")
        return account(uuid, name).flatMap { account ->
            account.name = name
            account.coins = amount
            account.updatedAt = clock()
            save(account)
        }
    }

    public fun deposit(
        uuid: UUID,
        name: String,
        amount: Long,
    ): SculkResult<EconomyAccount> {
        if (amount <= 0) return SculkResult.failure("Amount must be positive.")
        return account(uuid, name).flatMap { account ->
            account.name = name
            account.coins += amount
            account.updatedAt = clock()
            save(account)
        }
    }

    public fun withdraw(
        uuid: UUID,
        name: String,
        amount: Long,
    ): SculkResult<EconomyAccount> {
        if (amount <= 0) return SculkResult.failure("Amount must be positive.")
        return account(uuid, name).flatMap { account ->
            if (account.coins < amount) return@flatMap SculkResult.failure("Insufficient funds.")
            account.name = name
            account.coins -= amount
            account.updatedAt = clock()
            save(account)
        }
    }

    public fun transfer(
        fromUuid: UUID,
        fromName: String,
        toUuid: UUID,
        toName: String,
        amount: Long,
    ): SculkResult<Pair<EconomyAccount, EconomyAccount>> {
        if (fromUuid == toUuid) return SculkResult.failure("You cannot pay yourself.")
        if (amount <= 0) return SculkResult.failure("Amount must be positive.")

        return account(fromUuid, fromName).flatMap { from ->
            if (from.coins < amount) return@flatMap SculkResult.failure("Insufficient funds.")
            account(toUuid, toName).flatMap { to ->
                val now = clock()
                from.name = fromName
                from.coins -= amount
                from.updatedAt = now
                to.name = toName
                to.coins += amount
                to.updatedAt = now
                when (val saved = repository.saveAll(listOf(from, to))) {
                    is SculkResult.Success -> {
                        knownAccounts[from.uuid] = from
                        knownAccounts[to.uuid] = to
                        SculkResult.success(from to to)
                    }
                    is SculkResult.Failure -> saved
                }
            }
        }
    }

    public fun top(limit: Int): SculkResult<List<EconomyAccount>> = repository.findTopBy(limit.coerceAtLeast(1), EconomyAccount::coins)

    public fun flushKnown(): SculkResult<Unit> {
        val accounts = knownAccounts.values.toList()
        return if (accounts.isEmpty()) SculkResult.success(Unit) else repository.saveAll(accounts)
    }

    private fun account(
        uuid: UUID,
        name: String,
    ): SculkResult<EconomyAccount> =
        repository
            .findOrCreate(uuid) {
                val now = clock()
                EconomyAccount(uuid, name, startingCoins(), now, now)
            }.also { result ->
                if (result is SculkResult.Success) knownAccounts[uuid] = result.value
            }

    private fun save(account: EconomyAccount): SculkResult<EconomyAccount> =
        when (val saved = repository.save(account)) {
            is SculkResult.Success -> {
                knownAccounts[account.uuid] = account
                SculkResult.success(account)
            }
            is SculkResult.Failure -> saved
        }
}
