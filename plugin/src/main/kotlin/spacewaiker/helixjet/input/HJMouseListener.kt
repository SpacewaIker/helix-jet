package spacewaiker.helixjet.input

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import spacewaiker.helixjet.editor.HJRange
import spacewaiker.helixjet.editor.HJSelection
import spacewaiker.helixjet.editor.HJState

class HJMouseListener : EditorMouseListener {
    var anchor: Int = 0
    var head: Int = 0

    override fun mouseClicked(event: EditorMouseEvent) {
    }

    override fun mousePressed(event: EditorMouseEvent) {
        HJState.isMouseButtonDown = true
        val editor = event.editor
        val pos = editor.xyToVisualPosition(event.mouseEvent.point)
        val offset = editor.visualPositionToOffset(pos)
        anchor = offset
    }

    override fun mouseReleased(event: EditorMouseEvent) {
        HJState.isMouseButtonDown = false
        val editor = event.editor
        val pos = editor.xyToVisualPosition(event.mouseEvent.point)
        val offset = editor.visualPositionToOffset(pos)
        head = offset

        val selection = HJSelection(HJRange(anchor, head))
        HJState.getHJEditor(editor)?.setNewSelection(selection)

        // set focus back to HJPrompt, if applicable
        HJState.activePrompt?.requestFocus()
    }

    override fun mouseEntered(event: EditorMouseEvent) {}
    override fun mouseExited(event: EditorMouseEvent) {}
}