package spacewaiker.helixjet.editor

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.CaretVisualAttributes
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.util.Key
import com.intellij.psi.PlainTextTokenTypes
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.elementType
import spacewaiker.helixjet.config.HJFileGlobMatcher
import spacewaiker.helixjet.config.HJLanguage
import spacewaiker.helixjet.treesitter.HJSyntaxLoader
import spacewaiker.helixjet.ui.HJJumpLabelOverlay
import spacewaiker.helixjet.ui.HJStatusBarFileTypeWidget
import java.awt.event.MouseWheelListener

private val JUMP_OVERLAY_KEY = Key.create<HJJumpLabelOverlay>("helixjet.gotoword.inlays")

sealed class HJChange {
    abstract val fromRank: Int
    abstract val toRank: Int

    data class Insert(val at: Int, val str: String) : HJChange() {
        override val fromRank = at
        override val toRank = at
    }

    data class Replace(val from: Int, val to: Int, val replacement: String) : HJChange() {
        override val fromRank = from
        override val toRank = to
    }

    data class Delete(val from: Int, val to: Int) : HJChange() {
        override val fromRank = from
        override val toRank = to
    }
}

data class HJTransaction(
    val changes: List<HJChange>,
    val selection: HJSelection? = null,
)

/**
 * @property objectSelections Used to store previous selections of treesitter objects
 */
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
    var language: HJLanguage? = null
        set(value) {
            field = value
            HJStatusBarFileTypeWidget.updateFileTypeWidget()
        }
    val syntax
        get() = language?.let { HJSyntaxLoader.getLanguageSyntax(it.grammarOrName, editor.document.text) }
    val objectSelections = mutableListOf<HJSelection>()

    val selectionIsLinewise
        get() = selection.ranges.all { range ->
            val (fromLine, toLine) = range.lineRange(editor.document)
            val start = editor.document.getLineStartOffset(fromLine)
            val end = editor.document.getLineEndOffset(toLine) + 1 // to account for line ending
            start == range.from && end == range.to
        }

    constructor(editor: Editor) {
        this.editor = editor
        this.language = HJFileGlobMatcher.match(editor.virtualFile.path)
        this.selection = HJSelection(editor.caretModel)

        logger.debug("Created HJEditor with selection: $selection")

        editor.settings.verticalScrollOffset = HJState.config.scrolloff
        reloadCaretShapes()
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

            val pos = editor.offsetToLogicalPosition(range.cursor.coerceAtMost(editor.document.textLength))
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
                if (range.len <= 1 && HJState.mode != HJEditorMode.Select) {
                    caret.removeSelection()
                } else {
                    val from = range.from.coerceAtMost(editor.document.textLength - 1)
                    val to = range.to.coerceAtMost(editor.document.textLength - 1)
                    caret.setSelection(from, to)
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
        val changes = transaction.changes.sortedByDescending { it.toRank }

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
        editor.settings.isBlockCursor = HJState.mode != HJEditorMode.Insert && HJState.keymappingEnabled
    }

    fun setCorrectScroll() {
        editor.scrollingModel.scrollToCaret(ScrollType.RELATIVE)
    }

    fun notify(message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("HelixJet Notifications")
            .createNotification(message, type)
            .notify(editor.project)
    }

    fun setStatus(message: String) {
        notify(message, NotificationType.INFORMATION)
    }

    fun setWarning(message: String) {
        notify(message, NotificationType.WARNING)
    }

    fun setError(message: String) {
        notify(message, NotificationType.ERROR)
    }

    private lateinit var jumpLabelMouseWheelListener: MouseWheelListener

    fun setJumpLabels(labels: List<Pair<Int, String>>) {
        var overlay = editor.getUserData(JUMP_OVERLAY_KEY)
        if (overlay == null) {
            overlay = HJJumpLabelOverlay(editor)
            editor.contentComponent.add(overlay)
            editor.contentComponent.revalidate()
            editor.putUserData(JUMP_OVERLAY_KEY, overlay)

            // hide labels on mouse scroll
            jumpLabelMouseWheelListener = MouseWheelListener { event ->
                hideJumpLabels()
                editor.contentComponent.parent.dispatchEvent(event)
                editor.contentComponent.removeMouseWheelListener(jumpLabelMouseWheelListener)
            }
            editor.contentComponent.addMouseWheelListener(jumpLabelMouseWheelListener)
        }
        overlay.setLabels(labels)
    }

    fun hideJumpLabels() {
        editor.getUserData(JUMP_OVERLAY_KEY)?.let { overlay ->
            editor.contentComponent.remove(overlay)
            editor.contentComponent.revalidate()
            editor.contentComponent.repaint()
            editor.putUserData(JUMP_OVERLAY_KEY, null)
        }
    }
}