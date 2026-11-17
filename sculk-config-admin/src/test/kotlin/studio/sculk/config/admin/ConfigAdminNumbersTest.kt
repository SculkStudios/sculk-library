package studio.sculk.config.admin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import studio.sculk.config.ConfigField

/** How the editor turns sliders and clicks into the text it saves. */
class ConfigAdminNumbersTest {
    @Test
    fun `an untouched slider saves nothing, however the file writes the number`() {
        assertNull(editedNumber(ConfigField.Kind.DECIMAL, "0.1", 0.1f))
        assertNull(editedNumber(ConfigField.Kind.DECIMAL, "1", 1f))
        assertNull(editedNumber(ConfigField.Kind.DECIMAL, "0.123456789", 0.123456789f))
        assertNull(editedNumber(ConfigField.Kind.WHOLE, "27", 27f))
    }

    @Test
    fun `a moved slider saves the float as written, without the noise of widening it`() {
        assertEquals("0.35", editedNumber(ConfigField.Kind.DECIMAL, "0.1", 0.35f))
        assertEquals("45", editedNumber(ConfigField.Kind.WHOLE, "27", 45f))
    }

    @Test
    fun `a whole setting beyond the integers a float holds exactly is edited as text`() {
        assertTrue(slides(ConfigField.Kind.WHOLE, 1.0..54.0))
        assertFalse(slides(ConfigField.Kind.WHOLE, 0.0..16_777_217.0))
        assertFalse(slides(ConfigField.Kind.WHOLE, 0.0..Long.MAX_VALUE.toDouble()))
        assertTrue(slides(ConfigField.Kind.DECIMAL, 0.0..1e30))
        assertFalse(slides(ConfigField.Kind.DECIMAL, 5.0..5.0))
    }

    @Test
    fun `a click on a decimal moves it by plain decimal text and keeps it in range`() {
        assertEquals("0.11", decimalStep("0.1", 0.0..1.0, down = false, large = false))
        assertEquals("0.2", decimalStep("0.1", 0.0..1.0, down = false, large = true))
        assertEquals("0", decimalStep("0.05", 0.0..1.0, down = true, large = true))
        assertEquals("1", decimalStep("0.95", 0.0..1.0, down = false, large = true))
    }
}
