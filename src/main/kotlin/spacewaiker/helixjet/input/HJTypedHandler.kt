package spacewaiker.helixjet.input

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx

class HJTypedHandler : TypedActionHandlerEx {
    private var originalHandler: TypedActionHandler? = null

    override fun beforeExecute(
        editor: Editor, c: Char,
        context: DataContext, plan: ActionPlan
    ) {
        // nothing needed
    }

    override fun execute(editor: Editor, c: Char, context: DataContext) {
        if (!HJInputHandler.handle(editor, HJInputKey.Character(c))) {
            originalHandler?.execute(editor, c, context)
        }
    }

    fun setOriginalHandler(handler: TypedActionHandler?) {
        originalHandler = handler
    }
}