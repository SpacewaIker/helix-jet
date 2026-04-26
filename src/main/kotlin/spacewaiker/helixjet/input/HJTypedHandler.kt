package spacewaiker.helixjet.input

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx
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
        if ((HJState.mode == HJEditorMode.INSERT && !HJState.typedHandlerBlock)
            || !HJState.keymappingEnabled
        ) {
            originalHandler.execute(editor, c, context)
            HJState.getHJEditor(editor).setNewSelection(HJSelection(editor.caretModel))
        }
    }
}