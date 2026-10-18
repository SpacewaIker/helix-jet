package io.github.spacewaiker.helixjet.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.*
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import io.github.spacewaiker.helixjet.editor.HJState
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.regex.PatternSyntaxException
import javax.swing.JSplitPane
import kotlin.io.path.Path

val LINE_ENDING_REGEX = Regex("""\r\n|\r|\n""")

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
    return if (line < 0 || line >= this.lineCount) null else {
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

fun lastNonWhitespaceChar(line: String, startOffset: Int = 0): Int? {
    val idx = line.asSequence().indexOfLast { !it.isWhitespace() }
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
fun regex(pattern: String, hjState: HJState? = null, smartCase: Boolean? = null, ignoreCase: Boolean? = null): Regex? {
    val smartCase = smartCase ?: hjState?.config?.searchSmartCase ?: true
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

fun openFile(
    cx: HJActionContext,
    path: String,
    openLocation: HJFileOpenLocation = HJFileOpenLocation.InPlace,
): FileEditor? {
    val path = Path(path)
    return openFile(cx, path, openLocation)
}

fun openFile(
    cx: HJActionContext,
    path: Path,
    openLocation: HJFileOpenLocation = HJFileOpenLocation.InPlace,
): FileEditor? {
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
        return null
    }

    return openFile(cx, file, openLocation)
}

fun openFile(
    cx: HJActionContext,
    file: VirtualFile,
    openLocation: HJFileOpenLocation = HJFileOpenLocation.InPlace,
): FileEditor? {
    val project = cx.hjEditor.editor.project!!

    return when (openLocation) {
        HJFileOpenLocation.InPlace -> {
            FileEditorManager.getInstance(project).openFile(file, true).first()
        }

        HJFileOpenLocation.BackgroundTab -> {
            val fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)

            // "false" parameter should open in background but doesn't...
            // so we need to manually re-focus the old editor. Not great but only workaround found so far
            val openedEditor = fileEditorManagerEx.openFile(file, false).first()

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

            openedEditor
        }

        HJFileOpenLocation.HSplit, HJFileOpenLocation.VSplit -> {
            val fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
            val currentWindow = fileEditorManagerEx.currentWindow ?: return null
            val orientation =
                if (openLocation == HJFileOpenLocation.HSplit) JSplitPane.VERTICAL_SPLIT else JSplitPane.HORIZONTAL_SPLIT
            val editorWindow = currentWindow.split(orientation, true, file, true)
            editorWindow?.selectedComposite?.selectedEditor
        }
    }
}

fun closeFiles(cx: HJActionContext, selector: (FileEditorManagerEx) -> List<VirtualFile>?) {
    val project = cx.hjEditor.editor.project!!
    val fileEditorManagerEx = FileEditorManagerEx.getInstanceEx(project)
    val currentWindow = fileEditorManagerEx.currentWindow ?: return
    for (file in selector(fileEditorManagerEx) ?: return) {
        currentWindow.closeFile(file)
    }
    fileEditorManagerEx.selectedTextEditor?.contentComponent?.requestFocusInWindow()
}

fun quitWindows(cx: HJActionContext, selector: (FileEditorManagerEx) -> List<EditorWindow>?) {
    val project = cx.hjEditor.editor.project!!
    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    for (window in selector(fileEditorManager) ?: return) {
        window.closeAllExcept(null)
    }
}

fun CharArray.indexOfOrNull(char: Char) = indexOf(char).let { if (it == -1) null else it }

const val SEPARATOR = '_'

/**
 * Increment an integer.
 *
 * Supported bases:
 *      2 with prefix 0b
 *      8 with prefix 0o
 *      10 with no prefix
 *      16 with prefix 0x
 *
 * An integer can contain `_` as a separator but may not start or end with a separator.
 * Base 10 integers can go negative, but bases 2, 8, and 16 cannot.
 * All additions and subtractions are saturating.
 */
fun incrementInteger(selectedText: String, amount: Int): String? {
    if (selectedText.isEmpty() || selectedText.endsWith(SEPARATOR) || selectedText.startsWith(SEPARATOR))
        return null

    val radix = if (selectedText.startsWith("0x", true)) 16
    else if (selectedText.startsWith("0o", true)) 8
    else if (selectedText.startsWith("0b", true)) 2
    else 10

    // Get separator indices from right to left
    val separatorRTLIndices = selectedText.reversed()
        .withIndex()
        .mapNotNull { (i, c) -> if (c == SEPARATOR) i else null }

    val word = selectedText.filterNot { it == SEPARATOR }

    val newText = StringBuilder(
        if (radix == 10) {
            val number = word
            val value = number.toLongOrNull() ?: return null
            val newValue = value + amount

            val formatLength = when (Pair(value < 0, newValue < 0)) {
                Pair(true, false) -> number.length - 1
                Pair(false, true) -> number.length + 1
                else -> number.length
            } - separatorRTLIndices.size

            if (number.startsWith('0') || number.startsWith("-0")) {
                newValue.toString().padStart(formatLength, '0')
            } else {
                newValue.toString()
            }
        } else {
            val number = word.substring(2)
            val value = number.toLongOrNull(radix) ?: return null
            val newValue = (value + amount).coerceAtLeast(0)
            val formatLength = selectedText.length - 2 - separatorRTLIndices.size

            when (radix) {
                2 -> "0b" + newValue.toString(2).padStart(formatLength, '0')
                8 -> "0o" + newValue.toString(8).padStart(formatLength, '0')
                16 -> { // uppercase or lowercase
                    val (lowerCount, upperCount) = number.fold(Pair(0, 0)) { (lower, upper), c ->
                        Pair(lower + if (c.isLowerCase()) 1 else 0, upper + if (c.isUpperCase()) 1 else 0)
                    }

                    var numberString = newValue.toString(16).padStart(formatLength, '0')
                    if (upperCount > lowerCount)
                        numberString = numberString.uppercase()

                    "0x$numberString"
                }

                else -> throw Exception("incrementInteger: radix $radix not implemented")
            }
        }
    )

    // Add separators from original number
    for (rtlIndex in separatorRTLIndices) {
        if (rtlIndex < newText.length) {
            val newIndex = (newText.length - rtlIndex).coerceAtLeast(0)
            if (newIndex > 0) {
                newText.insert(newIndex, SEPARATOR)
            }
        }
    }

    // Add in additional separators if necessary
    if (newText.length > selectedText.length && separatorRTLIndices.isNotEmpty()) {
        val spacing = if (separatorRTLIndices.size >= 2) {
            val lastIdx = separatorRTLIndices.size - 1
            separatorRTLIndices[lastIdx] - separatorRTLIndices[lastIdx - 1] - 1
        } else separatorRTLIndices[0]

        val prefixLength = if (radix == 10) 0 else 2
        for ((i, c) in newText.withIndex()) {
            if (c == SEPARATOR) {
                var i = i
                while (i - prefixLength > spacing) {
                    i -= spacing
                    newText.insert(i, SEPARATOR)
                }

                break
            }
        }
    }

    return newText.toString()
}

private val DATETIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")!!,
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")!!,
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")!!,
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")!!,
)

