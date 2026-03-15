package spacewaiker.helixjet.core

import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.editor.HelixJetEditorState
import java.util.WeakHashMap

object HelixJetStateService {
    private val states = WeakHashMap<Editor, HelixJetEditorState>()

    fun attach(editor: Editor) { states[editor] = HelixJetEditorState()
    }
    fun detach(editor: Editor) { states.remove(editor) }
    fun get(editor: Editor): HelixJetEditorState? = states[editor]
}