package spacewaiker.helixjet.editor

import com.intellij.lang.ASTNode
import com.intellij.lang.tree.util.parents
import com.intellij.lang.tree.util.siblings

/**
 * Get the smallest node within this node that spans the given range.
 */
fun ASTNode.descendantForRange(start: Int, end: Int): ASTNode? {
    if (startOffset > start || startOffset + textLength - 1 < end) return null

    for (child in getChildren(null)) {
        val result = child.descendantForRange(start, end)
        if (result != null) return result
    }

    return this
}

/**
 * Get the smallest named node within this node that spans the given range.
 */
fun ASTNode.namedDescendantForRange(start: Int, end: Int): ASTNode? {
    if (!isNamed || startOffset > start || startOffset + textLength < end) return null

    for (child in getChildren(null)) {
        val result = child.namedDescendantForRange(start, end)
        if (result != null) return result
    }

    return this
}

val ASTNode.isNamed get() = firstChildNode != null

val ASTNode.nextSibling get() = siblings(true, false).firstOrNull()

val ASTNode.prevSibling get() = siblings(false, false).firstOrNull()

val ASTNode.parent get() = parents(false).firstOrNull()