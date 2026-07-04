package spacewaiker.helixjet.actions

import com.intellij.openapi.editor.Document
import io.github.treesitter.ktreesitter.Node
import spacewaiker.helixjet.treesitter.HJLanguageSyntax
import spacewaiker.helixjet.treesitter.getStartOffset
import spacewaiker.helixjet.treesitter.offsetToPoint

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
    fun findMatchingBracket(syntax: HJLanguageSyntax, document: Document, pos: Int): Int? {
        if (pos >= document.textLength || !isValidPair(document.charsSequence[pos])) return null

        return findPair(syntax, document, pos, false)
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
    fun findMatchingBracketFuzzy(syntax: HJLanguageSyntax, document: Document, pos: Int): Int? {
        return findPair(syntax, document, pos, true)
    }

    private fun findPair(syntax: HJLanguageSyntax, document: Document, pos: Int, traverseParents: Boolean): Int? {
        val posPoint = offsetToPoint(document, pos)
        val root = syntax.tree.rootNode
        var node = root.descendant(posPoint, posPoint) ?: return null

        while (true) {
            if (node.isNamed && node.children.count() >= 2) {
                val open = node.child(0u)!!
                val close = node.child(node.childCount - 1u)!!

                val startCh = asChar(document, open)
                val endCh = asChar(document, close)
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

            val closePair = asClosePair(document, node)
            if (closePair != null) {
                val (startChar, endChar) = closePair
                val pairStart = findPairEnd(document, node.prevSibling, startChar, endChar, HJDirection.Backward)
                if (pairStart != null) {
                    return pairStart
                }
            }
            val openPair = asOpenPair(document, node)
            if (openPair != null) {
                val (startChar, endChar) = openPair
                val pairEnd = findPairEnd(document, node.nextSibling, startChar, endChar, HJDirection.Forward)
                if (pairEnd != null) {
                    return pairEnd
                }
            }

            if (traverseParents) {
                for (sibling in generateSequence(node.nextSibling, Node::nextSibling).take(MATCH_LIMIT)) {
                    val (startChar, endChar) = asClosePair(document, sibling) ?: continue
                    if (findPairEnd(document, sibling.prevSibling, startChar, endChar, HJDirection.Backward) != null) {
                        return sibling.getStartOffset(document)
                    }
                }
            } else if (node.isNamed) {
                break
            }

            node = node.parent ?: break
        }
        val node_ = root.namedDescendant(posPoint, offsetToPoint(document, pos + 1)) ?: return null
        if (node_.children.count() != 0) return null
        val nodeStart = node_.getStartOffset(document)
        val nodeText = node_.text()?.toString() ?: ""
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
    private fun asChar(document: Document, node: Node): Pair<Int, Char>? {
        if (node.endByte - node.startByte != 1u) return null
        val pos = node.getStartOffset(document)
        return Pair(pos, document.charsSequence[pos])
    }

    /**
     * Tests if this node is a pair close char and returns the expected open char and close char contained in this node
     */
    private fun asClosePair(document: Document, node: Node): Pair<Char, Char>? {
        val close = asChar(document, node)?.second ?: return null

        return pairs.find { (_, close_) -> close_ == close }
            ?.let { (open, close) -> Pair(close, open) }
    }

    /**
     * Checks if `node` or its siblings (at most MATCH_LIMIT nodes) is the specified closing char
     *
     * @returns The position of the found node or null otherwise
     */
    private fun findPairEnd(
        document: Document,
        node: Node?,
        startChar: Char,
        endChar: Char,
        direction: HJDirection,
    ): Int? {
        val advance = when (direction) {
            HJDirection.Forward -> { node: Node -> node.nextSibling }
            HJDirection.Backward -> { node: Node -> node.prevSibling }
        }
        var depth = 0
        generateSequence(node, advance)
            .take(MATCH_LIMIT)
            .find { node ->
                val (pos, c) = asChar(document, node) ?: return@find false
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
    private fun asOpenPair(document: Document, node: Node): Pair<Char, Char>? {
        val open = asChar(document, node)?.second ?: return null

        return pairs.find { (open_, _) -> open_ == open }
    }
}