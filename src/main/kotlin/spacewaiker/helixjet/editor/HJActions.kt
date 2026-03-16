package spacewaiker.helixjet.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.ui.HJStatusBarWidget

typealias HJAction = (Editor) -> Unit

object HJActions {
    var actions: Map<String, HJAction> = mapOf(
        "insert_mode" to fun(editor: Editor) { switchToMode(editor, HJEditorMode.INSERT) },
        "select_mode" to fun(editor: Editor) { switchToMode(editor, HJEditorMode.SELECT) },
        "normal_mode" to fun(editor: Editor) { switchToMode(editor, HJEditorMode.NORMAL) },

        "move_visual_line_up" to fun(editor: Editor) { moveCaretLogicalPosition(editor, -1, 0) },
        "move_visual_line_down" to fun(editor: Editor) { moveCaretLogicalPosition(editor, 1, 0) },
        "move_char_left" to fun(editor: Editor) { moveCaretOffset(editor, -1) },
        "move_char_right" to fun(editor: Editor) { moveCaretOffset(editor, 1) },

        "delete_selection" to fun(editor: Editor) {
            editor.selectionModel.copySelectionToClipboard()
            deleteSelection(editor)
            collapseSelection(editor)
            switchToMode(editor, HJEditorMode.NORMAL)
        },
        "collapse_selection" to fun(editor: Editor) { collapseSelection(editor) },
    )
}

private fun moveCaretLogicalPosition(editor: Editor, deltaLine: Int, deltaColumn: Int) {
    val position = editor.caretModel.primaryCaret.logicalPosition
    editor.caretModel.primaryCaret.moveToLogicalPosition(
        LogicalPosition(
            position.line + deltaLine, position.column + deltaColumn
        )
    )
}

private fun moveCaretOffset(editor: Editor, deltaOffset: Int) {
    val offset = editor.caretModel.primaryCaret.offset
    editor.caretModel.primaryCaret.moveToOffset(offset + deltaOffset)
}

fun switchToMode(editor: Editor, newMode: HJEditorMode) {
    if (HJEditorState.mode == newMode) return
    HJEditorState.mode = newMode
    EditorFactory.getInstance().allEditors.forEach { it.settings.isBlockCursor = newMode != HJEditorMode.INSERT }
    editor.project?.let {
        WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarWidget.ID)
    }
}

private fun collapseSelection(editor: Editor) {
    editor.selectionModel.removeSelection()
}

private fun deleteSelection(editor: Editor) {
    val caret = editor.caretModel.primaryCaret
    val start = caret.selectionStart
    var end = caret.selectionEnd
    if (caret.offset == end) end += 1

    WriteCommandAction.runWriteCommandAction(editor.project) {
        editor.document.deleteString(start, end)
    }
}