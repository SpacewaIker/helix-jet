package spacewaiker.helixjet.input

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class HJEscapeHandler(private val originalHandler: EditorActionHandler?)
    : EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (!HJInputHandler.handle(editor, HJInputKey.Escape)) {
            originalHandler?.execute(editor, caret, dataContext)
        }
    }
}