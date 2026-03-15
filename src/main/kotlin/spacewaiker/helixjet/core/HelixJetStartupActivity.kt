package spacewaiker.helixjet.core

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import spacewaiker.helixjet.input.HelixJetEscapeHandler
import spacewaiker.helixjet.input.HelixJetTypedHandler

class HelixJetStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // typed handler
        val typedAction = TypedAction.getInstance()
        val handler = HelixJetTypedHandler()
        handler.setOriginalHandler(typedAction.rawHandler)
        typedAction.setupRawHandler(handler)

        // action handler
        val actionManager = EditorActionManager.getInstance()
        val escapeHandler = HelixJetEscapeHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE))
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, escapeHandler)
    }
}