package io.github.spacewaiker.helixjet.ui

import com.intellij.openapi.editor.Editor
import io.github.spacewaiker.helixjet.actions.HJCommand

private const val WIDTH = 700
private const val PROMPT_HEIGHT = 38

class HJCommandPopup(val editor: Editor) : HJFloatingTextarea(editor) {
    init {
        width = Pair(WIDTH, WIDTH)
        bottomSpacing = PROMPT_HEIGHT
    }

    var bottomPosition = PROMPT_HEIGHT
        set(value) {
            field = value
            bottomSpacing = value + PROMPT_HEIGHT
        }

    var command: HJCommand? = null

    fun setNewCommand(newCommand: HJCommand?) {
        command = newCommand

        panel.isVisible = newCommand != null

        recomputeElement()
    }

    fun forceRecomputePosition() = recomputeElement()

    override fun formatText(maxChars: Int): String {
        val command = command ?: return ""

        val sb = StringBuilder()

        // description
        sb.append(command.doc)

        // aliases
        if (command.aliases.isNotEmpty()) {
            sb.append("\nAliases: ")
            sb.append(command.aliases.joinToString(separator = ", "))
        }

        // flags
        if (command.flagsDoc.isNotEmpty()) {
            sb.append("\nFlags:\n  ")
            sb.append(command.flagsDoc.joinToString(separator = "\n  "))
        }

        return sb.toString()
    }
}