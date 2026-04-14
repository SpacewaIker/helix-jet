package spacewaiker.helixjet.input

import com.intellij.openapi.diagnostic.Logger
import java.awt.event.KeyEvent

/**
 * Internal representation of input keys.
 */
sealed class HJInputKey(
    val shift: Boolean,
    val ctrl: Boolean,
    val alt: Boolean,
) {
    /**
     * Represents printable characters. Cannot be typed with/without Shift modifier on normal keyboards
     *
     * e.g. A <==> shift-a, : <==> shift-;
     */
    class Character(val char: Char, ctrl: Boolean, alt: Boolean) : HJInputKey(false, ctrl, alt)

    /**
     * Represents non-printable characters, or characters that can utilize a Shift modifier.
     *
     * e.g. Escape, space (because shift-space is a valid input)
     */
    class Other(val code: Int, shift: Boolean, ctrl: Boolean, alt: Boolean) : HJInputKey(shift, ctrl, alt)

    override fun toString(): String {
        val keyString = when (this) {
            is Character if char == '-' -> "minus"
            is Character if char == '<' -> "lt"
            is Character if char == '>' -> "gt"
            is Character -> char.toString()
            is Other -> when (code) {
                KeyEvent.VK_ESCAPE -> "esc"
                KeyEvent.VK_UP -> "up"
                KeyEvent.VK_DOWN -> "down"
                KeyEvent.VK_LEFT -> "left"
                KeyEvent.VK_RIGHT -> "right"
                KeyEvent.VK_HOME -> "home"
                KeyEvent.VK_END -> "end"
                KeyEvent.VK_PAGE_UP -> "pageup"
                KeyEvent.VK_PAGE_DOWN -> "pagedown"
                KeyEvent.VK_SPACE -> "space"
                KeyEvent.VK_BACK_SPACE -> "backspace"
                KeyEvent.VK_TAB -> "tab"
                KeyEvent.VK_DELETE -> "del"
                KeyEvent.VK_ENTER -> "ret"
                else -> "unknown"
            }
        }

        return if (shift || ctrl || alt) {
            val string = StringBuilder("<")
            if (ctrl) string.append("C-")
            if (alt) string.append("A-")
            if (shift) string.append("S-")
            string.append(keyString).append('>').toString()
        } else if (keyString.length > 1) {
            "<$keyString>"
        } else {
            keyString
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as HJInputKey

        if (shift != other.shift) return false
        if (ctrl != other.ctrl) return false
        if (alt != other.alt) return false

        return when (this) {
            is Character -> {
                other as Character
                this.char == other.char
            }

            is Other -> {
                other as Other
                this.code == other.code
            }
        }
    }

    override fun hashCode(): Int {
        var result = shift.hashCode()
        result = 31 * result + ctrl.hashCode()
        result = 31 * result + alt.hashCode()
        result = when (this) {
            is Character -> 31 * result + char.hashCode()
            is Other -> 31 * result + code.hashCode()
        }
        return result
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        fun fromKeyEvent(keyEvent: KeyEvent) = when (keyEvent.keyCode) {
            KeyEvent.VK_ESCAPE, KeyEvent.VK_UP, KeyEvent.VK_DOWN, KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME,
            KeyEvent.VK_END, KeyEvent.VK_PAGE_UP, KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_SPACE, KeyEvent.VK_BACK_SPACE,
            KeyEvent.VK_TAB, KeyEvent.VK_DELETE, KeyEvent.VK_ENTER,
                -> Other(keyEvent.keyCode, keyEvent.isShiftDown, keyEvent.isControlDown, keyEvent.isAltDown)

            // must check these directly instead of using keyEvent.keyChar because combinations such as C-n produce special characters
            in KeyEvent.VK_A..KeyEvent.VK_Z -> {
                var char = keyEvent.keyCode.toChar()
                if (!keyEvent.isShiftDown) char = char.lowercaseChar()
                Character(char, keyEvent.isControlDown, keyEvent.isAltDown)
            }
            // non-letter characters
            else if keyEvent.keyChar != KeyEvent.CHAR_UNDEFINED
                -> Character(keyEvent.keyChar, keyEvent.isControlDown, keyEvent.isAltDown)

            else -> {
                logger.debug("Unknown key event $keyEvent")
                null
            }
        }

        fun fromString(string: String): HJInputKey? {
            val parts = string.split('-').toList()
            val modifiers = parts.dropLast(1)

            val shift = modifiers.contains("S")
            val ctrl = modifiers.contains("C")
            val alt = modifiers.contains("A")
            val keyString = parts.last()

            return when (keyString) {
                "esc" -> Other(KeyEvent.VK_ESCAPE, shift, ctrl, alt)
                "up" -> Other(KeyEvent.VK_UP, shift, ctrl, alt)
                "down" -> Other(KeyEvent.VK_DOWN, shift, ctrl, alt)
                "left" -> Other(KeyEvent.VK_LEFT, shift, ctrl, alt)
                "right" -> Other(KeyEvent.VK_RIGHT, shift, ctrl, alt)
                "home" -> Other(KeyEvent.VK_HOME, shift, ctrl, alt)
                "end" -> Other(KeyEvent.VK_END, shift, ctrl, alt)
                "pageup" -> Other(KeyEvent.VK_PAGE_UP, shift, ctrl, alt)
                "pagedown" -> Other(KeyEvent.VK_PAGE_DOWN, shift, ctrl, alt)
                "space" -> Other(KeyEvent.VK_SPACE, shift, ctrl, alt)
                "backspace" -> Other(KeyEvent.VK_BACK_SPACE, shift, ctrl, alt)
                "tab" -> Other(KeyEvent.VK_TAB, shift, ctrl, alt)
                "del" -> Other(KeyEvent.VK_DELETE, shift, ctrl, alt)
                "ret" -> Other(KeyEvent.VK_ENTER, shift, ctrl, alt)
                "minus" -> Character('-', ctrl, alt)
                "gt" -> Character('>', ctrl, alt)
                "lt" -> Character('<', ctrl, alt)
                else if keyString.length == 1 -> Character(keyString[0], ctrl, alt)
                else -> null
            }
        }
    }
}