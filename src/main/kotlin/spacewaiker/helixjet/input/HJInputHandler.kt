package spacewaiker.helixjet.input

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJState

sealed class HJInputKey {
    object Escape : HJInputKey()
    object Up : HJInputKey()
    object Down : HJInputKey()
    object Left : HJInputKey()
    object Right : HJInputKey()
    object Home : HJInputKey()
    object End : HJInputKey()
    object PageUp : HJInputKey()
    object PageDown : HJInputKey()
    object Space : HJInputKey()
    object Backspace : HJInputKey()
    object Tab : HJInputKey()
    object Del : HJInputKey()
    object Enter : HJInputKey()
    data class Character(val c: Char) : HJInputKey() {
        override fun toString(): String {
            return "$c"
        }
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        fun fromString(s: String): HJInputKey? {
            return when (s) {
                "esc" -> Escape
                "up" -> Up
                "down" -> Down
                "left" -> Left
                "right" -> Right
                "home" -> Home
                "end" -> End
                "pageup" -> PageUp
                "pagedown" -> PageDown
                "space" -> Space
                "backspace" -> Backspace
                "tab" -> Tab
                "del" -> Del
                "ret" -> Enter
                else if s.length == 1 -> Character(s[0])
                else -> {
                    logger.warn("Unknown character $s")
                    null
                }
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            Escape -> "Escape"
            Up -> "Up"
            Down -> "Down"
            Left -> "Left"
            Right -> "Right"
            Home -> "Home"
            End -> "End"
            PageUp -> "PageUp"
            PageDown -> "PageDown"
            Space -> "Space"
            Backspace -> "Backspace"
            Tab -> "Tab"
            Del -> "Del"
            Enter -> "Return"
            else -> ""
        }
    }
}

object HJInputHandler {
    private val logger = Logger.getInstance(this::class.java)

    fun handle(editor: Editor, key: HJInputKey): Boolean {
        if (HJState.mode == HJEditorMode.INSERT && key is HJInputKey.Character) {
            return false
        }

        var action = HJState.config.mapping[Pair(HJState.mode, key)]

        // try to fallback to normal mode if no action is found for select mode
        if (action == null && HJState.mode == HJEditorMode.SELECT) {
            action = HJState.config.mapping[Pair(HJEditorMode.NORMAL, key)]
        }

        if (action == null) {
            logger.info("Could not find an action corresponding to $key in mode ${HJState.mode}")
        } else {
            action.execute(editor)
        }

        return true
    }
}