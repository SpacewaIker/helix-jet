package spacewaiker.helixjet.input

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import spacewaiker.helixjet.editor.HJRange
import spacewaiker.helixjet.editor.HJSelection
import spacewaiker.helixjet.editor.HJState

class HJMouseListener : EditorMouseListener {
    override fun mouseClicked(event: EditorMouseEvent) {}

    override fun mousePressed(event: EditorMouseEvent) {
        event.editor.project?.let { HJState.getInstance(it).isMouseButtonDown = true }
    }

    override fun mouseReleased(event: EditorMouseEvent) {
        val project = event.editor.project ?: return
        val hjState = HJState.getInstance(project)
        val editor = hjState.getHJEditor(event.editor) ?: return

        // here we use setNewSelection instead of ideSetNewSelection to ensure the cursor and selection are agreed
        if (event.mouseEvent.clickCount > 1) {
            // multi-click selection: sets the cursor one character after the actual selection so we subtract 1
            val selection = HJSelection(editor.editor.caretModel).transform { range ->
                HJRange(range.from, range.to - 1).withDirection(range.direction)
            }
            editor.setNewSelection(selection)
        } else {
            // single click or click and drag
            val selection = HJSelection(event.editor.caretModel)
            editor.setNewSelection(selection)
        }

        hjState.isMouseButtonDown = false
    }

    override fun mouseEntered(event: EditorMouseEvent) {}
    override fun mouseExited(event: EditorMouseEvent) {}
}