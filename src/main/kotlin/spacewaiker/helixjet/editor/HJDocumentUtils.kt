package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * Get line content at line `line`. Checks that the line number is valid
 */
fun getLine(doc: Document, line: Int): String? {
    return if (line > doc.lineCount - 1) null else {
        doc.getText(TextRange(doc.getLineStartOffset(line), doc.getLineEndOffset(line)))
    }
}

/**
 * Return index of first non whitespace character in string, if any.
 * Optionally offset by a provided value
 */
fun firstNonWhitespaceChar(line: String, startOffset: Int = 0): Int? {
    val idx = line.asSequence().indexOfFirst { !it.isWhitespace() }
    return if (idx == -1) null else startOffset + idx
}