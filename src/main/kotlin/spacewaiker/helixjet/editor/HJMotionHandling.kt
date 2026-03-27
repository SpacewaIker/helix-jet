package spacewaiker.helixjet.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import fleet.fastutil.skip
import fleet.util.backwards
import kotlin.math.max

object HJMotionHandling {
    val logger = Logger.getInstance(this::class.java)
}

enum class HJMovementDirection {
    FORWARD, BACKWARD;

    val value
        get() = when (this) {
            FORWARD -> 1
            BACKWARD -> -1
        }
}

enum class HJCharMovementKind {
    HORIZONTAL, VERTICAL, VERTICAL_VISUAL
}

fun moveHorizontally(
    doc: Document,
    range: HJRange,
    direction: HJMovementDirection,
    count: Int,
    extend: Boolean,
): HJRange {
    val pos = range.cursor

    // compute the new position
    val newPos = when (direction) {
        HJMovementDirection.FORWARD -> nthNextGraphemeBoundary(doc, pos, count)
        HJMovementDirection.BACKWARD -> nthPrevGraphemeBoundary(pos, count)
    }

    // compute the final new range
    return range.putCursor(doc, newPos, extend)
}

fun moveVerticallyVisual(
    editor: Editor,
    range: HJRange,
    direction: HJMovementDirection,
    count: Int,
    extend: Boolean,
): HJRange {
    if (editor.softWrapModel.isSoftWrappingEnabled) {
        return moveVertically(editor, range, direction, count, extend)
    }

    val pos = range.cursor

    // compute the current position's 2d coordinates
    val visualPos = editor.offsetToVisualPosition(pos)
    val newCol = range.oldVisualPosition?.second ?: visualPos.column

    // compute the new position
    var rowOffset = count * direction.value

    // compute visual offset relative to block start to avoid traversing the block twice
    rowOffset += visualPos.line
    rowOffset = rowOffset.coerceIn(0, editor.document.lineCount - 1)
    val newPos = editor.visualPositionToOffset(VisualPosition(rowOffset, newCol))

    val newRange = range.putCursor(editor.document, newPos, extend)
    newRange.oldVisualPosition = Pair(0, newCol)
    return newRange
}

fun moveVertically(
    editor: Editor,
    range: HJRange,
    direction: HJMovementDirection,
    count: Int,
    extend: Boolean,
): HJRange {
    val pos = range.cursor

    // compute the current position's 2d coordinates
    val logicalPos = editor.offsetToLogicalPosition(pos)
    val newCol = range.oldVisualPosition?.second ?: logicalPos.column

    // compute the new position
    var rowOffset = count * direction.value

    // compute visual offset relative to block start to avoid traversing the block twice
    rowOffset += logicalPos.line
    rowOffset = rowOffset.coerceIn(0, editor.document.lineCount - 1)
    val newPos = editor.logicalPositionToOffset(LogicalPosition(rowOffset, newCol))

    val newRange = range.putCursor(editor.document, newPos, extend)
    newRange.oldVisualPosition = editor.offsetToVisualPosition(newPos).let { Pair(it.line, it.column) }
    return newRange
}

enum class HJWordMotionTarget {
    NEXT_WORD_START, NEXT_WORD_END, PREV_WORD_START, PREV_WORD_END, NEXT_LONG_WORD_START, NEXT_LONG_WORD_END, PREV_LONG_WORD_START, PREV_LONG_WORD_END, NEXT_SUB_WORD_START, NEXT_SUB_WORD_END, PREV_SUB_WORD_START, PREV_SUB_WORD_END;

    val isPrev
        get() = when (this) {
            PREV_WORD_START, PREV_WORD_END, PREV_LONG_WORD_START, PREV_LONG_WORD_END, PREV_SUB_WORD_START, PREV_SUB_WORD_END -> true
            else -> false
        }
}

fun wordMove(doc: Document, range: HJRange, target: HJWordMotionTarget, count: Int): HJRange {
    val isPrev = target.isPrev

    // special-case early out
    if ((isPrev && range.head == 0) || (!isPrev && range.head == doc.textLength)) {
        return range
    }

    // prepare the range appropriately based on the target movement direction. This is addressing two things at once:
    //      1. Block-cursor semantics
    //      2. The anchor position being irrelevant to the output result
    val startRange = if (isPrev) {
        if (range.anchor < range.head) {
            HJRange(range.head, prevGraphemeBoundary(range.head))
        } else {
            HJRange(nextGraphemeBoundary(doc, range.head), range.head)
        }
    } else {
        if (range.anchor < range.head) {
            HJRange(prevGraphemeBoundary(range.head), range.head)
        } else {
            HJRange(range.head, nextGraphemeBoundary(doc, range.head))
        }
    }

    // do the main work
    var range = startRange
    for (_ in 0 until count) {
        val chars = doc.charsSequence.toList().listIterator()
        chars.skip(range.head)
        val nextRange = rangeToTarget(chars, target, range)
        if (range == nextRange) break
        range = nextRange
    }

    return range
}

