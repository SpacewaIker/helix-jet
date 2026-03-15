package spacewaiker.helixjet.input

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.editor.HelixJetEditorState
import spacewaiker.helixjet.core.HelixJetStateService
import spacewaiker.helixjet.ui.HelixJetStatusBarWidget
import spacewaiker.helixjet.editor.Mode

sealed class HelixJetInputKey {
    object Escape : HelixJetInputKey()
    data class Character(val c: Char) : HelixJetInputKey()
}

object HelixJetInputHandler {
    fun handle(editor: Editor, key: HelixJetInputKey): Boolean {
        val state = HelixJetStateService.get(editor) ?: return false

        return when (state.mode) {
            Mode.NORMAL -> handleNormal(key, editor, state)
            Mode.INSERT -> handleInsert(key, editor, state)
        }
    }

    private fun handleNormal(key: HelixJetInputKey, editor: Editor, state: HelixJetEditorState): Boolean {
        when (key) {
            is HelixJetInputKey.Character -> when(key.c) {
                'i' -> enterInsert(editor, state)
            }
            is HelixJetInputKey.Escape -> enterNormal(editor, state)
        }
        return true
    }

    private fun handleInsert(key: HelixJetInputKey, editor: Editor, state: HelixJetEditorState): Boolean {
        when (key) {
            is HelixJetInputKey.Character -> return false
            is HelixJetInputKey.Escape -> enterNormal(editor, state)
        }
        return true
    }

    private fun enterInsert(editor: Editor, state: HelixJetEditorState) {
        state.mode = Mode.INSERT
        editor.settings.isBlockCursor = false
        updateStatusBar(editor)
    }

    private fun enterNormal(editor: Editor, state: HelixJetEditorState) {
        state.mode = Mode.NORMAL
        editor.settings.isBlockCursor = true
        editor.selectionModel.removeSelection()
        updateStatusBar(editor)
    }

    private fun updateStatusBar(editor: Editor) {
        editor.project?.let {
            WindowManager.getInstance().getStatusBar(it)?.updateWidget(HelixJetStatusBarWidget.Companion.ID)
        }
    }
}