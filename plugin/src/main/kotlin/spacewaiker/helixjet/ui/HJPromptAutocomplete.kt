package spacewaiker.helixjet.ui

import com.intellij.openapi.editor.Editor
import javax.swing.text.DefaultHighlighter

private const val PROMPT_HEIGHT = 38
private const val COL_CHARS = 24
private const val COL_PADDING = 2

class HJPromptAutocomplete(editor: Editor) : HJFloatingTextarea(editor) {
    init {
        width = null
        bottomSpacing = PROMPT_HEIGHT
        margin = 0
    }

    var entries = emptyList<String>()
    var offsets = intArrayOf()

    /**
     * Set the new available entries in the autocomplete list.
     *
     * @return The height of the element after resizing.
     */
    fun setNewEntries(entries: List<String>): Int? {
        this.entries = entries
        val (_, _, _, h) = recomputeElement() ?: return null
        return h
    }

    override fun formatText(maxChars: Int): String {
        val numCols = maxChars / COL_CHARS
        // -(-a).floorDiv(b) == a.ceilDiv(b)
        val numRows = -(-entries.size).floorDiv(numCols)

        val sb = StringBuilder()

        val offsets = IntArray(entries.size) { 0 }
        for (row in 0 until numRows) {
            for (col in 0 until numCols) {
                val idx = row + col * numRows
                if (idx > entries.size - 1) continue
                val entry = entries[idx]

                // add start offset of this entry
                offsets[idx] = sb.length

                sb.append(entry.take(COL_CHARS))
                sb.repeat(" ", COL_CHARS - entry.length + COL_PADDING)
            }
            sb.append('\n')
        }

        this.offsets = offsets

        return sb.toString().removeSuffix("\n")
    }

    fun highlightEntry(idx: Int) {
        val entry = entries[idx]
        val start = offsets[idx]

        val highlighter = textArea.highlighter
        highlighter.removeAllHighlights()
        highlighter.addHighlight(start, start + entry.length, DefaultHighlighter.DefaultPainter)
    }
}