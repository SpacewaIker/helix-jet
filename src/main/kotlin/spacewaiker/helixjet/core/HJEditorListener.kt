package spacewaiker.helixjet.core

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJEditorState

class HJEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.settings.isBlockCursor = HJEditorState.mode != HJEditorMode.INSERT
    }

    override fun editorReleased(event: EditorFactoryEvent) {
    }
}