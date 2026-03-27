package spacewaiker.helixjet.core

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.input.HJMouseListener

class HJEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        editor.settings.isBlockCursor = HJState.mode != HJEditorMode.INSERT

        // create entry for this editor in HJEditors map
        HJState.getHJEditor(editor)

        editor.addEditorMouseListener(HJMouseListener())
    }

    override fun editorReleased(event: EditorFactoryEvent) {
    }
}