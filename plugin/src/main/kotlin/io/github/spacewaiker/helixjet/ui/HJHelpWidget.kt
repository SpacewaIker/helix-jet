package io.github.spacewaiker.helixjet.ui

import com.intellij.openapi.editor.Editor
import io.github.spacewaiker.helixjet.actions.HJKeyAction

private const val MIN_WIDTH = 400
private const val MAX_WIDTH = 700

class HJHelpWidget(editor: Editor) : HJFloatingTextarea(editor) {
    init {
        width = Pair(MIN_WIDTH, MAX_WIDTH)
        hAlignment = HAlignment.Right
    }

    var textRaw = emptyList<String>()
    var padding = 0

    fun setNewMinorMode(newMinorMode: HJKeyAction.HJMinorMode) {
        val lines = formatMinorMode(newMinorMode)
        setHelpContent(newMinorMode.name, lines)
    }

    fun setHelpContent(title: String, lines: List<Pair<String, String>>) {
        val (textRaw, padding) = formatHelpContent(title, lines)
        this.textRaw = textRaw
        this.padding = padding
        recomputeElement()
    }

    override fun formatText(maxChars: Int): String {
        val sb = StringBuilder()

        val availableWidth = maxChars - padding
        for (line in textRaw) {
            var line = line
            var addPadding = false
            while (line.isNotEmpty()) {
                if (addPadding) sb.repeat(" ", padding)
                addPadding = true
                sb.appendLine(line.take(availableWidth))
                line = line.drop(availableWidth)
            }
        }

        return sb.toString().removeSuffix("\n")
    }

    private fun formatMinorMode(minorMode: HJKeyAction.HJMinorMode): List<Pair<String, String>> {
        return minorMode.groupedMapping
            .filterNot { (action, _) -> action.name == "no_op" || action is HJKeyAction.HJPassthroughAction || (action is HJKeyAction.HJAction && !action.isImplemented) }
            .map { (action, keys) ->
                val keys = keys.joinToString(separator = ", ") { it.toString(false) }
                val action = when (action) {
                    is HJKeyAction.HJMinorMode -> action.name
                    is HJKeyAction.HJAction -> action.doc
                    else -> "" // filtered out
                }
                Pair(keys, action)
            }
    }

    private fun formatHelpContent(title: String, lines: List<Pair<String, String>>): Pair<List<String>, Int> {
        val outLines = mutableListOf<String>()

        // title
        outLines.add(title)
        outLines.add("")

        // lines
        // compute max length of first column to compute padding length
        val totalPadding = (lines.maxOfOrNull { it.first.length } ?: 0) + 2
        for ((keys, line) in lines) {
            val sb = StringBuilder()
            // add keys and padding
            sb.append(keys)
            sb.repeat(" ", totalPadding - keys.length)
            sb.append(line)

            outLines.add(sb.toString())
        }

        return Pair(outLines, totalPadding)
    }
}