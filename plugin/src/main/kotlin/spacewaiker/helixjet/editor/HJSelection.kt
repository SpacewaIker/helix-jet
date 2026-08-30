package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import spacewaiker.helixjet.actions.regex
import spacewaiker.helixjet.utils.isSubset

/**
 * A selection consists of one or more selection ranges.
 * invariant: A selection can never be empty (always contains at least primary range)
 */
class HJSelection {
    val ranges: MutableList<HJRange> = mutableListOf()
    var primaryIndex: Int
    val len get() = ranges.size
    val primary get() = ranges[primaryIndex]
    val rangesWithPrimaryFirst: List<HJRange>
        get() {
            val ranges = this.ranges.toMutableList()
            val rangeIndex0 = ranges[0]
            ranges[0] = ranges[primaryIndex]
            ranges[primaryIndex] = rangeIndex0
            return ranges
        }
    val rangeBounds
        get() = ranges.map { it.from until it.to }

    constructor(ranges: List<HJRange>, primaryIndex: Int) {
        this.ranges.addAll(ranges.map { it.copy() })
        this.primaryIndex = primaryIndex
        normalize()
    }

    constructor(primaryRange: HJRange) {
        ranges.add(primaryRange)
        primaryIndex = 0
    }

    constructor(caretModel: CaretModel) {
        this.primaryIndex = 0
        this.ranges.addAll(caretModel.allCarets.withIndex().map { (i, caret) ->
            if (caret == caretModel.primaryCaret) this.primaryIndex = i
            HJRange(caret)
        })
        normalize()
    }

    fun clone() = HJSelection(ranges, primaryIndex)

    /**
     * Adds a new range to the selection and makes it the primary range
     */
    fun push(range: HJRange): HJSelection {
        ranges.add(range)
        primaryIndex = ranges.size - 1
        return this
    }

    /**
     * Removes a range from the selection
     */
    fun remove(index: Int): HJSelection {
        if (ranges.size <= 1) return this

        ranges.removeAt(index)
        if (index < primaryIndex || primaryIndex == ranges.size) {
            primaryIndex--
        }

        return this
    }

    /**
     * Replace a range in the selection with a new range
     */
    fun replace(index: Int, range: HJRange): HJSelection {
        ranges[index] = range
        return this.normalize()
    }

    /**
     * Map selections over a set of changes. Useful for adjusting the selection position after applying changes to a document.
     */
    fun map(changes: List<HJChange>): HJSelection {
        return mapNoNormalize(changes).normalize()
    }

    /**
     * Map selections over a set of changes. Useful for adjusting the selection position after applying changes to a document.
     * Doesn't normalize the selection.
     */
    fun mapNoNormalize(changes: List<HJChange>): HJSelection {
        if (changes.isEmpty()) return this

        val positionsToMap = ranges.flatMap { range ->
            range.oldVisualPosition = null
            when (val cmp = range.anchor.compareTo(range.head)) {
                /* equal */ 0 -> listOf(
                range.anchor,
                range.head
            )
                /* less than */ else if cmp < 0 -> listOf(range.anchor, range.head)
                /* greater than */ else -> listOf(range.head, range.anchor)
            }
        }

//        changes.updatePositions(positionsToMap)
        // return this
        TODO() // need to implement the above function O.O'
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

        removeConsecutiveDuplicatesAndReplace(ranges) { currRange, prevRange ->
            if (prevRange.overlaps(currRange)) {
                val newRange = currRange.merge(prevRange)
                if (prevRange == primary || currRange == primary) {
                    primary = newRange
                }
                Pair(true, newRange)
            } else {
                Pair(false, null)
            }
        }

        primaryIndex = ranges.indexOfFirst { it == primary }

        return this
    }

    /**
     * Replaces ranges with one spanning from first to last
     */
    fun mergeRanges(): HJSelection {
        val first = ranges.first()
        val last = ranges.last()
        return HJSelection(first.merge(last))
    }

    /**
     * Merges all ranges that are consecutive
     */
    fun mergeConsecutiveRanges(): HJSelection {
        var primary = this.primary

        removeConsecutiveDuplicatesAndReplace(ranges) { currRange, prevRange ->
            if (prevRange.to == currRange.from) {
                val newRange = currRange.merge(prevRange)
                if (prevRange == primary || currRange == primary) {
                    primary = newRange
                }
                Pair(true, newRange)
            } else {
                Pair(false, null)
            }
        }

        primaryIndex = ranges.indexOfFirst { it == primary }

        return this
    }

    /**
     * Returns true if this is a subset or equal to other
     */
    fun contains(other: HJSelection) = isSubset(this.rangeBounds.iterator(), other.rangeBounds.iterator(), true)

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
     * Takes a function and maps each HJRange over the function to multiple HJRanges
     */
    fun transformIter(f: (HJRange) -> Iterable<HJRange>): HJSelection {
        val newRanges = ranges.flatMap(f)
        ranges.clear()
        ranges.addAll(newRanges)
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

    override fun toString(): String {
        val sb = StringBuilder("HJSelection(")
        sb.append(ranges.joinToString { if (it == primary) "*$it" else "$it" })
        sb.append(')')
        return sb.toString()
    }

    fun slices(doc: Document) = ranges.map { it.slice(doc) }

    companion object {
        fun selectOnMatches(doc: Document, selection: HJSelection, pattern: String): HJSelection? {
            val result = mutableListOf<HJRange>()
            val regex = regex(pattern) ?: return null

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
            val regex = regex(pattern) ?: return selection.clone()

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

/**
 * Removes consecutive duplicate elements using the given entity relation.
 */
fun <E> removeConsecutiveDuplicates(list: MutableList<E>, sameBucket: (E, E) -> Boolean) {
    removeConsecutiveDuplicatesAndReplace(list) { first, second -> Pair(sameBucket(first, second), null) }
}

/**
 * Removes consecutive duplicate elements using the given entity relation and replaces the kept element with the given value.
 */
fun <E> removeConsecutiveDuplicatesAndReplace(list: MutableList<E>, sameBucket: (E, E) -> Pair<Boolean, E?>) {
    if (list.size <= 1) return

    var write = 1
    for (read in 1 until list.size) {
        val elRead = list[read]
        val elWriteMinus1 = list[write - 1]

        val (isSameBucket, newWriteMinus1) = sameBucket(elRead, elWriteMinus1)
        newWriteMinus1?.let { list[write - 1] = it }
        if (!isSameBucket) {
            if (read != write) {
                list[write] = elRead
            }
            write++
        }
    }

    list.subList(write, list.size).clear()
}

fun keepOrRemoveMatches(text: String, selection: HJSelection, regex: Regex, remove: Boolean): HJSelection? {
    val oldPrimaryIdx = selection.primaryIndex
    val result = selection.ranges.withIndex().filter { (_, range) ->
        regex.containsMatchIn(text.substring(range.from, range.to)).xor(remove)
    }.withIndex().toList()

    if (result.isEmpty()) return null

    val newPrimaryIndex = (result.find { (_, i) -> i.index > oldPrimaryIdx } ?: result.last()).index
    val ranges = result.map { (_, i) -> i.value }

    return HJSelection(ranges, newPrimaryIndex)
}