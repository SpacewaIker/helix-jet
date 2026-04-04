package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange

/**
 * A selection consists of one or more selection ranges.
 * invariant: A selection can never be empty (always contains at least primary range)
 */
class HJSelection {
    val ranges: MutableList<HJRange> = mutableListOf()
    var primaryIndex: Int
    val len get() = ranges.size
    val primary get() = ranges[primaryIndex]

    constructor(ranges: List<HJRange>, primaryIndex: Int) {
        this.ranges.addAll(ranges.map { it.copy() })
        this.primaryIndex = primaryIndex
    }

    constructor(primaryRange: HJRange) {
        ranges.add(primaryRange)
        primaryIndex = 0
    }

    fun clone(): HJSelection {
        return HJSelection(ranges, primaryIndex)
    }

    /**
     * Normalizes an HJSelection
     *
     * Ranges are sorted by HJRange.from, with overlapping ranges merged.
     */
    fun normalize(): HJSelection {
        if (len < 2) return this

        var primary = ranges[primaryIndex]
        ranges.sortBy { it.from }

        // dedup
        var i = 1
        while (i < ranges.size) {
            val currRange = ranges[i]
            val prevRange = ranges[i - 1]

            if (prevRange.overlaps(currRange)) {
                val newRange = currRange.merge(prevRange)
                if (prevRange == primary || currRange == primary) {
                    primary = newRange
                }
                ranges[i - 1] = newRange
                ranges.removeAt(i)
            } else {
                i++
            }
        }

        primaryIndex = ranges.indexOfFirst { it == primary }

        return this
    }

    /**
     * Takes a function and maps each HJRange over the function
     */
    fun transform(f: (HJRange) -> HJRange): HJSelection {
        for (i in ranges.indices) {
            ranges[i] = f(ranges[i])
        }
        return this.normalize()
    }

    /**
     * Ensures the selection adheres to the following invariants:
     * 1. All ranges are grapheme aligned
     * 2. All ranges are at least 1 character wide, unless at the very end of the document
     * 3. Ranges are non-overlapping
     * 4. Ranges are sorted by their position in the text
     */
    fun ensureInvariants(doc: Document): HJSelection {
        return this.transform { r ->
            r.minWidth1(doc)
        }.normalize()
    }

    companion object {
        fun selectOnMatches(doc: Document, selection: HJSelection, pattern: String): HJSelection? {
            val result = mutableListOf<HJRange>()
            val regex = Regex(pattern)

            for (sel in selection.ranges) {
                for (mat in regex.findAll(doc.getText(TextRange(sel.from, sel.to)))) {
                    val start = mat.range.first + sel.from
                    val end = mat.range.last + sel.from + 1

                    val range = HJRange(start, end)
                    // make sure the match is not right outside of the selection.
                    // these invalid matches can come from using regex anchors like ^, $
                    if (range != HJRange.point(sel.to)) {
                        result.add(range)
                    }
                }
            }

            return if (!result.isEmpty()) {
                HJSelection(result, 0)
            } else {
                null
            }
        }

        fun splitOnMatches(doc: Document, selection: HJSelection, pattern: String): HJSelection {
            if (pattern.isEmpty()) return selection.clone()

            val result = mutableListOf<HJRange>()
            val regex = Regex(pattern)

            for (sel in selection.ranges) {
                // special case: zero-width selection
                if (sel.from == sel.to) {
                    result.add(sel)
                    continue
                }

                val selStart = sel.from
                val selEnd = sel.to
                var start = selStart

                for (mat in regex.findAll(doc.getText(TextRange(selStart, selEnd)))) {
                    val end = mat.range.first + selStart
                    result.add(HJRange(start, end))
                    start = mat.range.last + selStart + 1
                }

                if (start < selEnd) {
                    result.add(HJRange(start, selEnd))
                }
            }

            return HJSelection(result, 0)
        }

        fun splitOnNewline(doc: Document, selection: HJSelection): HJSelection {
            val result = mutableListOf<HJRange>()

            for (sel in selection.ranges) {
                // special case: zero-width selection
                if (sel.from == sel.to) {
                    result.add(sel)
                    continue
                }

                val selStart = sel.from
                val selEnd = sel.to
                var start = selStart

                val lines = doc.getText(TextRange(selStart, selEnd)).lines()
                for (line in lines) {
                    val lineEnd = start + line.length
                    result.add(HJRange(start, lineEnd)) // - 1 for \n char
                    start = lineEnd + 1
                }

                if (start < selEnd) {
                    result.add(HJRange(start, selEnd))
                }
            }

            return HJSelection(result, 0)
        }
    }
}