package spacewaiker.helixjet.core

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import spacewaiker.helixjet.input.HJEscapeHandler
import spacewaiker.helixjet.input.HJTypedHandler

class HJStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // typed handler
        val typedAction = TypedAction.getInstance()
        val handler = HJTypedHandler()
        handler.setOriginalHandler(typedAction.rawHandler)
        typedAction.setupRawHandler(handler)

        // action handler
        val actionManager = EditorActionManager.getInstance()
        val escapeHandler = HJEscapeHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE))
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, escapeHandler)
    }
}