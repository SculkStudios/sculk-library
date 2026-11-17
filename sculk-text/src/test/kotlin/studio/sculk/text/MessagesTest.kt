package studio.sculk.text

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import studio.sculk.annotation.SculkExperimental

@OptIn(SculkExperimental::class)
class MessagesTest {
    private val plain = PlainTextComponentSerializer.plainText()

    @Test
    fun `mm renders a template with its values`() {
        assertEquals("Balance: 12", plain.serialize("<primary>Balance: <balance>".mm("balance" to 12)))
    }

    @Test
    fun `mm prints markup in a value instead of obeying it`() {
        assertEquals("Hi <red>Admin", plain.serialize("Hi <name>".mm("name" to "<red>Admin")))
    }

    @Test
    fun `send delivers the rendered template to the audience`() {
        val received = mutableListOf<Component>()
        val audience =
            object : Audience {
                override fun sendMessage(message: Component) {
                    received += message
                }
            }
        audience.send("<muted>Welcome back, visit <visits>.", "visits" to 3)
        assertEquals(listOf("Welcome back, visit 3."), received.map(plain::serialize))
    }
}
