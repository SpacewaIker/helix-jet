package spacewaiker.helixjet.input

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class HelixJetEscapeHandler(private val originalHandler: EditorActionHandler?)
    : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (!HelixJetInputHandler.handle(editor, HelixJetInputKey.Escape)) {
            originalHandler?.execute(editor, caret, dataContext)
        }
    }
}