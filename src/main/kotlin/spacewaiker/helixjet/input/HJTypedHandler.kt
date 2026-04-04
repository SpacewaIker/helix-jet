package spacewaiker.helixjet.input

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx

class HJTypedHandler(private val originalHandler: TypedActionHandler?) : TypedActionHandlerEx {
    override fun beforeExecute(
        editor: Editor, c: Char,
        context: DataContext, plan: ActionPlan,
    ) {
        // nothing needed
    }

    override fun execute(editor: Editor, c: Char, context: DataContext) {
        if (!HJInputHandler.handle(editor, HJInputKey.fromCharacter(c))) {
            originalHandler?.execute(editor, c, context)
        }
    }
}