package spacewaiker.helixjet.utils

fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

fun IntRange.contains(other: IntRange) = this.first <= other.first && other.last <= this.last

/**
 * Returns true if all ranges yielded by subSet are contained by superSet.
 */
fun isSubset(superSet: Iterator<IntRange>, subSet: Iterator<IntRange>, allowEmpty: Boolean): Boolean {
    var superRange = superSet.nextOrNull()
    var subRange = subSet.nextOrNull()
    while (true) {
        when (Pair(superRange != null, subRange != null)) {
            // skip over irrelevant ranges
            Pair(true, true)
                if superRange!!.last <= subRange!!.first && (superRange.first != subRange.first || !allowEmpty)
                -> superRange = superSet.nextOrNull()

            Pair(true, true) -> if (superRange!!.contains(subRange!!)) subRange = subSet.nextOrNull() else return false

            Pair(false, true) -> return false // exhausted superSet, we can't match the remained of subSet

            Pair(true, false), Pair(false, false),
                -> return true // no elements from subSet left to match, i.e. superSet contains subSet
        }
    }
}