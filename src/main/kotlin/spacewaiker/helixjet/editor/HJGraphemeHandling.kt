package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Document
import kotlin.math.max
import kotlin.math.min

fun nthPrevGraphemeBoundary(charIdx: Int, n: Int): Int {
    return max(charIdx - n, 0)
}

fun prevGraphemeBoundary(charIdx: Int): Int {
    return nthPrevGraphemeBoundary(charIdx, 1)
}

fun nthNextGraphemeBoundary(doc: Document, charIdx: Int, n: Int): Int {
    return min(charIdx + n, doc.textLength)
}

fun nextGraphemeBoundary(doc: Document, charIdx: Int): Int {
    return nthNextGraphemeBoundary(doc, charIdx, 1)
}