private val DATE_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd")!!,
    DateTimeFormatter.ofPattern("yyyy/MM/dd")!!,
    DateTimeFormatter.ofPattern("E MMM dd yyyy")!!,
    DateTimeFormatter.ofPattern("dd-MMM-yyyy")!!,
    DateTimeFormatter.ofPattern("yyyy MMM dd")!!,
    DateTimeFormatter.ofPattern("MMMM dd, yyyy")!!,
)

private val TIME_FORMATS = listOf(
    DateTimeFormatter.ofPattern("hh:mm:ss a")!!,
    DateTimeFormatter.ofPattern("hh:mm a")!!,
    DateTimeFormatter.ofPattern("hh:mm:ss A")!!,
    DateTimeFormatter.ofPattern("hh:mm A")!!,
    DateTimeFormatter.ofPattern("HH:mm:ss")!!,
    DateTimeFormatter.ofPattern("HH:mm")!!,
)

fun parseDateTime(input: String, format: DateTimeFormatter): LocalDateTime? {
    return try {
        LocalDateTime.parse(input, format)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun parseDate(input: String, format: DateTimeFormatter): LocalDate? {
    return try {
        LocalDate.parse(input, format)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun parseTime(input: String, format: DateTimeFormatter): LocalTime? {
    return try {
        LocalTime.parse(input, format)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * Increment a Date or DateTime
 *
 * If just a Date is selected the day will be incremented.
 * If a DateTime is selected, the second will be incremented.
 */
fun incrementDatetime(selectedText: String, amount: Int): String? {
    if (selectedText.isEmpty()) return null

    // datetime
    for (format in DATETIME_FORMATS) {
        val dateTime = parseDateTime(selectedText, format) ?: continue
        return dateTime.plusSeconds(amount.toLong()).format(format)
    }
    // date
    for (format in DATE_FORMATS) {
        val date = parseDate(selectedText, format) ?: continue
        return date.plusDays(amount.toLong()).format(format)
    }
    // time
    for (format in TIME_FORMATS) {
        val time = parseTime(selectedText, format) ?: continue
        return time.plusMinutes(amount.toLong()).format(format)
    }

    return null
}

/**
 * "Increments" a bool, i.e. switches between true <--> false
 */
fun incrementBool(selectedText: String, amount: Int): String? {
    val value = when (selectedText.lowercase()) {
        "true" -> true
        "false" -> false
        else -> return null
    }

    val newValue = if (amount % 2 == 0) value else !value

    return if (selectedText.all(Char::isUpperCase))
        newValue.toString().uppercase()
    else if (selectedText[0].isUpperCase() && selectedText.substring(1).all(Char::isLowerCase))
        newValue.toString().lowercase().replaceFirstChar(Char::uppercase)
    else
        newValue.toString()
}

val Project.focusedEditor
    get() = (FileEditorManager.getInstance(this).focusedEditor as? TextEditor)?.editor
        ?.let { if (it.contentComponent.hasFocus()) it else null }

val Project.focusedHJEditor
    get() = focusedEditor?.let { HJState.getInstance(this).getHJEditor(it) }

/**
 * Get a list of all files in the project, ignoring files specified in .gitignore, .p4ignore, etc.
 */
fun getAllProjectFiles(project: Project): List<VirtualFile> {
    return buildList {
        getAllProjectFiles(project) { add(it) }
    }
}

/**
 * Get a list of all files in the project, ignoring files specified in .gitignore, .p4ignore, etc.
 */
fun getAllProjectFiles(project: Project, callback: (VirtualFile) -> Unit) {
    val changeListManager = ChangeListManager.getInstance(project)
    val projectDir = project.guessProjectDir()!!
    val dirs = mutableListOf(projectDir)
    val hjState = HJState.getInstance(project)

    val customIgnore = hjState.config.ignoreList
    customIgnore?.patterns?.forEach { (isWhitelist, pattern) ->
        if (!isWhitelist) return@forEach
        val path = projectDir.resolveFromRootOrRelative(pattern) ?: return@forEach
        val isWithinProject = path.path.startsWith(projectDir.path)
        if (!isWithinProject) {
            if (path.isDirectory) dirs.add(path)
            else callback(path)
        }
    }

    for (dir in dirs) {
        VfsUtilCore.visitChildrenRecursively(dir, object : VirtualFileVisitor<Unit>() {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file == projectDir) return true
                if (changeListManager.isIgnoredFile(file)) return false
                if (customIgnore != null && customIgnore.isIgnoredFile(file, projectDir)) return false

                if (!file.isDirectory) callback(file)
                return true
            }
        })
    }
}

fun Document.offsetToLogicalPosition(offset: Int): LogicalPosition {
    val line = getLineNumber(offset)
    val lineStart = getLineStartOffset(line)
    val column = offset - lineStart
    return LogicalPosition(line, column)
}

fun Document.logicalPositionToOffset(position: LogicalPosition): Int {
    val lineStart = getLineStartOffset(position.line)
    return lineStart + position.column
}

/**
 * Ensure the current file is writeable
 *
 * @return True if the file is writeable, false otherwise
 */
fun ensureFileWriteable(cx: HJActionContext): Boolean {
    val project = cx.hjEditor.editor.project!!
    val file = cx.hjEditor.editor.virtualFile
    val status = ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(listOf(file))
    return if (status.hasReadonlyFiles()) {
        cx.hjEditor.setWarning("Could not make file writeable")
        false
    } else true
}