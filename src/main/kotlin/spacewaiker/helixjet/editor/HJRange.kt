package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import kotlin.math.max
import kotlin.math.min

/**
 * A single selection range.
 *
 * A range consists of an "anchor" and "head" position in
 * the text.  The head is the part that the user moves when
 * directly extending a selection.  The head and anchor
 * can be in any order, or even share the same position.
 *
 * @property anchor The anchor of the range: the side that doesn't move when extending.
 * @property head The head of the range, moved when extending.
 * @property oldVisualPosition The previous visual offset from the start of the line
 */
class HJRange {
    var anchor: Int
    var head: Int
    var oldVisualPosition: Pair<Int, Int>? = null

    val from
        get() = min(anchor, head)

    val to
        get() = max(anchor, head)

    val len
        get() = to - from

    val isEmpty
        get() = anchor == head

    val direction
        get() = if (head < anchor) {
            HJMovementDirection.BACKWARD
        } else {
            HJMovementDirection.FORWARD
        }

    /**
     * Gets the left-side position of the block cursor
     */
    val cursor
        get() = if (head > anchor) {
            prevGraphemeBoundary(head)
        } else {
            head
        }

    constructor(anchor: Int, head: Int, oldVisualPosition: Pair<Int, Int>? = null) {
        this.head = head
        this.anchor = anchor
        this.oldVisualPosition = oldVisualPosition
    }

    constructor(editor: Editor) {
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        if (editor.caretModel.offset == selectionStart) {
            this.head = selectionStart
            this.anchor = selectionEnd
        } else {
            this.head = selectionEnd
            this.anchor = selectionStart
        }
    }

    fun flip() = HJRange(head, anchor, oldVisualPosition)

    /**
     * Puts the left side of the block cursor at `char_idx`, optionally extending.
     *
     * This follows "1-width" semantics, and therefore does a combination of anchor and head moves
     * to behave as if both the front and back of the range are 1-width blocks
     */
    @Suppress("ConvertTwoComparisonsToRangeCheck")
    fun putCursor(doc: Document, charIdx: Int, extend: Boolean): HJRange {
        return if (extend) {
            val anchor = if (head >= anchor && charIdx < anchor) {
                nextGraphemeBoundary(doc, this.anchor)
            } else if (head < anchor && charIdx >= anchor) {
                prevGraphemeBoundary(this.anchor)
            } else {
                this.anchor
            }

            if (anchor < charIdx) {
                HJRange(anchor, min(charIdx + 1, doc.textLength))
            } else {
                HJRange(anchor, charIdx)
            }
        } else {
            point(charIdx)
        }
    }

    /**
     * Save this range to the HJEditorState and apply it to provided Editor.
     */
    fun saveAndApply(editor: Editor) {
        HJEditorState.putRange(editor, this)

        editor.caretModel.moveToOffset(cursor)
        if (len <= 1 && HJEditorState.mode == HJEditorMode.NORMAL) {
            editor.selectionModel.removeSelection()
        } else {
            editor.selectionModel.setSelection(from, to)
        }
    }

    companion object {
        fun point(head: Int) = HJRange(head, head)
    }
}