package spacewaiker.helixjet.actions

import com.intellij.lang.ASTNode
import com.intellij.lang.FileASTNode
import com.intellij.lang.tree.util.children
import com.intellij.openapi.editor.Document
import spacewaiker.helixjet.editor.*

const val MATCH_LIMIT = 16
const val MAX_PLAINTEXT_SCAN = 10000

object HJMatchBrackets {
    val brackets = listOf(
        Pair('(', ')'),
        Pair('{', '}'),
        Pair('[', ']'),
        Pair('<', '>'),
        Pair('‘', '’'),
        Pair('“', '”'),
        Pair('«', '»'),
        Pair('「', '」'),
        Pair('（', '）'),
    )

    // The difference between BRACKETS and PAIRS is that we can find matching BRACKETS in a plain text file, but we
    // can't do the same for PAIRs. PAIRS also contains all BRACKETS.
    val pairs = brackets + listOf(
        Pair('"', '"'),
        Pair('\'', '\''),
        Pair('`', '`'),
        Pair('|', '|'),
    )

    /**
     * Returns the open and closing chars pair. If not found in HJMatchBrackets.pairs returns (ch, ch)
     */
    fun getPair(ch: Char) = pairs.find { it.first == ch || it.second == ch } ?: Pair(ch, ch)

    fun isOpenBracket(ch: Char) = brackets.any { (l, _) -> l == ch }
    fun isCloseBracket(ch: Char) = brackets.any { (_, r) -> r == ch }
    fun isValidBracket(ch: Char) = brackets.any { (l, r) -> l == ch || r == ch }
    fun isOpenPair(ch: Char) = pairs.any { (l, _) -> l == ch }
    fun isClosePair(ch: Char) = pairs.any { (_, r) -> r == ch }
    fun isValidPair(ch: Char) = pairs.any { (l, r) -> l == ch || r == ch }

    /**
     * Returns the position of the matching bracket under cursor.
     *
     * If the cursor is on the opening bracket, the position of the closing bracket is returned. If the cursor is on
     * the closing bracket, the position of the opening bracket is returned.
     *
     * If the cursor is not on a bracket, null is returned
     *
     * If no matching bracket is found, null is returned
     */
    fun findMatchingBracket(ast: FileASTNode, doc: Document, pos: Int): Int? {
        if (pos >= doc.textLength || !isValidPair(doc.charsSequence[pos])) return null

        return findPair(ast, doc, pos, false)
    }

    /**
     * Returns the position of the bracket that is closing the current scope.
     *
     * If the cursor is on an opening or closing bracket, the function behaves equivalent to `findMatchingBracket`
     *
     * If the cursor position is within a scope, the function searches for the surrounding scope that is surrounded by
     * brackets and returns the position of the closing bracket for that scope.
     *
     * If no surrounding scope is found, the function returns null
     */
    fun findMatchingBracketFuzzy(ast: FileASTNode, doc: Document, pos: Int): Int? {
        return findPair(ast, doc, pos, true)
    }

    private fun findPair(ast: FileASTNode, doc: Document, pos: Int, traverseParents: Boolean): Int? {
        val root = ast
        var node = root.descendantForRange(pos, pos) ?: return null

        while (true) {
            if (node.isNamed && node.children().count() >= 2) {
                val open = node.firstChildNode
                val close = node.lastChildNode

                val startCh = asChar(doc, open)
                val endCh = asChar(doc, close)
                if (startCh != null && endCh != null) {
                    val (startPos, open) = startCh
                    val (endPos, close) = endCh

                    if (pairs.contains(Pair(open, close)) && startPos <= pos && pos <= endPos) {
                        if (endPos == pos) {
                            return startPos
                        }

                        // We return the end char if the cursor is either on the start char or at some arbitrary
                        // position between start and end char
                        if (traverseParents || startPos == pos) {
                            return endPos
                        }
                    }
                }
            }
            // this node itself wasn't a pair but maybe its siblings are

            val closePair = asClosePair(doc, node)
            if (closePair != null) {
                val (startChar, endChar) = closePair
                val pairStart = findPairEnd(doc, node.prevSibling, startChar, endChar, HJDirection.Backward)
                if (pairStart != null) {
                    return pairStart
                }
            }
            val openPair = asOpenPair(doc, node)
            if (openPair != null) {
                val (startChar, endChar) = openPair
                val pairEnd = findPairEnd(doc, node.nextSibling, startChar, endChar, HJDirection.Forward)
                if (pairEnd != null) {
                    return pairEnd
                }
            }

            if (traverseParents) {
                for (sibling in generateSequence(node.nextSibling, ASTNode::nextSibling).take(MATCH_LIMIT)) {
                    val (startChar, endChar) = asClosePair(doc, sibling) ?: continue
                    if (findPairEnd(doc, sibling.prevSibling, startChar, endChar, HJDirection.Backward) != null) {
                        return sibling.startOffset
                    }
                }
            } else if (node.isNamed) {
                break
            }

            node = node.parent ?: break
        }
        val node_ = root.namedDescendantForRange(pos, pos + 1) ?: return null
        if (node_.children().count() != 0) return null
        val nodeStart = node_.startOffset
        val nodeText = node_.text
        return findMatchingBracketPlaintext(nodeText, pos - nodeStart)?.let { it + nodeStart }
    }

