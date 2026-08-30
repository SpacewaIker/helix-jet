package spacewaiker.helixjet.actions

import com.intellij.openapi.editor.Document
import com.intellij.rml.dfa.Quadruple
import spacewaiker.helixjet.config.HJLanguage
import spacewaiker.helixjet.editor.HJChange
import spacewaiker.helixjet.editor.HJRange
import spacewaiker.helixjet.editor.HJSelection
import spacewaiker.helixjet.editor.HJTransaction
import kotlin.math.min

object HJComment {
    const val DEFAULT_COMMENT_TOKEN = "#"

    sealed class CommentChange {
        data class Commented(
            val range: HJRange,
            val startPos: Int,
            val endPos: Int,
            val startMargin: Boolean,
            val endMargin: Boolean,
            val startToken: String,
            val endToken: String,
        ) : CommentChange()

        data class Uncommented(
            val range: HJRange,
            val startPos: Int,
            val endPos: Int,
            val startToken: String,
            val endToken: String,
        ) : CommentChange()

        data class Whitespace(val range: HJRange) : CommentChange()
    }


    fun toggleLineComments(doc: Document, selection: HJSelection, token: String?): HJTransaction {
        val text = doc.text

        val token = token ?: DEFAULT_COMMENT_TOKEN
        val comment = "$token "

        val lines = mutableListOf<Int>()

        var minNextLine = 0
        for (selection in selection.ranges) {
            var (start, end) = selection.lineRange(doc)
            start = start.coerceIn(minNextLine, doc.lineCount - 1)
            end = (end + 1).coerceAtMost(doc.lineCount - 1)

            lines.addAll(start until end)
            minNextLine = end
        }

        val (commented, toChange, min, margin) = findLineComment(token, text, lines)

        val changes = mutableListOf<HJChange>()

        for (line in toChange) {
            val pos = doc.getLineStartOffset(line) + min

            if (!commented) {
                // comment line
                changes.add(HJChange.Insert(pos, comment))
            } else {
                // uncomment line
                changes.add(HJChange.Delete(pos, pos + token.length + margin))
            }
        }

        return HJTransaction(changes)
    }

    fun splitLinesOfSelection(doc: Document, selection: HJSelection): HJSelection {
        val ranges = mutableListOf<HJRange>()

        for (range in selection.ranges) {
            val (lineStart, lineEnd) = range.lineRange(doc)
            var pos = doc.getLineStartOffset(lineStart)
            for (line in doc.text.substring(pos, doc.getLineEndOffset(lineEnd)).lines()) {
                val start = pos
                pos += line.length
                ranges.add(HJRange(start, pos))
            }
        }

        return HJSelection(ranges, 0)
    }

    fun findBlockComments(
        tokens: List<HJLanguage.BlockCommentToken>,
        doc: Document,
        selection: HJSelection,
    ): Pair<Boolean, List<CommentChange>> {
        var commented = true
        var onlyWhitespace = true
        val commentChanges = mutableListOf<CommentChange>()
        val defaultTokens = tokens.firstOrNull() ?: HJLanguage.BlockCommentToken.DEFAULT
        var startToken = defaultTokens.start
        var endToken = defaultTokens.end

        val tokens = tokens.toMutableList()
        // sort the tokens by length, so longer tokens will match first
        tokens.sortWith { a, b ->
            if (a.start.length == b.start.length) b.end.length.compareTo(a.end.length)
            else b.start.length.compareTo(a.start.length)
        }
        for (range in selection.ranges) {
            val selectionString = range.slice(doc) ?: continue

            val startPos = firstNonWhitespaceChar(selectionString)
            val endPos = lastNonWhitespaceChar(selectionString)
            if (startPos != null && endPos != null) {
                var lineCommented = false
                var afterStart = 0
                var beforeEnd = 0
                val len = (endPos + 1) - startPos

                for ((start, end) in tokens) {
                    val startLen = start.length
                    val endLen = end.length
                    afterStart = startPos + startLen
                    beforeEnd = (endPos - endLen).coerceAtLeast(0)

                    if (len >= startLen + endLen) {
                        val startFragment = selectionString.substring(startPos, afterStart)
                        val endFragment = selectionString.substring(beforeEnd + 1, endPos + 1)

                        // block commented with these tokens
                        if (startFragment == start && endFragment == end) {
                            startToken = start
                            endToken = end
                            lineCommented = true
                            break
                        }
                    }
                }

                if (!lineCommented) {
                    commentChanges.add(
                        CommentChange.Uncommented(
                            range,
                            startPos,
                            endPos,
                            defaultTokens.start,
                            defaultTokens.end,
                        )
                    )
                    commented = false
                } else {
                    commentChanges.add(
                        CommentChange.Commented(
                            range,
                            startPos,
                            endPos,
                            selectionString.getOrNull(afterStart) == ' ',
                            afterStart != beforeEnd && selectionString.getOrNull(beforeEnd) == ' ',
                            startToken,
                            endToken,
                        )
                    )
                }
                onlyWhitespace = false
            } else {
                commentChanges.add(CommentChange.Whitespace(range))
            }
        }

        if (onlyWhitespace) {
            commented = false
        }

        return Pair(commented, commentChanges)
    }

