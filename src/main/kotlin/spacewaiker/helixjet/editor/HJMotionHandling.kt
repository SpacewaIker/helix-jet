package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.VisualPosition

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
    val newPos = editor.logicalPositionToOffset(LogicalPosition(rowOffset, newCol))

    val newRange = range.putCursor(editor.document, newPos, extend)
    newRange.oldVisualPosition = editor.offsetToVisualPosition(newPos).let { Pair(it.line, it.column) }
    return newRange
}

private enum class HJCharCategory {
    WORD, PUNCTUATION, WHITESPACE;

    companion object {
        fun categorize(c: Char): HJCharCategory = when {
            c.isLetterOrDigit() || c == '_' -> WORD
            c.isWhitespace() -> WHITESPACE
            else -> PUNCTUATION
        }
    }
}

//fun getNextWordStart(editor: Editor): Int? {
//    val caret = editor.caretModel.primaryCaret.offset
//    val text = editor.document.getText(TextRange(caret, min(caret + 100, editor.document.textLength)))
//
//    for (i in 1 until text.length - 1) {
//        val current = text[i]
//        val next = text[i + 1]
//        if (!isWordCharacter(current) && isWordCharacter(next)) {
//            return caret + i
//        }
//    }
//
//    return null
//}

fun isAtWordEnd(editor: Editor): Boolean {
    val doc = editor.document.charsSequence
    val offset = editor.caretModel.primaryCaret.offset
    return (offset + 1 < doc.length) && HJCharCategory.categorize(doc[offset]) != HJCharCategory.categorize(doc[offset + 1])
}

fun getNextWordStart(editor: Editor): Int? {
    val doc = editor.document.charsSequence
    var offset = editor.caretModel.primaryCaret.offset
    if (offset >= doc.length) return null

    if (isAtWordEnd(editor)) {
        offset++
        if (HJEditorState.mode == HJEditorMode.NORMAL) editor.selectionModel.removeSelection()
    }

    val initial = HJCharCategory.categorize(doc[offset])

    // advance through current category
    while (offset < doc.length && HJCharCategory.categorize(doc[offset]) == initial) offset++

    // skip whitespace
    while (offset < doc.length && doc[offset].isWhitespace()) offset++

    return offset - 1
}