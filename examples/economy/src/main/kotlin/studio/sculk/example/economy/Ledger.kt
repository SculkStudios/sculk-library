package studio.sculk.example.economy

import java.util.UUID

/**
 * Every account's balance. Thread-safe: on Folia, players pay each other from different region
 * threads at once, so every read and change goes through one lock rather than relying on the
 * caller's thread. Each operation is a few map lookups, so the lock is never held for long.
 */
class Ledger(private val startingBalance: Double, saved: Map<UUID, Double> = emptyMap()) {
    private val lock = Any()
    private val balances = HashMap(saved)

    fun balance(account: UUID): Double = synchronized(lock) { balances[account] ?: startingBalance }

    /** Opens [account] with the starting balance; returns false when it already existed. */
    fun open(account: UUID): Boolean = synchronized(lock) { balances.putIfAbsent(account, startingBalance) == null }

    fun deposit(account: UUID, amount: Double) {
        require(amount > 0) { "A deposit is positive, but was $amount." }
        synchronized(lock) { balances[account] = (balances[account] ?: startingBalance) + amount }
    }

    /** Takes [amount] if the account holds it; returns whether it did. */
    fun withdraw(account: UUID, amount: Double): Boolean = synchronized(lock) {
        val current = balances[account] ?: startingBalance
        if (current < amount) return false
        balances[account] = current - amount
        true
    }

    /** Moves [amount] between two accounts, all or nothing. */
    fun transfer(from: UUID, to: UUID, amount: Double): Boolean = synchronized(lock) {
        if (from == to || !withdraw(from, amount)) return false
        deposit(to, amount)
        true
    }

    fun reset() {
        synchronized(lock) { balances.clear() }
    }

    fun snapshot(): Map<UUID, Double> = synchronized(lock) { balances.toMap() }
}