    fun createBlockCommentTransaction(
        commented: Boolean,
        commentChanges: List<CommentChange>,
    ): Pair<HJTransaction, List<HJRange>> {
        val changes = mutableListOf<HJChange>()
        val ranges = mutableListOf<HJRange>()
        var offs = 0

        for (change in commentChanges) {
            if (commented) {
                if (change is CommentChange.Commented) {
                    val from = change.range.from
                    changes.add(
                        HJChange.Delete(
                            from + change.startPos,
                            from + change.startPos + change.startToken.length + if (change.startMargin) 1 else 0,
                        )
                    )
                    changes.add(
                        HJChange.Delete(
                            from + change.endPos - change.endToken.length - if (change.endMargin) 1 else 0,
                            from + change.endPos + 1,
                        )
                    )
                }
            } else {
                // uncommented so manually map ranges through changes
                when (change) {
                    is CommentChange.Uncommented -> {
                        val from = change.range.from
                        changes.add(HJChange.Insert(from + change.startPos, "${change.startToken} "))
                        changes.add(HJChange.Insert(from + change.endPos + 1, "${change.endToken} "))

                        val offset = change.startToken.length + change.endToken.length + 2
                        ranges.add(
                            HJRange(from + offs, from + offs + change.endPos + 1 + offset)
                                .withDirection(change.range.direction)
                        )
                        offs += offset
                    }

                    is CommentChange.Commented -> {
                        ranges.add(HJRange(change.range.from + offs, change.range.to + offs))
                    }

                    is CommentChange.Whitespace -> {
                        ranges.add(HJRange(change.range.from + offs, change.range.to + offs))
                    }
                }
            }
        }

        return Pair(HJTransaction(changes), ranges)
    }

    /**
     * Given text, a comment token, and a set of line indices, returns the following:
     * - Whether the given lines should be considered commented
     *     - If any of the lines are uncommented, all lines are considered as such.
     * - The lines to change for toggling comments
     *     - This is all provided lines excluding blank lines
     * - The column of the comment tokens
     *     - Column of existing tokens, if the lines are commented; column to place tokens at otherwise.
     * - The margin to the right of the comment tokens
     *     - Defaults to `1`. If any existing comment token is not followed by a space, changes to `0`.
     */
    private fun findLineComment(
        token: String,
        text: String,
        lines: List<Int>,
    ): Quadruple<Boolean, List<Int>, Int, Int> {
        var commented = true
        val toChange = mutableListOf<Int>()
        var min = Int.MAX_VALUE // minimum col for firstNonWhitespaceChar
        var margin = 1
        val tokenLen = token.length

        val linesList = text.lines()

        for (line in lines) {
            val lineString = linesList[line]
            firstNonWhitespaceChar(lineString)?.let { pos ->
                val len = lineString.length

                min = min(min, pos)

                // line can be shorter than pos + token len
                val fragment = lineString.substring(pos, min(pos + tokenLen, len))

                // as soon as one of the non-blank lines doesn't have a comment, the whole block is considered uncommented
                if (fragment != token) {
                    commented = false
                }

                // determine margin of 0 or 1 for uncommenting; if any comment token is not followed by a space, a
                // margin of 0 is used for all lines.
                if (lineString.getOrNull(pos + tokenLen) != ' ') {
                    margin = 0
                }

                // blank lines don't get pushed
                toChange.add(line)
            }
        }

        return Quadruple(commented, toChange, min, margin)
    }
}