// note: this only changes the anchor of the range if the head is effectively starting on a boundary (either directly or
// after skipping newline characters). Any other changes to the anchor should be handled by the calling code.
private fun rangeToTarget(chars: ListIterator<Char>, target: HJWordMotionTarget, origin: HJRange): HJRange {
    val isPrev = target.isPrev
    var chars = chars

    // reverse the iterator if needed for the motion direction
    if (isPrev) {
        chars = chars.backwards()
    }

    // function to advance index in the appropriate motion direction
    val advance = if (isPrev) {
        { idx: Int -> max(0, idx - 1) }
    } else {
        { idx: Int -> idx + 1 }
    }

    // initialize state variables
    var anchor = origin.anchor
    var head = origin.head
    var prevChar = if (chars.hasPrevious()) {
        val prev = chars.previous()
        chars.next()
        prev
    } else {
        null
    }

    // skip any initial newline characters
    while (chars.hasNext()) {
        val ch = chars.next()
        if (charIsLineEnding(ch)) {
            prevChar = ch
            head = advance(head)
        } else {
            chars.previous()
            break
        }
    }
    if (prevChar != null && charIsLineEnding(prevChar)) {
        anchor = head
    }

    // find our target position(s)
    val headStart = head
    while (chars.hasNext()) {
        val nextChar = chars.next()
        if (prevChar == null || reachedTarget(target, prevChar, nextChar)) {
            if (head == headStart) {
                anchor = head
            } else {
                break
            }
        }
        prevChar = nextChar
        head = advance(head)
    }

    return HJRange(anchor, head)
}

private fun reachedTarget(target: HJWordMotionTarget, prevChar: Char, nextChar: Char): Boolean {
    // @formatter:off
    return when (target) {
        HJWordMotionTarget.NEXT_WORD_START, HJWordMotionTarget.PREV_WORD_END -> {
            isWordBoundary(prevChar, nextChar) && (charIsLineEnding(nextChar) || !nextChar.isWhitespace())
        }
        HJWordMotionTarget.NEXT_WORD_END, HJWordMotionTarget.PREV_WORD_START -> {
            isWordBoundary(prevChar, nextChar) && (!prevChar.isWhitespace() || charIsLineEnding(nextChar))
        }
        HJWordMotionTarget.NEXT_LONG_WORD_START, HJWordMotionTarget.PREV_LONG_WORD_END -> {
            isLongWordBoundary(prevChar, nextChar) && (charIsLineEnding(nextChar) || !nextChar.isWhitespace())
        }
        HJWordMotionTarget.NEXT_LONG_WORD_END, HJWordMotionTarget.PREV_LONG_WORD_START -> {
            isLongWordBoundary(prevChar, nextChar) && (!prevChar.isWhitespace() || charIsLineEnding(nextChar))
        }
        HJWordMotionTarget.NEXT_SUB_WORD_START -> {
            isSubWordBoundary(prevChar, nextChar, HJMovementDirection.FORWARD)
                    && (charIsLineEnding(nextChar) || !(nextChar.isWhitespace() || nextChar == '_'))
        }
        HJWordMotionTarget.PREV_SUB_WORD_END -> {
            isSubWordBoundary(prevChar, nextChar, HJMovementDirection.BACKWARD)
                    && (charIsLineEnding(nextChar) || !(nextChar.isWhitespace() || nextChar == '_'))
        }
        HJWordMotionTarget.NEXT_SUB_WORD_END -> {
            isSubWordBoundary(prevChar, nextChar, HJMovementDirection.FORWARD)
                    && (!(prevChar.isWhitespace() || prevChar == '_') || charIsLineEnding(nextChar))
        }
        HJWordMotionTarget.PREV_SUB_WORD_START -> {
            isSubWordBoundary(prevChar, nextChar, HJMovementDirection.BACKWARD)
                    && (!(prevChar.isWhitespace() || prevChar == '_') || charIsLineEnding(nextChar))
        }
    }
    // @formatter:on
}

private fun isWordBoundary(a: Char, b: Char): Boolean {
    return HJCharCategory.categorize(a) != HJCharCategory.categorize(b)
}

private fun isLongWordBoundary(a: Char, b: Char): Boolean {
    val aCat = HJCharCategory.categorize(a)
    val bCat = HJCharCategory.categorize(b)
    return when (Pair(aCat, bCat)) {
        Pair(HJCharCategory.WORD, HJCharCategory.PUNCTUATION),
        Pair(HJCharCategory.PUNCTUATION, HJCharCategory.WORD),
            -> false

        else if aCat != bCat -> true
        else -> false
    }
}

private fun isSubWordBoundary(a: Char, b: Char, direction: HJMovementDirection): Boolean {
    val aCat = HJCharCategory.categorize(a)
    val bCat = HJCharCategory.categorize(b)
    return when (Pair(aCat, bCat)) {
        Pair(HJCharCategory.WORD, HJCharCategory.WORD) -> {
            if ((a == '_') != (b == '_')) {
                true
            } else {
                // subword boundaries are directional: in 'fooBar' there is a boundary between 'o' and 'B', but not between 'B' and 'a'.
                when (direction) {
                    HJMovementDirection.FORWARD -> a.isLowerCase() && b.isUpperCase()
                    HJMovementDirection.BACKWARD -> a.isUpperCase() && b.isLowerCase()
                }
            }
        }

        else if a != b -> true
        else -> false
    }
}

sealed class HJCharMatcher {
    data class Character(val char: Char) : HJCharMatcher()
    data class Function(val fn: (Char) -> Boolean) : HJCharMatcher()

    fun matches(ch: Char): Boolean {
        return when (this) {
            is Character -> ch == this.char
            is Function -> this.fn(ch)
        }
    }
}

fun findNthChar(doc: Document, charMatcher: HJCharMatcher, pos: Int, count: Int, direction: HJMovementDirection): Int? {
    if (count == 0) return null

    val chars = doc.charsSequence.toList().listIterator()
    chars.skip(pos)

    var n = count
    var pos = pos

    when (direction) {
        HJMovementDirection.FORWARD -> while (true) {
            if (!chars.hasNext()) return null
            val c = chars.next()
            if (charMatcher.matches(c)) {
                n--
                if (n == 0) return pos
            }
            pos++
        }

        HJMovementDirection.BACKWARD -> while (true) {
            if (!chars.hasPrevious()) return null
            val c = chars.previous()
            pos--
            if (charMatcher.matches(c)) {
                n--
                if (n == 0) return pos
            }
        }
    }
}
