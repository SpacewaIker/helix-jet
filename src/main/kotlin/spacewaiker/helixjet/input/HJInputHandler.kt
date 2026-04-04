package spacewaiker.helixjet.input

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.wm.WindowManager
import spacewaiker.helixjet.editor.HJActionContext
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJKeyAction
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.ui.HJStatusBarInputWidget
import kotlin.math.max

sealed class HJInputKey {
    object Escape : HJInputKey()
    object Up : HJInputKey()
    object Down : HJInputKey()
    object Left : HJInputKey()
    object Right : HJInputKey()
    object Home : HJInputKey()
    object End : HJInputKey()
    object PageUp : HJInputKey()
    object PageDown : HJInputKey()
    object Space : HJInputKey()
    object Backspace : HJInputKey()
    object Tab : HJInputKey()
    object Del : HJInputKey()
    object Enter : HJInputKey()
    data class Character(val c: Char) : HJInputKey() {
        override fun toString(): String {
            return "$c"
        }
    }

    companion object {
        private val logger = Logger.getInstance(this::class.java)

        fun fromString(s: String): HJInputKey? {
            return when (s) {
                "esc" -> Escape
                "up" -> Up
                "down" -> Down
                "left" -> Left
                "right" -> Right
                "home" -> Home
                "end" -> End
                "pageup" -> PageUp
                "pagedown" -> PageDown
                "space" -> Space
                "backspace" -> Backspace
                "tab" -> Tab
                "del" -> Del
                "ret" -> Enter
                else if s.length == 1 -> Character(s[0])
                else -> {
                    logger.warn("Unknown character $s")
                    null
                }
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            Escape -> "Escape"
            Up -> "Up"
            Down -> "Down"
            Left -> "Left"
            Right -> "Right"
            Home -> "Home"
            End -> "End"
            PageUp -> "PageUp"
            PageDown -> "PageDown"
            Space -> "Space"
            Backspace -> "Backspace"
            Tab -> "Tab"
            Del -> "Del"
            Enter -> "Return"
            else -> ""
        }
    }
}

object HJInputHandler {
    private val logger = Logger.getInstance(this::class.java)
    private var onNextKeyCallback: ((HJInputKey) -> Unit)? = null
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
        if (onNextKeyCallback != null) {
            onNextKeyCallback?.invoke(key)
            onNextKeyCallback = null
            pendingActionKey = null
            updateInputWidget()
            return true
        }

        // proceed with normal key handling
        return handleKeyNormalBehaviour(editor, key)
    }

    private fun handleKeyNormalBehaviour(editor: Editor, key: HJInputKey): Boolean {
        // if the key is a number, we're inputting an action count
        if (key is HJInputKey.Character && key.c.isDigit()) {
            actionCount += key.c
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
                pendingActionKey = null
                minorModeName = null
                updateInputWidget()

                val hjEditor = HJState.getHJEditor(editor)
                val cx = HJActionContext(hjEditor, count, null)
                keyAction.execute(cx)
            }

            is HJKeyAction.HJMinorMode -> {
                pendingActionKey = if (pendingActionKey == null) lastKey.toString() else pendingActionKey + lastKey
                minorModeName = keyAction.name
                updateInputWidget()
                minorModeMapping = keyAction.mapping
            }
        }

        return true
    }

    /**
     * Set callback to be executed on next key press.
     * Also sets last pressed key as "pending key" indicator in widget.
     */
    fun onNextKey(callback: (HJInputKey) -> Unit) {
        if (onNextKeyCallback != null)
            logger.warn("onNextKeyCallback already defined, replacing with new value")

        onNextKeyCallback = callback

        lastKey?.let {
            pendingActionKey = it.toString()
            updateInputWidget()
        }
    }

    private fun updateInputWidget() {
        EditorFactory.getInstance().editorList.first().project?.let {
            WindowManager.getInstance().getStatusBar(it)?.updateWidget(HJStatusBarInputWidget.ID)
        }
    }
}