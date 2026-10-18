package io.github.spacewaiker.helixjet.actions

import com.intellij.openapi.editor.Document
import io.github.spacewaiker.helixjet.editor.HJRange
import io.github.spacewaiker.helixjet.editor.HJSelection
import io.github.spacewaiker.helixjet.treesitter.HJLanguageSyntax
import io.github.spacewaiker.helixjet.utils.skip
import kotlin.math.max
import kotlin.math.min

object HJSurround {
    /**
     * Find position of surrounding characters around every cursor. Returns null if any positions overlap. Note that the
     * positions are in a flat List. Use getSurroundPos.chunked(2) to get matching pairs of surrounding positions.
     * `ch` can be either closing or opening pair. If 'ch' is null, surround pairs are automatically detected around each
     * cursor (note that this may result in them selecting different surrounding characters for each selection)
     */
    fun getSurroundPos(
        syntax: HJLanguageSyntax?,
        document: Document,
        selection: HJSelection,
        ch: Char?,
        skip: Int,
    ): List<Int>? {
        val changePos = mutableListOf<Int>()

        for (range in selection.ranges) {
            val rangeRaw = if (ch != null) {
                findNthPairsPos(syntax, document, ch, range, skip)
            } else {
                findNthClosestPairsPos(syntax, document, range, skip)
            } ?: return null
            val openPos = min(rangeRaw.first, rangeRaw.second)
            val closePos = max(rangeRaw.first, rangeRaw.second)

            if (changePos.contains(openPos) || changePos.contains(closePos)) {
                return null // cursor overlap
            }
            // ensure the positions are always paired in the forward direction
            changePos.add(min(openPos, closePos))
            changePos.add(max(openPos, closePos))
        }

        return changePos
    }

    /**
     * Find the position of surround pairs of `ch` which can be either a closing or opening pair. `n` will skip n - 1
     * pairs (e.g. n = 2 will discard the first pair found and keep looking)
     */
    fun findNthPairsPos(
        syntax: HJLanguageSyntax?,
        document: Document,
        ch: Char,
        range: HJRange,
        n: Int,
    ): Pair<Int, Int>? {
        if (document.textLength < 2) return null
        if (range.to >= document.textLength) return null

        val (openCh, closeCh) = HJMatchBrackets.getPair(ch)
        val pos = range.cursor

        val (open, close) = if (openCh == closeCh) {
            if (openCh == document.charsSequence[pos]) {
                // Cursor is directly on match character for which the opening and closing pairs are the same.
                // This is potentially ambiguous, because there's no way to know which side of the char we should be searching on
                val matchingPairsPos = (
                        if (syntax == null) HJMatchBrackets.findMatchingBracketPlaintext(document.text, pos)
                        else HJMatchBrackets.findMatchingBracketFuzzy(syntax, document, pos)
                        ) ?: return null
                if (matchingPairsPos > pos) {
                    Pair(pos, matchingPairsPos)
                } else {
                    Pair(matchingPairsPos, pos)
                }
            } else {
                Pair(
                    findNthChar(document, HJCharMatcher.Character(openCh), pos, n, HJDirection.Backward),
                    findNthChar(document, HJCharMatcher.Character(closeCh), pos, n, HJDirection.Forward)
                )
            }
        } else {
            Pair(
                findNthOpenPair(document, openCh, closeCh, pos, n),
                findNthClosePair(document, openCh, closeCh, pos, n)
            )
        }

        // preserve original direction
        return if (open != null && close != null) {
            when (range.direction) {
                HJDirection.Forward -> Pair(open, close)
                HJDirection.Backward -> Pair(close, open)
            }
        } else null
    }

    /**
     * Finds the position of surround paris of any `HJMatchBrackets.pairs` using PSI when possible.
     */
    fun findNthClosestPairsPos(
        syntax: HJLanguageSyntax?,
        document: Document,
        range: HJRange,
        skip: Int,
    ): Pair<Int, Int>? {
        return if (syntax != null) findNthClosestPairsTs(syntax, document, range, skip)
        else findNthClosestPairsPlain(document, range, skip)
    }