    /**
     * Returns the position of the matching bracket under cursor. This function works on plain text and ignores AST.
     * The search is limited to `MAX_PLAINTEXT_SCAN` characters.
     *
     * If the cursor is on the opening bracket, the position of the closing bracket is returned. If the cursor is on the
     * closing bracket, the position of the opening bracket is returned.
     *
     * If the cursor is not on a bracket, null is returned.
     * If no matching bracket is found, null is returned.
     */
    fun findMatchingBracketPlaintext(doc: String, cursorPos: Int): Int? {
        val bracket = doc.getOrNull(cursorPos) ?: return null
        val matchingBracket = getPair(bracket).let { if (it.first == bracket) it.second else it.first }

        // don't do anything when the cursor is not on top of a bracket
        if (!isValidBracket(bracket)) return null

        // determine the direction of the matching
        val isFwd = isOpenBracket(bracket)
        val charsIter = if (isFwd) {
            doc.drop(cursorPos + 1)
        } else {
            doc.take(cursorPos).reversed()
        }

        var openCnt = 1

        for ((i, candidate) in charsIter.take(MAX_PLAINTEXT_SCAN).withIndex()) {
            if (candidate == bracket) {
                openCnt++
            } else if (candidate == matchingBracket) {
                // return when all pending brackets have been closed
                if (openCnt == 1) {
                    return if (isFwd) {
                        cursorPos + i + 1
                    } else {
                        cursorPos - i - 1
                    }
                }
                openCnt--
            }
        }

        return null
    }

    /**
     * If node is a single char, return it and its char position
     */
    private fun asChar(doc: Document, node: ASTNode): Pair<Int, Char>? {
        if (node.textLength != 1) return null
        return Pair(node.startOffset, doc.charsSequence[node.startOffset])
    }

    /**
     * Tests if this node is a pair close char and returns the expected open char and close char contained in this node
     */
    private fun asClosePair(doc: Document, node: ASTNode): Pair<Char, Char>? {
        val close = asChar(doc, node)?.second ?: return null

        return pairs.find { (_, close_) -> close_ == close }
            ?.let { (open, close) -> Pair(close, open) }
    }

    /**
     * Checks if `node` or its siblings (at most MATCH_LIMIT nodes) is the specified closing char
     *
     * @returns The position of the found node or null otherwise
     */
    private fun findPairEnd(
        doc: Document,
        node: ASTNode?,
        startChar: Char,
        endChar: Char,
        direction: HJDirection,
    ): Int? {
        val advance = when (direction) {
            HJDirection.Forward -> { node: ASTNode -> node.nextSibling }
            HJDirection.Backward -> { node: ASTNode -> node.prevSibling }
        }
        var depth = 0
        generateSequence(node, advance)
            .take(MATCH_LIMIT)
            .find { node ->
                val (pos, c) = asChar(doc, node) ?: return@find false
                if (c == endChar) {
                    if (depth == 0) {
                        return pos
                    }
                    depth--
                } else if (c == startChar) {
                    depth++
                }
                false
            }

        return null
    }

    /**
     * Tests if this node is a pair open char and returns the expected close char and open char contained in this node
     */
    private fun asOpenPair(doc: Document, node: ASTNode): Pair<Char, Char>? {
        val open = asChar(doc, node)?.second ?: return null

        return pairs.find { (open_, _) -> open_ == open }
    }
}