package studio.sculk.example

import studio.sculk.core.SculkResult
import studio.sculk.data.cache.SculkCache
import java.util.Collections
import java.util.UUID

public class EconomyService(
    private val repository: SculkCache<EconomyAccount, UUID>,
    private val startingCoins: () -> Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val knownAccounts = Collections.synchronizedMap(linkedMapOf<UUID, EconomyAccount>())

    public suspend fun balance(
        uuid: UUID,
        name: String,
    ): SculkResult<EconomyAccount> = account(uuid, name)

    public suspend fun set(
        uuid: UUID,
        name: String,
        amount: Long,
    ): SculkResult<EconomyAccount> {
        if (amount < 0) return SculkResult.failure("Balance cannot be negative.")
        val account = account(uuid, name).valueOrReturn { return it }
        account.name = name
        account.coins = amount
        account.updatedAt = clock()
        return save(account)
    }

    public suspend fun deposit(
        uuid: UUID,
        name: String,
        amount: Long,
    ): SculkResult<EconomyAccount> {
        if (amount <= 0) return SculkResult.failure("Amount must be positive.")
        val account = account(uuid, name).valueOrReturn { return it }
        account.name = name
        account.coins += amount
        account.updatedAt = clock()
        return save(account)
    }

    public suspend fun withdraw(
        uuid: UUID,
        name: String,
        amount: Long,
    ): SculkResult<EconomyAccount> {
        if (amount <= 0) return SculkResult.failure("Amount must be positive.")
        val account = account(uuid, name).valueOrReturn { return it }
        if (account.coins < amount) return SculkResult.failure("Insufficient funds.")
        account.name = name
        account.coins -= amount
        account.updatedAt = clock()
        return save(account)
    }

    public suspend fun transfer(
        fromUuid: UUID,
        fromName: String,
        toUuid: UUID,
        toName: String,
        amount: Long,
    ): SculkResult<Pair<EconomyAccount, EconomyAccount>> {
        if (fromUuid == toUuid) return SculkResult.failure("You cannot pay yourself.")
        if (amount <= 0) return SculkResult.failure("Amount must be positive.")

        val from = account(fromUuid, fromName).valueOrReturn { return it }
        if (from.coins < amount) return SculkResult.failure("Insufficient funds.")
        val to = account(toUuid, toName).valueOrReturn { return it }

        val now = clock()
        from.name = fromName
        from.coins -= amount
        from.updatedAt = now
        to.name = toName
        to.coins += amount
        to.updatedAt = now
        return when (val saved = repository.saveAll(listOf(from, to))) {
            is SculkResult.Success -> {
                knownAccounts[from.uuid] = from
                knownAccounts[to.uuid] = to
                SculkResult.success(from to to)
            }
            is SculkResult.Failure -> saved
        }
    }

    public suspend fun top(limit: Int): SculkResult<List<EconomyAccount>> =
        repository.findTopBy(limit.coerceAtLeast(1), EconomyAccount::coins)

    public suspend fun flushKnown(): SculkResult<Unit> {
        val accounts = knownAccounts.values.toList()
        return if (accounts.isEmpty()) SculkResult.success(Unit) else repository.saveAll(accounts)
    }

    private suspend fun account(
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

    private suspend fun save(account: EconomyAccount): SculkResult<EconomyAccount> =
        when (val saved = repository.save(account)) {
            is SculkResult.Success -> {
                knownAccounts[account.uuid] = account
                SculkResult.success(account)
            }
            is SculkResult.Failure -> saved
        }

    /** Returns the success value, or runs [onFailure] (which must return non-locally) on failure. */
    private inline fun <T> SculkResult<T>.valueOrReturn(onFailure: (SculkResult.Failure) -> Nothing): T =
        when (this) {
            is SculkResult.Success -> value
            is SculkResult.Failure -> onFailure(this)
        }
}
