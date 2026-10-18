package io.github.spacewaiker.helixjet.input

import com.intellij.codeInsight.inline.completion.render.InlineCompletionLineRenderer
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import io.github.spacewaiker.helixjet.actions.HJActionContext
import io.github.spacewaiker.helixjet.actions.HJKeyAction
import io.github.spacewaiker.helixjet.editor.HJEditor
import io.github.spacewaiker.helixjet.editor.HJEditorMode
import io.github.spacewaiker.helixjet.editor.HJState
import io.github.spacewaiker.helixjet.ui.HJStatusBarInputWidget
import java.awt.event.KeyEvent
import java.util.*

/**
 * Main input handler.
 *
 * @property onNextKeyCallback Action to be performed on next key press instead of the normal key handling.
 * @property lastKey Last key that was pressed
 * @property pendingActionKey Keys pressed so far when we have a pending action
 * @property actionCount Numbers typed as part of the action count
 * @property minorModeName Name of the currently "active" minor mode
 */
@Service(Service.Level.PROJECT)
class HJInputHandler(private val project: Project) {
    private val logger = Logger.getInstance(this::class.java)
    private val onNextKeyCallback: Queue<((HJInputKey) -> Unit)> = LinkedList()
    private var lastKey: HJInputKey? = null
    var pendingActionKey: String? = null
        private set
    var actionCount: String = ""
        private set
    var minorModeName: String? = null
        private set
    private var minorModeMapping: Map<HJInputKey, HJKeyAction>? = null
    private var minorModeIsSticky: Boolean = false

    /**
     * Keys to treat as passthrough when an autocomplete popup is active in insert mode
     */
    private val autocompletePopupPassthroughKeys = listOf(
        HJInputKey.fromString("up"),
        HJInputKey.fromString("down"),
        HJInputKey.fromString("tab"),
        HJInputKey.fromString("ret"),
        HJInputKey.fromString("esc"),
    )
    private val autocompleteInlinePassthroughKeys = listOf(
        HJInputKey.fromString("tab"),
        HJInputKey.fromString("esc"),
    )

    /**
     * Handle the given input key.
     * Returns true if the key event was handled, false if it should be handled by the caller.
     */
    fun handle(editor: Editor, key: HJInputKey, event: KeyEvent): Boolean {
        val hjState = HJState.getInstance(project)

        // record all key presses, including insert mode presses
        if (hjState.macroRecording != null) {
            val keys = hjState.macroRecording!!.second
            keys.add(key)
        }

        if (hjState.mode == HJEditorMode.Insert) {
            hjState.lastInsertKeystrokes.add(key)
            if (key is HJInputKey.Character) {
                return false
            }
        }

        lastKey = key

        // clear help widget
        if (minorModeName != null && !minorModeIsSticky) {
            hjState.helpWidget?.hide()
            hjState.helpWidget = null
        }

        // if there is a callback set for the next key, execute it instead of normal key handling
        if (onNextKeyCallback.isNotEmpty()) {
            onNextKeyCallback.remove().invoke(key)
            pendingActionKey = null
            minorModeName = null
            hjState.helpWidget?.hide()
            hjState.helpWidget = null
            HJStatusBarInputWidget.getInstance(project).updateInputWidget()
            return true
        }

        // proceed with normal key handling
        return handleKeyNormalBehaviour(editor, key, event)
    }

