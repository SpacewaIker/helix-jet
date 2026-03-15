package spacewaiker.helixjet.core

import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.editor.HJEditorState
import java.util.WeakHashMap

object HJStateService {
    private val states = WeakHashMap<Editor, HJEditorState>()

    fun attach(editor: Editor) {
        states[editor] = HJEditorState()
    }

    fun detach(editor: Editor) {
        states.remove(editor)
    }

    fun get(editor: Editor): HJEditorState? = states[editor]
}