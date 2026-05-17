package spacewaiker.helixjet.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import spacewaiker.helixjet.editor.HJState
import java.util.regex.PatternSyntaxException

enum class HJFileOpenLocation {
    InPlace, BackgroundTab, VSplit, HSplit,
}

fun nthPrevGraphemeBoundary(charIdx: Int, n: Int): Int {
    return (charIdx - n).coerceAtLeast(0)
}

fun prevGraphemeBoundary(charIdx: Int): Int {
    return nthPrevGraphemeBoundary(charIdx, 1)
}

fun nthNextGraphemeBoundary(doc: Document, charIdx: Int, n: Int): Int {
    return (charIdx + n).coerceAtMost(doc.textLength)
}

fun nextGraphemeBoundary(doc: Document, charIdx: Int): Int {
    return nthNextGraphemeBoundary(doc, charIdx, 1)
}

/**
 * Get line content at line `line`. Checks that the line number is valid
 */
fun Document.getLine(line: Int): String? {
    return if (line > this.lineCount - 1) null else {
        this.getText(TextRange(this.getLineStartOffset(line), this.getLineEndOffset(line)))
    }
}

/**
 * Return index of first non whitespace character in string, if any.
 * Optionally offset by a provided value
 */
fun firstNonWhitespaceChar(line: String, startOffset: Int = 0): Int? {
    val idx = line.asSequence().indexOfFirst { !it.isWhitespace() }
    return if (idx == -1) null else startOffset + idx
}

/**
 * Returns the longest matching comment token of the given line (if it exists)
 */
fun getCommentToken(doc: Document, tokens: List<String>, lineNum: Int): String? {
    val line = doc.getLine(lineNum) ?: return null
    val start = firstNonWhitespaceChar(line) ?: return null

    return tokens
        .filter { token -> line.substring(start).startsWith(token) }
        .maxByOrNull { token -> token.length }
}

/**
 * Creates a Regex using settings from user config
 */
fun regex(pattern: String, smartCase: Boolean? = null, ignoreCase: Boolean? = null): Regex? {
    val smartCase = smartCase ?: HJState.config.searchSmartCase
    val ignoreCase = ignoreCase ?: smartCase && !pattern.any { it.isUpperCase() }

    val options = mutableSetOf<RegexOption>()
    if (ignoreCase) options.add(RegexOption.IGNORE_CASE)
    return try {
        Regex(pattern, options)
    } catch (_: PatternSyntaxException) {
        null
    }
}

fun getIndentForLine(psiFile: PsiFile, lineStartOffset: Int): String {
    return ApplicationManager.getApplication().runReadAction<String> {
        CodeStyleManager.getInstance(psiFile.project)
            .getLineIndent(psiFile, lineStartOffset) ?: ""
    }
}

/**
 * Returns an iterator of the char ranges in src that contain a path
 */
fun findPaths(src: String, matchSingleFile: Boolean): List<IntRange> {
    val regex = getPathRegex("", "", matchSingleFile)
    return regex.findAll(src).map { it.range }.toList()
}

@Suppress("ConvertToStringTemplate")
private fun getPathRegex(prefix: String, postfix: String, matchSingleFile: Boolean): Regex {
    val pathComponentRegex = """[\w@.\-+#$%?!,;~&]|[\\]\s"""
    val bracedEnvRegex = """\$\{(?:""" + pathComponentRegex + """|[/:=])+\}"""

    val firstComponent = "(?:$bracedEnvRegex|(?:$pathComponentRegex))"
    // for all components except the first we allow an equals so that `foo=/bar/baz` does not include foo.
    // This is primarily intended for url queries (where an equals is never in the first component)
    val component = "(?:$firstComponent|=)"
    val sep = """[/\\]""" // match \ on non-windows OSs too, why not
    val urlPrefix = """[\w+\-.]+://??"""
    // single slash handles most windows prefixes (like \\server\...) but `\\?\C:\..` (and C:\) needs special handling,
    // since we don't allow : in the path components (so that colon separated paths and <path>:<line> work)
    val pathPrefix = """\\\\\?\\\w:|\w:|\\|"""

    val pathStart = "(?:$firstComponent+|~|$pathPrefix$urlPrefix)"
    val optional = if (matchSingleFile) "|$pathStart" else ""
    val pathRegex = "$prefix(?:$pathStart?(?:(?:$sep$component+)+$sep?|$sep)$optional)$postfix"
    return Regex(pathRegex)
}