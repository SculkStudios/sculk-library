package studio.sculk.bench

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import net.kyori.adventure.text.Component
import studio.sculk.text.Text
import studio.sculk.text.Theme
import studio.sculk.text.ThemeStyle

/**
 * Rendering cost for the two templates every plugin sends most: a short themed line with values,
 * and a gradient header. The template cache is warm, as on a running server.
 */
@State(Scope.Benchmark)
class TextBenchmark {
    private lateinit var text: Text

    @Setup
    fun setUp() {
        text = Text(Theme.DEFAULT.with("header", ThemeStyle.Gradient(listOf("#8B7CF6", "#5EC4D6"))))
        render()
        header()
    }

    @Benchmark
    fun render(): Component = text.render("<primary>Sent <value><amount></value> coins to <secondary><player>.", "amount" to 1_250, "player" to "Steve")

    @Benchmark
    fun header(): Component = text.render("<header>Shop</header> <muted>page <page> of <pages>", "page" to 2, "pages" to 9)
}
