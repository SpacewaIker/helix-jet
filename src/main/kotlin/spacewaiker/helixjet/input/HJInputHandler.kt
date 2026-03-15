package spacewaiker.helixjet.input

import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.core.HJStateService
import spacewaiker.helixjet.editor.HJEditorMode

sealed class HJInputKey {
    object Escape : HJInputKey()
    data class Character(val c: Char) : HJInputKey()
}

object HJInputHandler {
    fun handle(editor: Editor, key: HJInputKey): Boolean {
        val state = HJStateService.get(editor) ?: return false


        if (state.mode == HJEditorMode.INSERT && key is HJInputKey.Character) {
            return false
        }

        state.config.mapping[Pair(state.mode, key)]?.invoke(editor, state)
        return true
    }
}