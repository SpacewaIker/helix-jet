package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Document

/**
 * A selection consists of one or more selection ranges.
 * invariant: A selection can never be empty (always contains at least primary range)
 */
class HJSelection {
    val ranges: MutableList<HJRange> = mutableListOf()
    var primaryIndex: Int
    val len get() = ranges.size

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
}