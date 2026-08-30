package spacewaiker.helixjet.input

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx
import spacewaiker.helixjet.actions.focusedEditor
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJSelection
import spacewaiker.helixjet.editor.HJState

/**
 * Handle typed keys. This is used only for user input in Insert mode.
 */
class HJTypedHandler(private val originalHandler: TypedActionHandler) : TypedActionHandlerEx {
    override fun beforeExecute(
        editor: Editor, c: Char,
        context: DataContext, plan: ActionPlan,
    ) {
        // nothing needed
    }

    override fun execute(editor: Editor, c: Char, context: DataContext) {
        val hjState = editor.project?.let { HJState.getInstance(it) } ?: return

        // focusedEditor is null if we're typing e.g. in the debugger drawer
        if (editor.project?.focusedEditor == null && !hjState.typedHandlerBlock) {
            originalHandler.execute(editor, c, context)
        }

        if ((hjState.mode == HJEditorMode.Insert && !hjState.typedHandlerBlock)
            || !hjState.keymappingEnabled
        ) {
            originalHandler.execute(editor, c, context)
            hjState.getHJEditor(editor)?.setNewSelection(HJSelection(editor.caretModel))
        }
    }
}