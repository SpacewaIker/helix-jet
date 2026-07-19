package spacewaiker.helixjet.utils

fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

// From fleet.fastutil
// https://github.com/JetBrains/intellij-community/blob/b65670db5a747c44713c8635027b9fc215e9b542/fleet/fastutil/srcCommonMain/fleet/fastutil/Arrays.kt#L46
fun <T> Iterator<T>.skip(n: Int): Int {
    if (n < 0) throw IllegalArgumentException("Argument must be nonnegative: $n")
    var i = n
    while (i-- != 0 && hasNext()) next()
    return n - i - 1
}

// From fleet.util
// https://github.com/JetBrains/intellij-community/blob/71ec005d4ed9d6d43e46dc3baaf0fd7bb83bb87d/fleet/util/core/srcCommonMain/fleet/util/CollectionsUtil.kt#L79
fun <T> ListIterator<T>.backwards(): ListIterator<T> {
    return object : ListIterator<T> {
        override fun hasNext(): Boolean = this@backwards.hasPrevious()
        override fun hasPrevious(): Boolean = this@backwards.hasNext()
        override fun next(): T = this@backwards.previous()
        override fun previous(): T = this@backwards.next()
        override fun nextIndex(): Int = this@backwards.previousIndex()
        override fun previousIndex(): Int = this@backwards.nextIndex()
    }
}

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

fun java.nio.file.Path.anyCumulative(f: (java.nio.file.Path) -> Boolean) =
    runningFold<java.nio.file.Path, java.nio.file.Path?>(null) { acc, path ->
        acc?.resolve(path) ?: path
    }.filterNotNull().any(f)