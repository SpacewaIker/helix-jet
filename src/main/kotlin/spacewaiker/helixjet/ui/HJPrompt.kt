package spacewaiker.helixjet.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import spacewaiker.helixjet.editor.HJRegister
import spacewaiker.helixjet.editor.HJState
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import javax.swing.*

enum class HJPromptEvent {
    Update, Validate, Abort
}

internal class HJPromptTextField(
    editor: Editor,
    private val onEvent: (HJPromptEvent, String?) -> Unit,
    historyRegister: HJRegister?,
) : JTextField() {
    private var historyString = ""

    init {
        font = editor.colorsScheme.getFont(EditorFontType.PLAIN) // monospace
        border = BorderFactory.createEmptyBorder(2, 0, 2, 2)
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground

        if (historyRegister != null) {
            val contents = HJState.getRegisterContents(historyRegister)
            if (contents != null) {
                historyString = contents[0]
            }
        }
        text = historyString
    }

    override fun processKeyEvent(e: KeyEvent) {
        when (e.keyCode) {
            KeyEvent.VK_ENTER -> {
                onEvent(HJPromptEvent.Validate, text)
            }

            KeyEvent.VK_ESCAPE -> {
                onEvent(HJPromptEvent.Abort, null)
            }

            KeyEvent.VK_BACK_SPACE if text.isEmpty() -> {
                text = historyString
                onEvent(HJPromptEvent.Update, text)
            }

            else -> {
                super.processKeyEvent(e)
                onEvent(HJPromptEvent.Update, text)
            }
        }
    }
}

class HJPrompt(
    private val editor: Editor,
    private val onEvent: (HJPromptEvent, String?) -> Unit,
    prompt: String = ":",
    historyRegister: HJRegister? = null,
) {
    private val promptCmp = JLabel(prompt).apply {
        font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        border = BorderFactory.createEmptyBorder(2, 2, 2, 0)
    }
    private val textField =
        HJPromptTextField(editor, ::onEventInternal, historyRegister)

    private val panel = JPanel(BorderLayout()).apply {
        add(promptCmp, BorderLayout.WEST)
        add(textField, BorderLayout.CENTER)
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground"))
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    }

    private fun onEventInternal(e: HJPromptEvent, command: String?) {
        onEvent(e, command)

        if (e == HJPromptEvent.Validate || e == HJPromptEvent.Abort)
            hide()
    }

    fun show() {
        val editorComponent = editor.component
        editorComponent.add(panel, BorderLayout.SOUTH)
        editorComponent.revalidate()
        textField.requestFocusInWindow()
    }

    fun hide() {
        val editorComponent = editor.component
        editorComponent.remove(panel)
        editorComponent.revalidate()
        editorComponent.repaint()
        editor.contentComponent.requestFocusInWindow()
    }
}