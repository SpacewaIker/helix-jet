package spacewaiker.helixjet.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.psi.codeStyle.NameUtil
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
    private val historyRegister: HJRegister?,
    private val autocompleteProvider: List<String>?,
    private val autocompleteUi: HJPromptAutocomplete?,
    private val commandPopup: HJCommandPopup?,
) : JTextField() {
    private var registerIndex = historyRegister?.let { HJState.getRegisterContents(it)?.size } ?: 0
    private val historyEntry
        get() = historyRegister?.let { HJState.getRegisterContents(it)?.getOrNull(registerIndex) } ?: ""
    private val hint = historyRegister?.let { HJState.getRegisterContents(it)?.lastOrNull() } ?: ""
    private var isHintShown = false
    private var autocompleteChoices: List<String>? = null
    private var autocompleteIdx: Int? = null

    init {
        font = editor.colorsScheme.getFont(EditorFontType.PLAIN) // monospace
        border = BorderFactory.createEmptyBorder(2, 0, 2, 2)
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
        resetHint()
        if (isHintShown) onEvent(HJPromptEvent.Update, text)
        setFocusTraversalKeysEnabled(false) // allows catching Tab key events

        if (!autocompleteProvider.isNullOrEmpty()) {
            val height = autocompleteUi?.setNewEntries(autocompleteProvider)
            if (height != null && commandPopup != null) {
                commandPopup.bottomPosition = height
                commandPopup.forceRecomputePosition()
            }
        }
    }

    override fun processKeyEvent(e: KeyEvent) {
        var eventType: HJPromptEvent? = null

        // this needs to be handled in a special way because it only sends KeyReleased events
        if (e.keyCode == KeyEvent.VK_BACK_SPACE) {
            if (text.isEmpty()) {
                resetHint()
                // I believe helix wouldn't send an update here, but I don't see why not...
            }
            eventType = HJPromptEvent.Update
        }

        // below we also check for event ID because different keys use different key events. however, if we don't
        // check for these at all, we get duplicate events and send onEvent twice for some keys...
        when (e.keyCode) {
            KeyEvent.VK_ENTER if e.id == KeyEvent.KEY_PRESSED -> {
                historyRegister?.let { HJState.pushRegister(it, text) }
                eventType = HJPromptEvent.Validate
            }

            KeyEvent.VK_ESCAPE if e.id == KeyEvent.KEY_PRESSED -> {
                eventType = HJPromptEvent.Abort
            }

            KeyEvent.VK_UP if e.id == KeyEvent.KEY_PRESSED -> {
                registerIndex = (registerIndex - 1).coerceAtLeast(0)
                text = historyEntry
            }

            KeyEvent.VK_DOWN if e.id == KeyEvent.KEY_PRESSED -> {
                registerIndex = (registerIndex + 1).coerceAtMost(historyEntry.length)
                text = historyEntry
            }

            else if e.id == KeyEvent.KEY_TYPED -> {
                // not key released because otherwise this triggers when the key used to create this prompt is released
                if (isHintShown) {
                    text = ""
                    isHintShown = false
                }
                super.processKeyEvent(e)
                eventType = HJPromptEvent.Update
            }

            else -> {}
        }

        // autocomplete
        // needs to happen after so that it has the updated text field
        if (!autocompleteProvider.isNullOrEmpty()) {
            if (e.keyChar == '\t') {
                if (e.id == KeyEvent.KEY_PRESSED) {
                    autocompleteIdx = if (autocompleteIdx == null) {
                        if (e.isShiftDown) autocompleteChoices!!.size - 1 else 0
                    } else {
                        val change = if (e.isShiftDown) -1 else 1
                        (autocompleteIdx!! + change) % autocompleteChoices!!.size
                    }

                    text = autocompleteChoices!![autocompleteIdx!!]
                    autocompleteUi?.highlightEntry(autocompleteIdx!!)
                }
            } else if (e.keyCode != KeyEvent.VK_SHIFT && (e.keyCode == KeyEvent.VK_BACK_SPACE || e.id == KeyEvent.KEY_TYPED)) { // non-tab key typed
                val matcher = NameUtil.buildMatcher("*$text*").build()
                autocompleteChoices = autocompleteProvider.filter { matcher.matches(it) }
                    .sortedByDescending { matcher.matchingDegree(it) }
                val height = autocompleteUi?.setNewEntries(autocompleteChoices!!)
                if (height != null && commandPopup != null) {
                    commandPopup.bottomPosition = height
                }
            }
        }

        // send event after everything is done
        if (eventType != null) {
            onEvent(eventType, text)
        }
    }

    fun resetHint() {
        if (hint.isEmpty()) return
        isHintShown = true
        text = hint
        caretPosition = 0
    }
}

class HJPrompt(
    private val editor: Editor,
    private val onEvent: (HJPromptEvent, String?) -> Unit,
    prompt: String = ":",
    historyRegister: HJRegister? = null,
    autocompleteProvider: List<String>? = null,
    autocompleteUi: HJPromptAutocomplete? = null,
    commandPopup: HJCommandPopup? = null,
) {
    private val editorsComponent = (FileEditorManager.getInstance(editor.project!!) as FileEditorManagerEx).splitters

    private val promptCmp = JLabel(prompt).apply {
        font = editor.colorsScheme.getFont(EditorFontType.PLAIN)
        border = BorderFactory.createEmptyBorder(2, 2, 2, 0)
    }
    private val textField = HJPromptTextField(
        editor, ::onEventInternal, historyRegister, autocompleteProvider, autocompleteUi, commandPopup
    )

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
        editorsComponent.add(panel, BorderLayout.SOUTH)
        editorsComponent.revalidate()
        editorsComponent.repaint()
        textField.requestFocusInWindow()
        HJState.activePrompt = this
    }

    fun hide() {
        editorsComponent.remove(panel)
        editorsComponent.revalidate()
        editorsComponent.repaint()
        editor.contentComponent.requestFocusInWindow()
        HJState.activePrompt = null
    }

    fun requestFocus() {
        textField.requestFocusInWindow()
    }
}