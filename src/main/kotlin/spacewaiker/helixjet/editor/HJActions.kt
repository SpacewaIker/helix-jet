package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.ui.HJStatusBarWidget

typealias HJAction = (Editor, HJEditorState) -> Unit

object HJActions {
    var actions: Map<String, HJAction> = mapOf(
        "insert_mode" to fun(editor: Editor, state: HJEditorState) { switchToMode(editor, state, HJEditorMode.INSERT) },
        "select_mode" to fun(editor: Editor, state: HJEditorState) { switchToMode(editor, state, HJEditorMode.SELECT) },
        "normal_mode" to fun(editor: Editor, state: HJEditorState) { switchToMode(editor, state, HJEditorMode.NORMAL) },

        "move_visual_line_up" to fun(editor: Editor, state: HJEditorState) { moveCaretLogicalPosition(editor, -1, 0) },
        "move_visual_line_down" to fun(editor: Editor, state: HJEditorState) { moveCaretLogicalPosition(editor, 1, 0) },
        "move_char_left" to fun(editor: Editor, state: HJEditorState) { moveCaretOffset(editor, -1) },
        "move_char_right" to fun(editor: Editor, state: HJEditorState) { moveCaretOffset(editor, 1) },

        "collapse_selection" to fun(editor: Editor, state: HJEditorState) { collapseSelection(editor) },
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

fun switchToMode(editor: Editor, state: HJEditorState, newMode: HJEditorMode) {
    state.mode = newMode
    editor.settings.isBlockCursor = newMode != HJEditorMode.INSERT
    editor.project?.let {
        WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarWidget.ID)
    }
}

private fun collapseSelection(editor: Editor) {
    editor.selectionModel.removeSelection()
}