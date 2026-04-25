package spacewaiker.helixjet.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.psi.PlainTextTokenTypes
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.elementType

sealed class HJChange {
    abstract val rank: Int

    data class Insert(val at: Int, val str: String) : HJChange() {
        override val rank = at
    }

    data class Replace(val from: Int, val to: Int, val replacement: String) : HJChange() {
        override val rank = to
    }

    data class Delete(val from: Int, val to: Int) : HJChange() {
        override val rank = to
    }
}

data class HJTransaction(
    val changes: List<HJChange>,
    val selection: HJSelection? = null,
)

class HJEditor {
    val logger = Logger.getInstance(this::class.java)
    val editor: Editor
    var selection: HJSelection
    var lastMotion: ((HJEditor) -> Unit)? = null
    val psiFile
        get() = editor.project?.let { PsiDocumentManager.getInstance(it) }?.getPsiFile(editor.document)?.let {
            if (it.elementType == PlainTextTokenTypes.PLAIN_TEXT_FILE || it.elementType?.toString() == "TextMate") null else it
        }
    val codeStyleManager get() = editor.project?.let { CodeStyleManager.getInstance(it) }
    val ast get() = psiFile?.node?.let { if (it.elementType == PlainTextTokenTypes.PLAIN_TEXT_FILE) null else it }
    val syntax
        get() = psiFile?.language?.id?.lowercase()?.let { HJLanguageSyntaxProvider.syntaxes.getOrDefault(it, null) }

    val selectionIsLinewise
        get() = selection.ranges.all { range ->
            val (fromLine, toLine) = range.lineRange(editor.document)
            val start = editor.document.getLineStartOffset(fromLine)
            val end = editor.document.getLineEndOffset(toLine) + 1 // to account for line ending
            start == range.from && end == range.to
        }

    constructor(editor: Editor) {
        this.editor = editor
        this.selection = HJSelection(editor.caretModel)

        editor.settings.verticalScrollOffset = HJState.config.scrolloff
    }

    /**
     * Save new selection to the HJState and apply it to this HJEditor.
     */
    fun setNewSelection(selection: HJSelection) {
        this.selection = selection.ensureInvariants(editor.document)

        editor.caretModel.removeSecondaryCarets()
        val primaryCaret = editor.caretModel.primaryCaret

        // need to process primary first because we can't delete the primaryCaret in the caretModel. Therefore we must
        // reuse it, and that means processing it first. Otherwise, we might have to create a secondary caret at the position
        // of the primary, which doesn't work.
        for ((idx, range) in this.selection.rangesWithPrimaryFirst.withIndex()) {
            val isPrimary = idx == 0 // rangesWithPrimaryFirst, so first element is primary

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

                // in normal/insert mode, don't show 1-width selections underneath the blinking block cursor
                if (range.len <= 1 && HJState.mode != HJEditorMode.SELECT) {
                    caret.removeSelection()
                } else {
                    caret.setSelection(range.from, range.to)
                }
            }
        }

        reloadCaretShapes()
        setCorrectScroll()
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
        return HJTransaction(changes)
    }

    fun apply(transaction: HJTransaction) {
        // sort changes so that their indices remain accurate in between changes
        val changes = transaction.changes.sortedByDescending { it.rank }

        WriteCommandAction.runWriteCommandAction(editor.project) {
            for (change in changes) {
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
        val selection = transaction.selection ?: HJSelection(editor.caretModel)
        setNewSelection(selection)
    }

    fun reloadCaretShapes() {
        editor.settings.isBlockCursor = HJState.mode != HJEditorMode.INSERT && HJState.keymappingEnabled
    }

    fun setCorrectScroll() {
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }
}