    private fun findNthClosestPairsTs(
        syntax: HJLanguageSyntax,
        document: Document,
        range: HJRange,
        skip: Int,
    ): Pair<Int, Int>? {
        var opening = range.from
        // we want to expand the selection if we are already on the found pair, otherwise we would need to subtract 1 from range.to
        var closing = range.to
        var skip = skip

        while (skip > 0) {
            closing = HJMatchBrackets.findMatchingBracketFuzzy(syntax, document, closing) ?: return null
            opening = HJMatchBrackets.findMatchingBracket(syntax, document, closing) ?: return null

            // if we're already in a closing bracket, findMatchingBracketFuzzy will return the position of the opening bracket
            if (closing < opening) {
                opening = closing.also { closing = opening }
            }

            // in case found brackets are partially inside current selection
            if (range.from < opening || closing < range.to - 1) {
                closing = nextGraphemeBoundary(document, closing)
            } else {
                skip--
                if (skip != 0) {
                    closing = nextGraphemeBoundary(document, closing)
                }
            }
        }

        // keep the original direction
        return if (range.direction == HJDirection.Forward) {
            Pair(opening, closing)
        } else {
            Pair(closing, opening)
        }
    }

    private fun findNthClosestPairsPlain(doc: Document, range: HJRange, skip: Int): Pair<Int, Int>? {
        val stack = mutableListOf<Char>()
        val pos = range.from
        var closePos = (pos - 1).coerceAtLeast(0)
        var skip = skip

        for (ch in doc.charsSequence.drop(pos)) {
            closePos++

            if (HJMatchBrackets.isOpenBracket(ch)) {
                // Track open pairs encountered so that we can step over the corresponding close pairs that will come up
                // further down the loop. We want to find a lone close pair whose open pair is before the cursor position
                stack.add(ch)
                continue
            }

            if (!HJMatchBrackets.isCloseBracket(ch)) {
                // we don't care if this character isn't a brace pair item, so short circuit here
                continue
            }

            val (open, close) = HJMatchBrackets.getPair(ch)

            if (stack.lastOrNull() == open) {
                // if we are encountering the closing pair for an opener we just found while traversing, then it's
                // inside the selection and should be skipped over
                stack.removeLast()
                continue
            }

            val openPos = findNthOpenPair(doc, open, close, closePos, 1) ?: continue
            // before we accept this pair, we want to ensure that the pair encloses the range rather than just the cursor
            if (openPos <= pos + 1 && closePos >= range.to - 1) {
                // since we have special conditions for when to accept, we can't just pass the skip parameter on
                // through to the findNth*Pair methods, so we track skips manually here
                if (skip > 1) {
                    skip--
                    continue
                }
                return when (range.direction) {
                    HJDirection.Forward -> Pair(openPos, closePos)
                    HJDirection.Backward -> Pair(closePos, openPos)
                }
            }
        }

        return null
    }

    private fun findNthOpenPair(doc: Document, open: Char, close: Char, pos: Int, n: Int): Int? {
        if (pos >= doc.textLength) return null

        val chars = doc.charsSequence.toList().listIterator()
        chars.skip(pos + 1)

        // adjusts pos for the first iteration, and handles the case of the cursor being *on* the close character
        // which will get falsely stepped over if not skipped here
        if (!chars.hasPrevious()) return null
        if (chars.previous() == open) {
            return pos
        }

        var pos = pos

        repeat(n) {
            var stepOver = 0

            while (true) {
                if (!chars.hasPrevious()) return null
                val c = chars.previous()
                pos = (pos - 1).coerceAtLeast(0)

                // ignore other surround pairs that are enclosed *within* our search scope
                if (c == close) {
                    stepOver++
                } else if (c == open) {
                    if (stepOver == 0) break

                    stepOver = (stepOver - 1).coerceAtLeast(0)
                }
            }
        }

        return pos
    }

    private fun findNthClosePair(doc: Document, open: Char, close: Char, pos: Int, n: Int): Int? {
        if (pos >= doc.textLength) return null

        val chars = doc.charsSequence.toList().listIterator()
        chars.skip(pos)

        if (!chars.hasNext()) return null
        if (chars.next() == close) {
            return pos
        }

        var pos = pos

        repeat(n) {
            var stepOver = 0

            while (true) {
                if (!chars.hasNext()) return null
                val c = chars.next()
                pos++

                // ignore other surround pairs that are enclosed *within* our search scope
                if (c == open) {
                    stepOver++
                } else if (c == close) {
                    if (stepOver == 0) break

                    stepOver = (stepOver - 1).coerceAtLeast(0)
                }
            }
        }

        return pos
    }
}