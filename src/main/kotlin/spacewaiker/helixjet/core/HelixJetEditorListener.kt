package spacewaiker.helixjet.core

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

class HelixJetEditorListener : EditorFactoryListener {
    override fun editorCreated(event: EditorFactoryEvent) {
        HelixJetStateService.attach(event.editor)
    }
    override fun editorReleased(event: EditorFactoryEvent) {
        HelixJetStateService.detach(event.editor)
    }
}