package io.github.spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import io.github.treesitter.ktreesitter.Node
import io.github.spacewaiker.helixjet.actions.HJDirection
import io.github.spacewaiker.helixjet.actions.nextGraphemeBoundary
import io.github.spacewaiker.helixjet.actions.prevGraphemeBoundary
import io.github.spacewaiker.helixjet.treesitter.getEndOffset
import io.github.spacewaiker.helixjet.treesitter.getStartOffset
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

    val isSingleGrapheme
        get() = (to - from) == 1

    val direction
        get() = if (head < anchor) {
            HJDirection.Backward
        } else {
            HJDirection.Forward
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

    constructor(caret: Caret) {
        val selectionStart = caret.selectionStart
        val selectionEnd = caret.selectionEnd + 1 // include last selected character
        if (caret.offset == selectionStart) {
            this.head = selectionStart
            this.anchor = selectionEnd
        } else {
            this.head = selectionEnd
            this.anchor = selectionStart
        }
    }

    fun flip() = HJRange(head, anchor, oldVisualPosition)

    /**
     * Returns the selection if it goes in the direction provided, flipping the selection otherwise
     */
    fun withDirection(direction: HJDirection) = if (this.direction == direction) this else this.flip()

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

            if (anchor <= charIdx) {
                HJRange(anchor, nextGraphemeBoundary(doc, charIdx))
            } else {
                HJRange(anchor, charIdx)
            }
        } else {
            point(charIdx)
        }
    }

    /**
     * The line number that the cursor is on
     */
    fun cursorLine(doc: Document) = doc.getLineNumber(this.cursor)

    /**
     * Compute a possibly new range from this range, attempting to ensure a minimum range width of 1 char by shifting
     * the head in the forward direction as needed.
     *
     * This method will never shift the anchor, and will only shift the head in the forward direction. Therefore, this
     * method can fail at ensuring the minimum width if and only if the passed range is both zero-width and at the end of the Document.
     */
    fun minWidth1(doc: Document): HJRange {
        return if (anchor == head) {
            HJRange(anchor, nextGraphemeBoundary(doc, head), oldVisualPosition)
        } else {
            this
        }
    }

    /**
     * Check two ranges for overlap
     */
    fun overlaps(other: HJRange): Boolean {
        return this.from == other.from || (this.to > other.from && other.to > this.from)
    }

    /**
     * Returns a range that encompasses both input ranges
     *
     * This is like extend(), but tries to negotiate the anchor/head ordering between the two input ranges
     */
    fun merge(other: HJRange): HJRange {
        return if (this.anchor > other.head && other.anchor > other.head) {
            HJRange(max(this.anchor, other.anchor), min(this.head, other.head))
        } else {
            HJRange(min(this.from, other.from), max(this.to, other.to))
        }
    }

    fun copy() = HJRange(anchor, head, oldVisualPosition)

    /**
     * The (inclusive) range of lines that the range overlaps
     */
    fun lineRange(doc: Document): Pair<Int, Int> {
        val from = this.from
        val to = if (this.isEmpty) this.to else {
            max(prevGraphemeBoundary(this.to), from)
        }
        return Pair(doc.getLineNumber(from), doc.getLineNumber(to))
    }

    fun slice(doc: Document): String? {
        return if (to <= doc.textLength) {
            doc.getText(TextRange(from, to))
        } else null
    }

    override fun toString(): String {
        return "HJRange($from, $to)"
    }

    companion object {
        fun point(head: Int) = HJRange(head, head)

        fun fromNode(node: Node, document: Document, direction: HJDirection): HJRange {
            val from = node.getStartOffset(document)
            val to = node.getEndOffset(document)
            return HJRange(from, to).withDirection(direction)
        }
    }
}