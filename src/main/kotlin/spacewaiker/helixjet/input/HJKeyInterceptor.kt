package spacewaiker.helixjet.input

import com.intellij.ide.IdeEventQueue
import spacewaiker.helixjet.editor.HJState
import java.awt.AWTEvent
import java.awt.event.KeyEvent

/**
 * Main interceptor for keys pressed.
 *
 * Forwards valid inputs to HJInputHandler, or leaves HJTypedHandler handle Insert mode key presses.
 */
class HJKeyInterceptor : IdeEventQueue.EventDispatcher {
    override fun dispatch(e: AWTEvent): Boolean {
        if (e !is KeyEvent) return false
        if (e.id != KeyEvent.KEY_PRESSED) return false
        // get currently focused editor, or return if there are no currently focused editors
        val focusedEditor = HJState.focusedEditor ?: return false

        // don't respond to those key pressed by themselves
        if (e.keyCode == KeyEvent.VK_CONTROL || e.keyCode == KeyEvent.VK_ALT || e.keyCode == KeyEvent.VK_SHIFT) return false

        // typedHandlerBlock is set to true when transitioning to insert mode, such that the key used to transition isn't
        // also used by the typed handler. So any key pressed here removes the block
        HJState.typedHandlerBlock = false

        val inputKey = HJInputKey.fromKeyEvent(e)
        return if (inputKey != null) HJInputHandler.handle(focusedEditor, inputKey) else false
    }
}