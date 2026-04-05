package spacewaiker.helixjet.actions

import com.intellij.openapi.diagnostic.Logger

object HJCharHandling {
    val logger = Logger.getInstance(this::class.java)
}

/**
 * Character category, analogous to `helix_core::chars::CharCategory`.
 * Does not have helix's UNKNOWN category.
 */
enum class HJCharCategory {
    WHITESPACE, EOL, WORD, PUNCTUATION;

    companion object {
        fun categorize(c: Char) = if (charIsLineEnding(c)) EOL
        else if (c.isWhitespace()) WHITESPACE
        else if (c.isLetterOrDigit() || c == '_') WORD
        else PUNCTUATION
    }
}

fun charIsLineEnding(c: Char): Boolean {
    if (c == '\r') HJCharHandling.logger.error("CR encountered. Newline logic will not work.")
    return c == '\n'
}