    private fun handleKeyNormalBehaviour(editor: Editor, key: HJInputKey, event: KeyEvent): Boolean {
        val hjState = HJState.getInstance(project)

        // special case: do not handle up/down/tab/enter if autocomplete is up
        // @formatter:off
        if (hjState.mode == HJEditorMode.Insert) {
            if (LookupManager.getActiveLookup(editor) != null && autocompletePopupPassthroughKeys.contains(key)) return false

            val inlays = editor.inlayModel.getInlineElementsInRange(editor.caretModel.offset, editor.caretModel.offset)
            val hasInlineCompletion = inlays.any { it.renderer is InlineCompletionLineRenderer }
            if (hasInlineCompletion && autocompleteInlinePassthroughKeys.contains(key)) return false
        }
        // @formatter:on

        // if the key is a number, we're inputting an action count
        if (key is HJInputKey.Character && key.char.isDigit()) {
            actionCount += key.char
            HJStatusBarInputWidget.getInstance(project).updateInputWidget()
            return true
        }
        val count = if (actionCount.isEmpty()) null else actionCount.toInt().coerceAtLeast(1)

        var usingMinorMode = false
        val getKeyAction: (HJInputKey) -> HJKeyAction? = minorModeMapping?.let {
            usingMinorMode = true
            { inputKey -> it[inputKey] }
        } ?: { inputKey -> hjState.config.mapping[hjState.mode]!![inputKey] }

        when (val keyAction = getKeyAction(key)) {
            null -> {
                if (usingMinorMode) {
                    // any key not mapped in said minor mode will exit mode if minor mode is not sticky
                    // if it is sticky, only esc exits it
                    if (!minorModeIsSticky || (key is HJInputKey.Other && key.code == KeyEvent.VK_ESCAPE)) {
                        minorModeMapping = null
                        pendingActionKey = null
                        minorModeName = null
                        HJStatusBarInputWidget.getInstance(project).updateInputWidget()

                        // clear help widget if we were in a sticky minor mode
                        if (minorModeIsSticky) {
                            hjState.helpWidget?.hide()
                            hjState.helpWidget = null
                        }
                    }
                } else {
                    logger.info("Could not find an action corresponding to $key in mode ${hjState.mode}")
                }
            }

            is HJKeyAction.HJAction -> {
                if (usingMinorMode && !minorModeIsSticky) minorModeMapping = null
                actionCount = ""

                val hjEditor = hjState.getHJEditor(editor)
                if (hjEditor == null) {
                    logger.error("Got null when trying to get HJEditor for Editor")
                    return true
                }
                val cx = HJActionContext(hjState, hjEditor, count, hjState.selectedRegister, event)
                keyAction.execute(cx)

                // if action sets onNextKeyCallback, do not clear pending action key and minor mode name
                if (onNextKeyCallback.isEmpty()) {
                    pendingActionKey = null
                    if (usingMinorMode && !minorModeIsSticky) minorModeName = null
                }
                HJStatusBarInputWidget.getInstance(project).updateInputWidget()
            }

            is HJKeyAction.HJMinorMode -> {
                pendingActionKey = if (pendingActionKey == null) lastKey.toString() else pendingActionKey + lastKey
                minorModeName = keyAction.name
                HJStatusBarInputWidget.getInstance(project).updateInputWidget()
                minorModeMapping = keyAction.mapping
                minorModeIsSticky = keyAction.sticky

                hjState.getOrCreateHelpWidget(editor).setNewMinorMode(keyAction)
            }

            is HJKeyAction.HJPassthroughAction -> {
                logger.debug("Passthrough action")
                return false
            }
        }

        hjState.selectedRegister = null

        return true
    }

    /**
     * Set callback to be executed on next key press.
     * Also sets last pressed key as "pending key" indicator in widget.
     */
    fun onNextKey(callback: (HJInputKey) -> Unit) {
        onNextKeyCallback.add(callback)

        lastKey?.let { lastKey ->
            pendingActionKey = pendingActionKey?.let { it + lastKey.toString() } ?: lastKey.toString()
            HJStatusBarInputWidget.getInstance(project).updateInputWidget()
        }
    }

    fun sendInputKeyKeyEvent(hjEditor: HJEditor, inputKey: HJInputKey) {
        val eventQueue = IdeEventQueue.getInstance()

        // key pressed for events intercepted by HelixJet
        val keyPressedEvent = inputKey.toKeyEvent(hjEditor)
        eventQueue.dispatchEvent(keyPressedEvent)

        // key typed and released for typing characters in insert mode
        if (inputKey is HJInputKey.Character) {
            val keyTypedEvent = KeyEvent(
                keyPressedEvent.component,
                KeyEvent.KEY_TYPED,
                System.currentTimeMillis(),
                keyPressedEvent.modifiersEx,
                0,
                keyPressedEvent.keyChar,
            )
            eventQueue.dispatchEvent(keyTypedEvent)
            val keyReleasedEvent = KeyEvent(
                keyPressedEvent.component,
                KeyEvent.KEY_RELEASED,
                System.currentTimeMillis(),
                keyPressedEvent.modifiersEx,
                keyPressedEvent.keyCode,
                keyPressedEvent.keyChar,
            )
            eventQueue.dispatchEvent(keyReleasedEvent)
        }
    }

    companion object {
        fun getInstance(project: Project): HJInputHandler = project.getService(HJInputHandler::class.java)
    }
}