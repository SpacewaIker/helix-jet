package io.github.spacewaiker.helixjet.input

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.project.Project
import io.github.spacewaiker.helixjet.actions.focusedEditor
import io.github.spacewaiker.helixjet.editor.HJState
import java.awt.AWTEvent
import java.awt.event.KeyEvent

/**
 * Main interceptor for keys pressed.
 *
 * Forwards valid inputs to HJInputHandler, or leaves HJTypedHandler handle Insert mode key presses.
 */
class HJKeyInterceptor(private val project: Project) : IdeEventQueue.EventDispatcher {
    private val hjState = HJState.getInstance(project)
    private val inputHandler = HJInputHandler.getInstance(project)

    override fun dispatch(e: AWTEvent): Boolean {
        if (!hjState.keymappingEnabled) return false
        if (e !is KeyEvent) return false
        if (e.id != KeyEvent.KEY_PRESSED) return false
        // get currently focused editor, or return if there are no currently focused editors
        val focusedEditor = project.focusedEditor ?: return false

        // don't respond to those key pressed by themselves
        if (e.keyCode == KeyEvent.VK_CONTROL || e.keyCode == KeyEvent.VK_ALT || e.keyCode == KeyEvent.VK_SHIFT) return false

        // typedHandlerBlock is set to true when transitioning to insert mode, such that the key used to transition isn't
        // also used by the typed handler. So any key pressed here removes the block
        hjState.typedHandlerBlock = false

        val inputKey = HJInputKey.fromKeyEvent(e)
        return inputKey != null && inputHandler.handle(focusedEditor, inputKey, e)
    }
}