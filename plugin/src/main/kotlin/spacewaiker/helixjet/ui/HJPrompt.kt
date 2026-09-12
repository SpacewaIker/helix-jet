package spacewaiker.helixjet.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.psi.codeStyle.NameUtil
import spacewaiker.helixjet.editor.HJEditor
import spacewaiker.helixjet.editor.HJRegister
import spacewaiker.helixjet.editor.HJState
import java.awt.BorderLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import javax.swing.*

enum class HJPromptEvent {
    Update, Validate, Abort
}

internal class HJPromptTextField(
    private val hjEditor: HJEditor,
    private val onEvent: (HJPromptEvent, String?) -> Unit,
    private val historyRegister: HJRegister?,
    private val autocompleteProvider: List<String>?,
    private val autocompleteUi: HJPromptAutocomplete?,
    private val commandPopup: HJCommandPopup?,
) : JTextField() {
    private val hjState = HJState.getInstance(hjEditor.editor.project!!)
    private var registerIndex = 0// historyRegister?.let { hjState.registers.read(it, hjEditor)?.size } ?: 0
    private val historyEntry
        get() = historyRegister?.let { hjState.registers.read(it, hjEditor)?.getOrNull(registerIndex) } ?: ""
    private val hint = historyRegister?.let { hjState.registers.first(it, hjEditor) } ?: ""
    private var isHintShown = false
    private var autocompleteChoices: List<String>? = null
    private var autocompleteIdx: Int? = null

    init {
        font = hjEditor.editor.colorsScheme.getFont(EditorFontType.PLAIN) // monospace
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
                historyRegister?.let { hjState.registers.push(it, text, hjEditor) }
                eventType = HJPromptEvent.Validate
            }

            KeyEvent.VK_ESCAPE if e.id == KeyEvent.KEY_PRESSED -> {
                eventType = HJPromptEvent.Abort
            }

            KeyEvent.VK_DOWN if e.id == KeyEvent.KEY_PRESSED -> {
                registerIndex = (registerIndex - 1).coerceAtLeast(0)
                text = historyEntry
                isHintShown = false
            }

            KeyEvent.VK_UP if e.id == KeyEvent.KEY_PRESSED -> {
                if (!isHintShown) {
                    val historySize = historyRegister?.let { hjState.registers.read(it, hjEditor)?.size }
                    registerIndex = (registerIndex + 1).coerceAtMost(historySize?.minus(1) ?: 0)
                }
                text = historyEntry
                isHintShown = false
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
                autocompleteChoices = if (isHintShown) autocompleteProvider
                else autocompleteProvider.filter { matcher.matches(it) }
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
    private val hjEditor: HJEditor,
    private val onEvent: (HJPromptEvent, String?) -> Unit,
    prompt: String = ":",
    historyRegister: HJRegister? = null,
    autocompleteProvider: List<String>? = null,
    autocompleteUi: HJPromptAutocomplete? = null,
    commandPopup: HJCommandPopup? = null,
) {
    private val hjState = HJState.getInstance(hjEditor.editor.project!!)
    private val editorsComponent = FileEditorManagerEx.getInstanceEx(hjEditor.editor.project!!).splitters
    private val layeredPane = SwingUtilities.getRootPane(editorsComponent)?.layeredPane

    private val promptCmp = JLabel(prompt).apply {
        font = hjEditor.editor.colorsScheme.getFont(EditorFontType.PLAIN)
        border = BorderFactory.createEmptyBorder(2, 2, 2, 0)
    }
    private val textField = HJPromptTextField(
        hjEditor, ::onEventInternal, historyRegister, autocompleteProvider, autocompleteUi, commandPopup
    )

    private val panel = JPanel(BorderLayout()).apply {
        add(promptCmp, BorderLayout.WEST)
        add(textField, BorderLayout.CENTER)
        border = BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground"))
        background = EditorColorsManager.getInstance().globalScheme.defaultBackground
    }

    private val resizeListener = object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) = updateBounds()
        override fun componentMoved(e: ComponentEvent?) = updateBounds()
    }

    private fun onEventInternal(e: HJPromptEvent, command: String?) {
        onEvent(e, command)

        if (e == HJPromptEvent.Validate || e == HJPromptEvent.Abort)
            hide()
    }

    fun show() {
        if (layeredPane == null) return

        hjState.typedHandlerBlock = true

        updateBounds()

        layeredPane.add(panel, JLayeredPane.POPUP_LAYER as Any)
        layeredPane.revalidate()
        layeredPane.repaint()
        textField.requestFocusInWindow()
        hjState.activePrompt = this

        editorsComponent.addComponentListener(resizeListener)
    }

    fun hide() {
        if (layeredPane == null) return

        editorsComponent.removeComponentListener(resizeListener)

        layeredPane.remove(panel)
        layeredPane.revalidate()
        layeredPane.repaint()
        hjState.activePrompt = null
        ApplicationManager.getApplication().invokeLater {
            hjEditor.editor.contentComponent.requestFocusInWindow()
        }
    }

    fun requestFocus() {
        textField.requestFocusInWindow()
    }

    private fun updateBounds() {
        if (layeredPane == null) return
        val panelHeight = panel.preferredSize.height.takeIf { it > 0 } ?: 28

        // convert editorsComponent's bottom-left to layeredPane coordinates
        val origin =
            SwingUtilities.convertPoint(editorsComponent, 0, editorsComponent.height - panelHeight, layeredPane)
        panel.setBounds(origin.x, origin.y, editorsComponent.width, panelHeight)
    }

    companion object {
        val logger = Logger.getInstance(this::class.java)
    }
}