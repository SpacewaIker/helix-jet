package spacewaiker.helixjet.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.CaretVisualAttributes
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
        val primaryCaret = editor.caretModel.primaryCaret

        for ((idx, range) in this.selection.ranges.withIndex()) {
            val isPrimary = idx == this.selection.primaryIndex

            val pos = editor.offsetToLogicalPosition(range.cursor)
            val caret = if (isPrimary) {
                // have to reuse old primary caret
                primaryCaret.moveToLogicalPosition(pos)
                primaryCaret
            } else {
                editor.caretModel.addCaret(pos, false)
            }

            if (caret != null) {
                val color = if (isPrimary) HJState.config.primaryCursorColor else HJState.config.otherCursorsColor
                caret.visualAttributes = CaretVisualAttributes(color, CaretVisualAttributes.Weight.NORMAL)

                // in normal mode, don't show 1-width selections underneath the blinking block cursor
                if (range.len <= 1 && HJState.mode == HJEditorMode.NORMAL) {
                    caret.removeSelection()
                } else {
                    caret.setSelection(range.from, range.to)
                }
            }
        }

        reloadCaretShapes()
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
        return changeBySelectionIndexed { _, range -> f(range) }
    }

    fun changeBySelectionIndexed(f: (Int, HJRange) -> HJChange?): HJTransaction {
        val changes = selection.ranges.mapIndexedNotNull(f)
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

    fun reloadCaretShapes() {
        editor.settings.isBlockCursor = HJState.mode != HJEditorMode.INSERT
    }
}