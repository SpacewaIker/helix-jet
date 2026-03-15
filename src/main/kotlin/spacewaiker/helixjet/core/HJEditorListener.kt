package spacewaiker.helixjet.core

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import spacewaiker.helixjet.editor.HJEditorMode

class HJEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        HJStateService.attach(event.editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        HJStateService.detach(event.editor)
    }
}