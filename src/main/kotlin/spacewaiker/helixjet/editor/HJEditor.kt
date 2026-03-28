package spacewaiker.helixjet.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor

sealed class HJChange {
    data class Insert(val at: Int, val str: String) : HJChange()
    data class Replace(val from: Int, val to: Int, val replacement: String) : HJChange()
    data class Delete(val from: Int, val to: Int) : HJChange()
}

data class HJTransaction(
    val changes: List<HJChange>,
    val selection: HJSelection?,
)

class HJEditor {
    val logger = Logger.getInstance(this::class.java)
    val editor: Editor
    var selection: HJSelection
    var lastMotion: ((HJEditor) -> Unit)? = null

    constructor(editor: Editor) {
        this.editor = editor
        this.selection = HJSelection(HJRange(editor))
    }

    /**
     * Save new selection to the HJState and apply it to this HJEditor.
     */
    fun setNewSelection(selection: HJSelection) {
        this.selection = selection.ensureInvariants(editor.document)

        editor.caretModel.removeSecondaryCarets()
        val oldPrimary = editor.caretModel.primaryCaret

        for ((idx, range) in this.selection.ranges.withIndex()) {
            val pos = editor.offsetToLogicalPosition(range.cursor)
            val caret = editor.caretModel.addCaret(pos, idx == this.selection.primaryIndex)

            if (caret != null && (range.len > 1 || HJState.mode != HJEditorMode.NORMAL)) {
                // in normal mode, don't show 1-width selections underneath the blinking block cursor
                caret.setSelection(range.from, range.to)
            }
        }
        editor.caretModel.removeCaret(oldPrimary)
    }

    fun applyMotion(motion: (HJEditor) -> Unit) {
        motion(this)
        lastMotion = motion
    }

    fun repeatLastMotion(count: Int) {
        if (lastMotion != null) {
            repeat(count) {
                lastMotion?.invoke(this)
            }
        }
    }

    /**
     * Generate an HJTransaction with a change per selection range
     */
    fun changeBySelection(f: (HJRange) -> HJChange?): HJTransaction {
        val changes = selection.ranges.mapNotNull { f(it) }
        return HJTransaction(changes, selection)
    }

    fun apply(transaction: HJTransaction) {
        for (change in transaction.changes) {
            WriteCommandAction.runWriteCommandAction(editor.project) {
                when (change) {
                    is HJChange.Insert -> {
                        editor.document.insertString(change.at, change.str)
                    }

                    is HJChange.Replace -> {
                        editor.document.replaceString(change.from, change.to, change.replacement)
                    }

                    is HJChange.Delete -> {
                        editor.document.deleteString(change.from, change.to)
                    }
                }
            }
        }
        selection.ensureInvariants(editor.document)
    }
}