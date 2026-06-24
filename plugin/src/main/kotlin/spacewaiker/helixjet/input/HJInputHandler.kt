package spacewaiker.helixjet.input

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import spacewaiker.helixjet.actions.HJActionContext
import spacewaiker.helixjet.actions.HJKeyAction
import spacewaiker.helixjet.editor.HJEditor
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.input.HJInputHandler.actionCount
import spacewaiker.helixjet.input.HJInputHandler.lastKey
import spacewaiker.helixjet.input.HJInputHandler.minorModeName
import spacewaiker.helixjet.input.HJInputHandler.onNextKeyCallback
import spacewaiker.helixjet.input.HJInputHandler.pendingActionKey
import spacewaiker.helixjet.ui.HJStatusBarInputWidget
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
object HJInputHandler {
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
     * Handle the given input key.
     * Returns true if the key event was handled, false if it should be handled by the caller.
     */
    fun handle(editor: Editor, key: HJInputKey): Boolean {
        // record all key presses, including insert mode presses
        if (HJState.macroRecording != null) {
            val keys = HJState.macroRecording!!.second
            keys.add(key)
        }

        if (HJState.mode == HJEditorMode.Insert) {
            HJState.lastInsertKeystrokes.add(key)
            if (key is HJInputKey.Character) {
                return false
            }
        }

        lastKey = key

        // clear help widget
        if (minorModeName != null && !minorModeIsSticky) {
            HJState.helpWidget?.hide()
            HJState.helpWidget = null
        }

        // if there is a callback set for the next key, execute it instead of normal key handling
        if (onNextKeyCallback.isNotEmpty()) {
            onNextKeyCallback.remove().invoke(key)
            pendingActionKey = null
            minorModeName = null
            HJStatusBarInputWidget.updateInputWidget()
            return true
        }

        // proceed with normal key handling
        return handleKeyNormalBehaviour(editor, key)
    }

    private fun handleKeyNormalBehaviour(editor: Editor, key: HJInputKey): Boolean {
        // if the key is a number, we're inputting an action count
        if (key is HJInputKey.Character && key.char.isDigit()) {
            actionCount += key.char
            HJStatusBarInputWidget.updateInputWidget()
            return true
        }
        val count = if (actionCount.isEmpty()) null else actionCount.toInt().coerceAtLeast(1)

        var usingMinorMode = false
        val getKeyAction: (HJInputKey) -> HJKeyAction? = minorModeMapping?.let {
            usingMinorMode = true
            { inputKey -> it[inputKey] }
        } ?: { inputKey -> HJState.config.mapping[HJState.mode]!![inputKey] }

        when (val keyAction = getKeyAction(key)) {
            null -> {
                if (usingMinorMode) {
                    // any key not mapped in said minor mode will exit mode if minor mode is not sticky
                    // if it is sticky, only esc exits it
                    if (!minorModeIsSticky || (key is HJInputKey.Other && key.code == KeyEvent.VK_ESCAPE)) {
                        minorModeMapping = null
                        pendingActionKey = null
                        minorModeName = null
                        HJStatusBarInputWidget.updateInputWidget()

                        // clear help widget if we were in a sticky minor mode
                        if (minorModeIsSticky) {
                            HJState.helpWidget?.hide()
                            HJState.helpWidget = null
                        }
                    }
                } else {
                    logger.info("Could not find an action corresponding to $key in mode ${HJState.mode}")
                }
            }

            is HJKeyAction.HJAction -> {
                if (usingMinorMode && !minorModeIsSticky) minorModeMapping = null
                actionCount = ""

                val hjEditor = HJState.getHJEditor(editor)
                val cx = HJActionContext(hjEditor, count, HJState.selectedRegister)
                keyAction.execute(cx)

                // if action sets onNextKeyCallback, do not clear pending action key and minor mode name
                if (onNextKeyCallback.isEmpty()) {
                    pendingActionKey = null
                    if (usingMinorMode && !minorModeIsSticky) minorModeName = null
                }
                HJStatusBarInputWidget.updateInputWidget()
            }

            is HJKeyAction.HJMinorMode -> {
                pendingActionKey = if (pendingActionKey == null) lastKey.toString() else pendingActionKey + lastKey
                minorModeName = keyAction.name
                HJStatusBarInputWidget.updateInputWidget()
                minorModeMapping = keyAction.mapping
                minorModeIsSticky = keyAction.sticky

                HJState.getOrCreateHelpWidget(editor).setNewMinorMode(keyAction)
            }

            is HJKeyAction.HJPassthroughAction -> {
                logger.debug("Passthrough action")
                return false
            }
        }

        HJState.selectedRegister = null

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
            HJStatusBarInputWidget.updateInputWidget()
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
}