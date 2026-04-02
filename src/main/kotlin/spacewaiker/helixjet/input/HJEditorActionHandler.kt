package spacewaiker.helixjet.input

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler

class HJEditorActionHandler(private val originalHandler: EditorActionHandler?, val hjInputKey: HJInputKey) :
    EditorActionHandler() {

    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (!HJInputHandler.handle(editor, hjInputKey)) {
            originalHandler?.execute(editor, caret, dataContext)
        }
    }
}