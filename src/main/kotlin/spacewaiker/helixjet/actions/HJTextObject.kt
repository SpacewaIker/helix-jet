package spacewaiker.helixjet.actions

import com.intellij.lang.FileASTNode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.rml.dfa.utils.toInt
import spacewaiker.helixjet.editor.HJAstObject
import spacewaiker.helixjet.editor.HJLanguageSyntax
import spacewaiker.helixjet.editor.HJRange
import spacewaiker.helixjet.editor.descendantForRange

object HJTextObject {
    val logger = Logger.getInstance(HJTextObject::class.java)

    enum class TextObject {
        Around, Inside, Movement
    }

    /**
     * Transform the given range to select text objects based on the AST.
     */
    fun textObjectAst(
        range: HJRange,
        textObject: TextObject,
        astObject: HJAstObject,
        ast: FileASTNode,
        languageSyntax: HJLanguageSyntax,
    ): HJRange {
        val root = ast
        val smallestNode = root.descendantForRange(range.from, range.to) ?: return range
        val parentOfType = languageSyntax.getParentAstObject(smallestNode, astObject) ?: return range

        val startEndPair = when (textObject) {
            TextObject.Around -> languageSyntax.aroundAstObject(parentOfType, astObject)
            TextObject.Inside -> languageSyntax.insideAstObject(parentOfType, astObject)
            TextObject.Movement -> {
                logger.error("textObjectAst shouldn't be called with textobject Movement!")
                return range
            }
        }

        return if (startEndPair == null) range
        else HJRange(startEndPair.first, startEndPair.second).withDirection(range.direction)
    }

    fun textObjectWord(doc: Document, range: HJRange, textObject: TextObject, long: Boolean): HJRange {
        val pos = range.cursor
        val wordStart = findWordBoundary(doc, pos, HJDirection.Backward, long)
        val wordEnd = when (HJCharCategory.categorize(doc.charsSequence[pos])) {
            HJCharCategory.Whitespace, HJCharCategory.EOL -> pos
            else -> findWordBoundary(doc, pos + 1, HJDirection.Forward, long)
        }

        // special case
        if (wordStart == wordEnd) {
            return HJRange(wordStart, wordEnd)
        }

        return when (textObject) {
            TextObject.Inside -> HJRange(wordStart, wordEnd)
            TextObject.Around -> {
                val whitespaceCountRight = doc.charsSequence.drop(wordEnd).takeWhile(Char::isWhitespace).count()

                if (whitespaceCountRight > 0) {
                    HJRange(wordStart, wordEnd + whitespaceCountRight)
                } else {
                    val whitespaceCountLeft =
                        doc.charsSequence.reversed().drop(doc.textLength - wordStart).takeWhile(Char::isWhitespace)
                            .count()
                    HJRange(wordStart - whitespaceCountLeft, wordEnd)
                }
            }

            TextObject.Movement -> {
                logger.error("textObjectWord shouldn't be called with textobject Movement!")
                range
            }
        }
    }

    private fun findWordBoundary(doc: Document, pos: Int, direction: HJDirection, long: Boolean): Int {
        val iter = when (direction) {
            HJDirection.Forward -> doc.charsSequence.drop(pos)
            HJDirection.Backward -> doc.charsSequence.reversed().drop(doc.textLength - pos)
        }
        var prevCategory = when (direction) {
            HJDirection.Forward if pos == 0 -> HJCharCategory.Whitespace
            HJDirection.Forward -> HJCharCategory.categorize(doc.charsSequence[pos - 1])
            HJDirection.Backward if pos == doc.textLength -> HJCharCategory.Whitespace
            HJDirection.Backward -> HJCharCategory.categorize(doc.charsSequence[pos])

        }
        var pos = pos
        for (ch in iter) {
            when (val category = HJCharCategory.categorize(ch)) {
                HJCharCategory.EOL, HJCharCategory.Whitespace -> return pos
                else -> {
                    if (!long && category != prevCategory && pos != 0 && pos != doc.textLength) {
                        return pos
                    }
                    when (direction) {
                        HJDirection.Forward -> pos++
                        HJDirection.Backward -> pos = (pos - 1).coerceAtLeast(0)
                    }
                    prevCategory = category
                }
            }
        }
        return pos
    }

