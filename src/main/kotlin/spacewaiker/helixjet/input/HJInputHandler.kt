package spacewaiker.helixjet.input

import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJEditorState

sealed class HJInputKey {
    object Escape : HJInputKey()
    data class Character(val c: Char) : HJInputKey()

    companion object {
        fun fromString(s: String): HJInputKey? {
            return when (s) {
                "esc" -> Escape
                else if s.length == 1 && s[0].isLetter() -> Character(s[0])
                else -> null
            }
        }
    }
}

object HJInputHandler {
    fun handle(editor: Editor, key: HJInputKey): Boolean {
        if (HJEditorState.mode == HJEditorMode.INSERT && key is HJInputKey.Character) {
            return false
        }

        var action = HJEditorState.config.mapping[Pair(HJEditorState.mode, key)]

        // try to fallback to normal mode if no action is found for select mode
        if (action == null && HJEditorState.mode == HJEditorMode.SELECT) {
            action = HJEditorState.config.mapping[Pair(HJEditorMode.NORMAL, key)]
        }

        action?.invoke(editor)

        return true
    }
}