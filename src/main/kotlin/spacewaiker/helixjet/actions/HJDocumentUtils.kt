package spacewaiker.helixjet.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import spacewaiker.helixjet.editor.HJState
import java.nio.file.Path
import java.util.regex.PatternSyntaxException
import javax.swing.JSplitPane
import kotlin.io.path.Path

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

fun openFile(cx: HJActionContext, path: String, openLocation: HJFileOpenLocation = HJFileOpenLocation.InPlace) {
    val path = Path(path)
    openFile(cx, path, openLocation)
}

fun openFile(cx: HJActionContext, path: Path, openLocation: HJFileOpenLocation = HJFileOpenLocation.InPlace) {
    // resolve WRT cx file path
    val cxPath = cx.hjEditor.editor.virtualFile.toNioPath()
    val filePath = cxPath.resolve(path)
    var file = VfsUtil.findFile(filePath, true)

    if (file == null) {
        // resolve WRT project root
        val projDir = cx.hjEditor.editor.project!!.guessProjectDir()?.toNioPath()
        if (projDir != null) {
            val filePath = projDir.resolve(path)
            file = VfsUtil.findFile(filePath, true)
        }
    }

    if (file == null) {
        cx.hjEditor.setError("Could not find file $path")
        return
    }

    openFile(cx, file, openLocation)
}

fun openFile(cx: HJActionContext, file: VirtualFile, openLocation: HJFileOpenLocation = HJFileOpenLocation.InPlace) {
    val project = cx.hjEditor.editor.project!!

    when (openLocation) {
        HJFileOpenLocation.InPlace -> {
            FileEditorManager.getInstance(project).openFile(file, true)
        }

        HJFileOpenLocation.BackgroundTab -> {
            val fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)

            // "false" parameter should open in background but doesn't...
            // so we need to manually re-focus the old editor. Not great but only workaround found so far
            fileEditorManagerEx.openFile(file, false)

            val currentTextEditor = TextEditorProvider.getInstance().getTextEditor(cx.hjEditor.editor)
            var composite: EditorComposite? = null
            fileEditorManagerEx.windows.find { window ->
                window.allComposites.any {
                    if (it.allEditors.contains(currentTextEditor)) {
                        composite = it
                        true
                    } else false
                }
            }?.setSelectedComposite(composite!!, true)
        }

        HJFileOpenLocation.HSplit, HJFileOpenLocation.VSplit -> {
            val fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
            val currentWindow = fileEditorManagerEx.currentWindow ?: return
            val orientation =
                if (openLocation == HJFileOpenLocation.HSplit) JSplitPane.VERTICAL_SPLIT else JSplitPane.HORIZONTAL_SPLIT
            currentWindow.split(orientation, true, file, true)
        }
    }
}

fun CharArray.indexOfOrNull(char: Char) = indexOf(char).let { if (it == -1) null else it }