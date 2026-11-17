package studio.sculk.example.economy

import java.util.Locale
import java.util.UUID
import kotlin.math.roundToLong

/**
 * Every account's balance, in cents so sums never drift the way doubles do. Thread-safe: on Folia,
 * players pay each other from different region threads at once, so every read and change goes
 * through one lock rather than relying on the caller's thread. Each operation is a few map lookups,
 * so the lock is never held for long.
 */
class Ledger(private val startingBalance: Long, saved: Map<UUID, Long> = emptyMap()) {
    private val lock = Any()
    private val balances = HashMap(saved)

    fun balance(account: UUID): Long = synchronized(lock) { balances[account] ?: startingBalance }

    /** Opens [account] with the starting balance; returns false when it already existed. */
    fun open(account: UUID): Boolean = synchronized(lock) { balances.putIfAbsent(account, startingBalance) == null }

    fun deposit(account: UUID, cents: Long) {
        require(cents > 0) { "A deposit is positive, but was $cents cents." }
        synchronized(lock) { balances[account] = Math.addExact(balances[account] ?: startingBalance, cents) }
    }

    /** Takes [cents] if the account holds them; returns whether it did. */
    fun withdraw(account: UUID, cents: Long): Boolean = synchronized(lock) {
        val current = balances[account] ?: startingBalance
        if (current < cents) return false
        balances[account] = current - cents
        true
    }

    /** Moves [cents] between two accounts, all or nothing. */
    fun transfer(from: UUID, to: UUID, cents: Long): Boolean = synchronized(lock) {
        if (from == to || !withdraw(from, cents)) return false
        deposit(to, cents)
        true
    }

    fun reset() {
        synchronized(lock) { balances.clear() }
    }

    fun snapshot(): Map<UUID, Long> = synchronized(lock) { balances.toMap() }
}

/** [amount], as typed in a command or a config file, in cents. */
fun cents(amount: Double): Long = (amount * 100).roundToLong()

/** [cents] as shown to players: `1,234.50 coins`. */
fun formatMoney(cents: Long, currency: String): String = String.format(Locale.ROOT, "%,d.%02d %s", cents / 100, cents % 100, currency)
