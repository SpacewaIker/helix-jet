package spacewaiker.helixjet.actions

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition
import fleet.fastutil.skip
import fleet.util.backwards
import spacewaiker.helixjet.editor.HJRange
import kotlin.math.max

object HJMotionHandling {
    val logger = Logger.getInstance(this::class.java)
}

enum class HJDirection {
    Forward, Backward;

    val value
        get() = when (this) {
            Forward -> 1
            Backward -> -1
        }
}

enum class HJNavDirection {
    Up, Down, Left, Right,
}

enum class HJAlignment {
    Top, Center, Bottom,
}

enum class HJGotoTarget {
    First, Last, Prev, Next,
}

enum class HJCharMovementKind {
    Horizontal, Vertical, VerticalVisual
}

fun moveHorizontally(
    doc: Document,
    range: HJRange,
    direction: HJDirection,
    count: Int,
    extend: Boolean,
): HJRange {
    val pos = range.cursor

    // compute the new position
    val newPos = when (direction) {
        HJDirection.Forward -> nthNextGraphemeBoundary(doc, pos, count)
        HJDirection.Backward -> nthPrevGraphemeBoundary(pos, count)
    }

    // compute the final new range
    return range.putCursor(doc, newPos, extend)
}

fun moveVerticallyVisual(
    editor: Editor,
    range: HJRange,
    direction: HJDirection,
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
    direction: HJDirection,
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
    NextWordStart, NextWordEnd, PrevWordStart, PrevWordEnd, NextLongWordStart, NextLongWordEnd, PrevLongWordStart, PrevLongWordEnd, NextSubWordStart, NextSubWordEnd, PrevSubWordStart, PrevSubWordEnd;

    val isPrev
        get() = when (this) {
            PrevWordStart, PrevWordEnd, PrevLongWordStart, PrevLongWordEnd, PrevSubWordStart, PrevSubWordEnd -> true
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
        HJWordMotionTarget.NextWordStart, HJWordMotionTarget.PrevWordEnd -> {
            isWordBoundary(prevChar, nextChar) && (charIsLineEnding(nextChar) || !nextChar.isWhitespace())
        }
        HJWordMotionTarget.NextWordEnd, HJWordMotionTarget.PrevWordStart -> {
            isWordBoundary(prevChar, nextChar) && (!prevChar.isWhitespace() || charIsLineEnding(nextChar))
        }
        HJWordMotionTarget.NextLongWordStart, HJWordMotionTarget.PrevLongWordEnd -> {
            isLongWordBoundary(prevChar, nextChar) && (charIsLineEnding(nextChar) || !nextChar.isWhitespace())
        }
        HJWordMotionTarget.NextLongWordEnd, HJWordMotionTarget.PrevLongWordStart -> {
            isLongWordBoundary(prevChar, nextChar) && (!prevChar.isWhitespace() || charIsLineEnding(nextChar))
        }
        HJWordMotionTarget.NextSubWordStart -> {
            isSubWordBoundary(prevChar, nextChar, HJDirection.Forward)
                    && (charIsLineEnding(nextChar) || !(nextChar.isWhitespace() || nextChar == '_'))
        }
        HJWordMotionTarget.PrevSubWordEnd -> {
            isSubWordBoundary(prevChar, nextChar, HJDirection.Backward)
                    && (charIsLineEnding(nextChar) || !(nextChar.isWhitespace() || nextChar == '_'))
        }
        HJWordMotionTarget.NextSubWordEnd -> {
            isSubWordBoundary(prevChar, nextChar, HJDirection.Forward)
                    && (!(prevChar.isWhitespace() || prevChar == '_') || charIsLineEnding(nextChar))
        }
        HJWordMotionTarget.PrevSubWordStart -> {
            isSubWordBoundary(prevChar, nextChar, HJDirection.Backward)
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
        Pair(HJCharCategory.Word, HJCharCategory.Punctuation),
        Pair(HJCharCategory.Punctuation, HJCharCategory.Word),
            -> false

        else if aCat != bCat -> true
        else -> false
    }
}

private fun isSubWordBoundary(a: Char, b: Char, direction: HJDirection): Boolean {
    val aCat = HJCharCategory.categorize(a)
    val bCat = HJCharCategory.categorize(b)
    return when (Pair(aCat, bCat)) {
        Pair(HJCharCategory.Word, HJCharCategory.Word) -> {
            if ((a == '_') != (b == '_')) {
                true
            } else {
                // subword boundaries are directional: in 'fooBar' there is a boundary between 'o' and 'B', but not between 'B' and 'a'.
                when (direction) {
                    HJDirection.Forward -> a.isLowerCase() && b.isUpperCase()
                    HJDirection.Backward -> a.isUpperCase() && b.isLowerCase()
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

fun findNthChar(doc: Document, charMatcher: HJCharMatcher, pos: Int, count: Int, direction: HJDirection): Int? {
    if (count == 0) return null

    val chars = doc.charsSequence.toList().listIterator()
    chars.skip(pos)

    var n = count
    var pos = pos

    when (direction) {
        HJDirection.Forward -> while (true) {
            if (!chars.hasNext()) return null
            val c = chars.next()
            if (charMatcher.matches(c)) {
                n--
                if (n == 0) return pos
            }
            pos++
        }

        HJDirection.Backward -> while (true) {
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

fun movePrevParagraph(doc: Document, range: HJRange, count: Int, extend: Boolean): HJRange {
    var line = range.cursorLine(doc)
    val firstChar = doc.getLineStartOffset(line) == range.cursor
    val prevLineEmpty = doc.getLine((line - 1).coerceAtLeast(0))!!.isEmpty()
    val currLineEmpty = doc.getLine(line)!!.isEmpty()
    val prevEmptyToLine = prevLineEmpty == !currLineEmpty

    // skip character before paragraph boundary
    if (prevEmptyToLine && !firstChar) {
        line++
    }
    var lines = doc.text.lines().reversed()
    lines = lines.drop(lines.count() - line)
    val linesBool = lines.map(String::isEmpty)
    var lastLine = line
    var i = 0
    repeat(count) {
        while (i < lines.size && linesBool[i]) {
            i++
            line--
        }
        while (i < lines.size && !linesBool[i]) {
            i++
            line--
        }
        if (line == lastLine) return@repeat
        lastLine = line
    }

    val head = doc.getLineStartOffset(line)
    val anchor = if (extend) {
        range.putCursor(doc, head, true).anchor
    } else {
        // exclude first character after paragraph boundary
        if (prevEmptyToLine && firstChar) range.cursor else range.head
    }
    return HJRange(anchor, head)
}

fun moveNextParagraph(doc: Document, range: HJRange, count: Int, extend: Boolean): HJRange {
    var line = range.cursorLine(doc)
    val lastChar = doc.getLineEndOffset(line) == range.cursor
    val currLineEmpty = doc.getLine(line)!!.isEmpty()
    val nextLineEmpty = doc.getLine((line + 1).coerceAtMost(doc.lineCount - 1))!!.isEmpty()
    val currEmptyToLine = currLineEmpty && !nextLineEmpty

    // skip character after paragraph boundary
    if (currEmptyToLine && lastChar) {
        line++
    }
    val lines = doc.text.lines().drop(line)
    val linesBool = lines.map(String::isEmpty)
    var lastLine = line
    var i = 0
    repeat(count) {
        while (i < lines.size && !linesBool[i]) {
            i++
            line++
        }
        while (i < lines.size && linesBool[i]) {
            i++
            line++
        }
        if (line == lastLine) return@repeat
        lastLine = line
    }

    val head = if (line == doc.lineCount) doc.textLength else doc.getLineStartOffset(line)
    val anchor = if (extend) {
        range.putCursor(doc, head, true).anchor
    } else {
        if (currEmptyToLine && lastChar) range.head else range.cursor
    }
    return HJRange(anchor, head)
}

/**
 * Returns the first index that doesn't satisfy the given predicate when advancing the character index.
 *
 * Returns null if all characters satisfy the predicate.
 */
fun skipWhile(string: String, pos: Int, predicate: (Char) -> Boolean): Int? {
    return string.drop(pos).withIndex().find { !predicate(it.value) }?.let { it.index + pos }
}

/**
 * Returns the first index that doesn't satisfy the given predicate when retreating the character index.
 *
 * Returns null if all characters satisfy the predicate.
 */
fun backwardsSkipWhile(string: String, pos: Int, predicate: (Char) -> Boolean): Int? {
    val pos = pos - 1 // adjust for caret/pos being after character when going backward
    return string.reversed().drop(string.length - pos - 1).withIndex().find { !predicate(it.value) }
        ?.let { pos - it.index + 1 } // adjust again for caret positioning
}

/**
 * Returns the element in the provided list for "goto" first/last/next/prev commands.
 */
fun <T> gotoElementInList(
    cx: HJActionContext,
    list: List<T>,
    gotoTarget: HJGotoTarget,
    startOffsetFn: (T) -> Int,
    endOffsetFn: (T) -> Int,
): T? {
    if (list.isEmpty()) return null

    return when (gotoTarget) {
        HJGotoTarget.First -> list.first()
        HJGotoTarget.Last -> list.last()
        HJGotoTarget.Prev -> {
            val pos = cx.hjEditor.selection.primary.cursor
            val reversed = list.asReversed()
            // coerce at least 0 since indexOfFirst returns -1 if nothing is found
            val firstPrevIdx = reversed.indexOfFirst { endOffsetFn(it) < pos }.coerceAtLeast(0)
            val targetIdx = (firstPrevIdx + cx.count - 1) % reversed.size
            reversed[targetIdx]
        }

        HJGotoTarget.Next -> {
            val pos = cx.hjEditor.selection.primary.cursor
            // coerce at least 0 since indexOfFirst returns -1 if nothing is found
            val firstNextIdx = list.indexOfFirst { startOffsetFn(it) > pos }.coerceAtLeast(0)
            val targetIdx = (firstNextIdx + cx.count - 1) % list.size
            list[targetIdx]
        }
    }
}
