package spacewaiker.helixjet.core

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.input.HJMouseListener

class HJEditorListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val editor = source.getSelectedEditor(file)
            ?.let { it as? TextEditor }
            ?.editor ?: return

        handleNewEditor(editor)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val editor = source.getSelectedEditor(file)
            ?.let { it as? TextEditor }
            ?.editor ?: return
        HJState.releaseEditor(editor)
    }

    companion object {
        fun handleNewEditor(editor: Editor) {
            HJState.getHJEditor(editor)
            editor.addEditorMouseListener(HJMouseListener())
        }
    }
}