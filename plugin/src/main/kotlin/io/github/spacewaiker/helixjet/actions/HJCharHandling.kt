package io.github.spacewaiker.helixjet.actions

import com.intellij.openapi.diagnostic.Logger

object HJCharHandling {
    val logger = Logger.getInstance(this::class.java)
}

/**
 * Character category, analogous to `helix_core::chars::CharCategory`.
 * Does not have helix's UNKNOWN category.
 */
enum class HJCharCategory {
    Whitespace, EOL, Word, Punctuation;

    companion object {
        fun categorize(c: Char) = if (charIsLineEnding(c)) EOL
        else if (c.isWhitespace()) Whitespace
        else if (c.isLetterOrDigit() || c == '_') Word
        else Punctuation
    }
}

fun charIsLineEnding(c: Char): Boolean {
    if (c == '\r') HJCharHandling.logger.error("CR encountered. Newline logic will not work.")
    return c == '\n'
}

fun charIsWord(c: Char): Boolean {
    return c.isLetterOrDigit() || c == '_'
}