package spacewaiker.helixjet.core

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.actionSystem.EditorActionManager
import com.intellij.openapi.editor.actionSystem.TypedAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import spacewaiker.helixjet.input.HJEditorActionHandler
import spacewaiker.helixjet.input.HJInputKey
import spacewaiker.helixjet.input.HJTypedHandler

class HJStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        // typed handler
        val typedAction = TypedAction.getInstance()
        val handler = HJTypedHandler(typedAction.rawHandler)
        typedAction.setupRawHandler(handler)

        // action handler
        val actionManager = EditorActionManager.getInstance()

        // escape
        val escapeHandler =
            HJEditorActionHandler(actionManager.getActionHandler(IdeActions.ACTION_EDITOR_ESCAPE), HJInputKey.Escape)
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_ESCAPE, escapeHandler)
        
        // backspace
        val backspaceHandler = HJEditorActionHandler(
            actionManager.getActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE),
            HJInputKey.Backspace
        )
        actionManager.setActionHandler(IdeActions.ACTION_EDITOR_BACKSPACE, backspaceHandler)
    }
}