package studio.sculk.itest.bot

import java.io.File
import java.io.PrintStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** One line the test client printed: a kind such as `WINDOW` and its fields. */
class BotEvent(val kind: String, val fields: List<String>) {
    override fun toString(): String = (listOf(kind) + fields).joinToString(" ")
}

/**
 * A real Minecraft client joining this server as [name], driven line by line (ADR-0029). The jar
 * and the Java that runs it come from the system properties the build sets on servers that ask for
 * a client. Blocking: call it from a thread that may wait, never from a server thread.
 */
class BotDriver(private val name: String, port: Int) : AutoCloseable {
    private val events = LinkedBlockingQueue<BotEvent>()
    private val seen = mutableListOf<BotEvent>()

    // The newest event per kind and first field, for events that report a state rather than a change.
    private val latest = HashMap<String, BotEvent>()
    private val process: Process
    private val input: PrintStream
    private val log = File("bot-$name.log")

    init {
        val jar = requireNotNull(System.getProperty("sculk.itest.bot.jar")) { "this server was not started with a test client" }
        val java = requireNotNull(System.getProperty("sculk.itest.bot.java")) { "this server was not started with a test client" }
        process = ProcessBuilder(java, "-Xmx256m", "-jar", jar, "127.0.0.1", port.toString(), name)
            .redirectError(ProcessBuilder.Redirect.appendTo(log))
            .start()
        input = PrintStream(process.outputStream, true, Charsets.UTF_8)
        thread(name = "bot-$name", isDaemon = true) {
            process.inputStream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
                val parts = line.split('\t')
                events.put(BotEvent(parts.first(), parts.drop(1)))
            }
        }
    }

    /** The next event matching [kind] and [matches] within [seconds], skipping others. */
    fun await(kind: String, seconds: Long = 30, matches: (BotEvent) -> Boolean = { true }): BotEvent {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (true) {
            val left = deadline - System.nanoTime()
            val event = events.poll(minOf(left.coerceAtLeast(0), TimeUnit.SECONDS.toNanos(1)), TimeUnit.NANOSECONDS)
            if (event == null) {
                if (!process.isAlive && events.isEmpty()) error("$name exited with ${process.exitValue()}: ${log.readLines().takeLast(5)}")
                if (System.nanoTime() >= deadline) error("$name saw no $kind within ${seconds}s; it saw ${seen.takeLast(12)}")
                continue
            }
            see(event)
            if (event.kind == "DISCONNECTED" && kind != "DISCONNECTED") error("$name was disconnected: ${event.fields}")
            if (event.kind == kind && matches(event)) return event
        }
    }

    /**
     * Waits until the state that [kind] events report for [key], their first field, matches: at once
     * when the newest such event already did, even if an earlier wait skipped past it.
     */
    fun awaitState(kind: String, key: String, seconds: Long = 30, matches: (BotEvent) -> Boolean): BotEvent {
        latest["$kind\t$key"]?.takeIf(matches)?.let { return it }
        return await(kind, seconds) { it.fields.firstOrNull() == key && matches(it) }
    }

    private fun see(event: BotEvent) {
        seen += event
        event.fields.firstOrNull()?.let { latest["${event.kind}\t$it"] = event }
    }

    /** Types [text] into the open anvil's rename field. */
    fun rename(text: String) = send("RENAME", text)

    /** Selects the open merchant's offer at [index], which moves its ingredients into place. */
    fun selectTrade(index: Int) = send("TRADE", index.toString())

    /** Whether an event matching [kind] and [matches] arrives within [millis]; for proving something did not happen. */
    fun sees(kind: String, millis: Long, matches: (BotEvent) -> Boolean = { true }): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis)
        while (true) {
            val left = deadline - System.nanoTime()
            if (left <= 0) return false
            val event = events.poll(left, TimeUnit.NANOSECONDS) ?: return false
            see(event)
            if (event.kind == kind && matches(event)) return true
        }
    }

    /**
     * Waits until the open window shows [name] in [slot], whether the server sent that slot alone or
     * the whole window, as it does when it resynchronises after a refused click.
     */
    fun awaitSlot(slot: Int, name: String, seconds: Long = 30): BotEvent {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (true) {
            val left = TimeUnit.NANOSECONDS.toSeconds(deadline - System.nanoTime()).coerceAtLeast(1)
            val event = awaitAny(setOf("SLOT", "CONTENT"), left)
            val shown = if (event.kind == "SLOT") event.fields[2].toInt() == slot && event.fields[3] == name else event.slots()[slot] == name
            if (shown) return event
            if (System.nanoTime() >= deadline) error("$name never showed in slot $slot; last ${seen.takeLast(6)}")
        }
    }

    private fun awaitAny(kinds: Set<String>, seconds: Long): BotEvent {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
        while (true) {
            val event = events.poll(1, TimeUnit.SECONDS)
            if (event == null) {
                if (!process.isAlive && events.isEmpty()) error("$name exited with ${process.exitValue()}: ${log.readLines().takeLast(5)}")
                if (System.nanoTime() >= deadline) error("$name saw none of $kinds within ${seconds}s; it saw ${seen.takeLast(12)}")
                continue
            }
            seen += event
            if (event.kind == "DISCONNECTED") error("$name was disconnected: ${event.fields}")
            if (event.kind in kinds) return event
        }
    }

    fun click(slot: Int, kind: String = "LEFT", hotbar: Int? = null) {
        send("CLICK", slot.toString(), kind, *listOfNotNull(hotbar?.toString()).toTypedArray())
    }

    fun closeWindow() = send("CLOSE")

    /** Presses the dialog button labelled [label] with [values] for its inputs, as `key` to text. */
    fun answerDialog(label: String, vararg values: Pair<String, Any>) =
        send("DIALOGCLICK", label, *values.map { (key, value) -> "$key=$value" }.toTypedArray())

    fun command(text: String) = send("COMMAND", text)

    private fun send(vararg fields: String) {
        input.println(fields.joinToString("\t"))
    }

    override fun close() {
        runCatching { send("QUIT") }
        if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly()
    }
}

/** The slots of a `CONTENT` event as slot number to name. */
fun BotEvent.slots(): Map<Int, String> = fields.drop(2).associate { field ->
    val (slot, name) = field.split(':', limit = 2)
    slot.toInt() to name
}