    fun textObjectParagraph(doc: Document, range: HJRange, textObject: TextObject, count: Int): HJRange {
        var line = range.cursorLine(doc)
        val prevLineEmpty = doc.getLine((line - 1).coerceAtLeast(0))!!.isEmpty()
        val currLineEmpty = doc.getLine(line)!!.isEmpty()
        val nextLineEmpty = doc.getLine(line + 1)?.let(String::isEmpty) ?: true
        val lastChar = prevGraphemeBoundary(doc.getLineStartOffset(line + 1)) == range.cursor
        val prevEmptyToLine = prevLineEmpty && !currLineEmpty
        val currEmptyToLine = currLineEmpty && !nextLineEmpty

        // skip character before paragraph boundary
        var lineBack = line // line but backwards
        if (prevEmptyToLine || currEmptyToLine) {
            lineBack++
        }
        // do not include current paragraph on paragraph end (include next)
        if (!(currEmptyToLine && lastChar)) {
            var lines = doc.text.lines().reversed()
            lines = lines.drop(lines.count() - lineBack)
            val linesBool = lines.map(String::isEmpty)
            var i = 0
            while (i < lines.size && linesBool[i]) {
                i++
                lineBack--
            }
            while (i < lines.size && !linesBool[i]) {
                i++
                lineBack--
            }
        }

        // skip character after paragraph boundary
        if (currEmptyToLine && lastChar) {
            line++
        }
        val lines = doc.text.lines().drop(line).map(String::isEmpty)
        var linesIdx = 0
        var countDone = 0 // count how many non-whitespace paragraphs done
        repeat(count) {
            var done = false
            while (linesIdx < lines.size && !lines[linesIdx]) {
                linesIdx++
                line++
                done = true
            }
            while (linesIdx < lines.size && lines[linesIdx]) {
                linesIdx++
                line++
            }
            countDone += done.toInt()
        }

        // search one paragraph backwards for last paragraph
        // makes 'map' at the end of the paragraph with trailing newlines useful
        val lastParagraph = countDone != count && linesIdx >= lines.size
        if (lastParagraph) {
            var lines = doc.text.lines().reversed()
            lines = lines.drop(lines.count() - lineBack)
            val linesBool = lines.map(String::isEmpty)
            var i = 0
            while (i < linesBool.size && linesBool[i]) {
                i++
                lineBack--
            }
            while (i < linesBool.size && !linesBool[i]) {
                i++
                lineBack--
            }
        }

        // handle last whitespaces part separately depending on textobject
        when (textObject) {
            TextObject.Around -> {}
            TextObject.Inside -> {
                // remove last whitespace paragraph
                var lines = doc.text.lines().reversed()
                lines = lines.drop(lines.count() - line)
                val linesBool = lines.map(String::isEmpty).listIterator()
                while (linesBool.hasNext() && linesBool.next()) line--
            }

            TextObject.Movement -> logger.error("textObjectParagraph shouldn't be called with textobject Movement!")
        }

        val anchor = doc.getLineStartOffset(lineBack)
        val head = if (line < doc.lineCount) doc.getLineStartOffset(line) else doc.textLength
        return HJRange(anchor, head)
    }

    fun textObjectPairSurround(
        ast: FileASTNode?,
        doc: Document,
        range: HJRange,
        textObject: TextObject,
        ch: Char,
        count: Int,
    ): HJRange {
        return textObjectPairSurroundImpl(ast, doc, range, textObject, ch, count)
    }

    fun textObjectPairSurroundClosest(
        ast: FileASTNode?,
        doc: Document,
        range: HJRange,
        textObject: TextObject,
        count: Int,
    ): HJRange {
        return textObjectPairSurroundImpl(ast, doc, range, textObject, null, count)
    }

    private fun textObjectPairSurroundImpl(
        ast: FileASTNode?,
        doc: Document,
        range: HJRange,
        textObject: TextObject,
        ch: Char?,
        count: Int,
    ): HJRange {
        val pairPos = if (ch != null) HJSurround.findNthPairsPos(ast, doc, ch, range, count)
        else HJSurround.findNthClosestPairsPos(ast, doc, range, count)

        return pairPos?.let { (anchor, head) ->
            when (textObject) {
                TextObject.Inside -> {
                    if (anchor < head) HJRange(nextGraphemeBoundary(doc, anchor), head)
                    else HJRange(anchor, nextGraphemeBoundary(doc, head))
                }

                TextObject.Around -> {
                    if (anchor < head) HJRange(anchor, nextGraphemeBoundary(doc, head))
                    else HJRange(nextGraphemeBoundary(doc, anchor), head)
                }

                TextObject.Movement -> {
                    logger.error("textObjectSurround shouldn't be called with textobject Movement!")
                    null
                }
            }
        } ?: range
    }
}