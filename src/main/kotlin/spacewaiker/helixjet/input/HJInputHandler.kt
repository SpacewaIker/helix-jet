package spacewaiker.helixjet.input

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.actions.HJActionContext
import spacewaiker.helixjet.actions.HJKeyAction
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.input.HJInputHandler.actionCount
import spacewaiker.helixjet.input.HJInputHandler.lastKey
import spacewaiker.helixjet.input.HJInputHandler.minorModeName
import spacewaiker.helixjet.input.HJInputHandler.onNextKeyCallback
import spacewaiker.helixjet.input.HJInputHandler.pendingActionKey
import spacewaiker.helixjet.ui.HJStatusBarInputWidget
import java.util.*
import kotlin.math.max

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

    /**
     * Handle the given input key.
     * Returns true if the key event was handled, false if it should be handled by the caller.
     */
    fun handle(editor: Editor, key: HJInputKey): Boolean {
        if (HJState.mode == HJEditorMode.INSERT && key is HJInputKey.Character) {
            return false
        }

        lastKey = key

        // if there is a callback set for the next key, execute it instead of normal key handling
        if (onNextKeyCallback.isNotEmpty()) {
            onNextKeyCallback.remove().invoke(key)
            pendingActionKey = null
            minorModeName = null
            updateInputWidget()
            return true
        }

        // proceed with normal key handling
        return handleKeyNormalBehaviour(editor, key)
    }

    private fun handleKeyNormalBehaviour(editor: Editor, key: HJInputKey): Boolean {
        // if the key is a number, we're inputting an action count
        if (key is HJInputKey.Character && key.char.isDigit()) {
            actionCount += key.char
            updateInputWidget()
            return true
        }
        var count: Int? = null
        if (!actionCount.isEmpty()) {
            // not a number, and action count is not empty, so we've finished entering our action count
            count = max(1, actionCount.toInt())
            actionCount = ""
            updateInputWidget()
        }

        var usingMinorMode = false
        val getKeyAction: (HJInputKey) -> HJKeyAction? = minorModeMapping?.let {
            usingMinorMode = true
            { inputKey -> it[inputKey] }
        } ?: { inputKey -> HJState.config.mapping[HJState.mode]!![inputKey] }
        when (val keyAction = getKeyAction(key)) {
            null -> {
                if (usingMinorMode) {
                    // any key not mapped in said minor mode will exit mode
                    minorModeMapping = null
                    pendingActionKey = null
                    minorModeName = null
                    updateInputWidget()
                } else {
                    logger.info("Could not find an action corresponding to $key in mode ${HJState.mode}")
                }
            }

            is HJKeyAction.HJAction -> {
                minorModeMapping = null

                val hjEditor = HJState.getHJEditor(editor)
                val cx = HJActionContext(hjEditor, count, null)
                keyAction.execute(cx)

                // if action sets onNextKeyCallback, do not clear pending action key and minor mode name
                if (onNextKeyCallback.isEmpty()) {
                    pendingActionKey = null
                    minorModeName = null
                }
                updateInputWidget()
            }

            is HJKeyAction.HJMinorMode -> {
                pendingActionKey = if (pendingActionKey == null) lastKey.toString() else pendingActionKey + lastKey
                minorModeName = keyAction.name
                updateInputWidget()
                minorModeMapping = keyAction.mapping
            }

            is HJKeyAction.HJPassthroughAction -> {
                logger.debug("Passthrough action")
                return false
            }
        }

        return true
    }

    /**
     * Set callback to be executed on next key press.
     * Also sets last pressed key as "pending key" indicator in widget.
     */
    fun onNextKey(callback: (HJInputKey) -> Unit) {
        onNextKeyCallback.add(callback)

        lastKey?.let {
            pendingActionKey += it.toString()
            updateInputWidget()
        }
    }

    private fun updateInputWidget() {
        EditorFactory.getInstance().editorList.first().project?.let {
            WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarInputWidget.ID)
        }
    }
}