package spacewaiker.helixjet.input

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import spacewaiker.helixjet.actions.HJActionContext
import spacewaiker.helixjet.actions.switchToMode
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJRange
import spacewaiker.helixjet.editor.HJSelection
import spacewaiker.helixjet.editor.HJState

class HJMouseListener : EditorMouseListener {
    var anchor: Int = 0
    var head: Int = 0

    override fun mouseClicked(event: EditorMouseEvent) {
        val editor = event.editor

        val cx = HJActionContext(HJState.getHJEditor(editor) ?: return)
        if (HJState.mode == HJEditorMode.Select) switchToMode(cx, HJEditorMode.Normal)

        // override range with block-corrected location
        val pos = editor.xyToVisualPosition(event.mouseEvent.point)
        val center = editor.visualPositionToXY(pos)
        // if clicked left of center between chars, i.e. if actually pressed on prev char
        if (event.mouseEvent.point.x < center.x) {
            val selection = HJSelection(HJRange.point(head - 1))
            cx.hjEditor.setNewSelection(selection)
        }
    }

    override fun mousePressed(event: EditorMouseEvent) {
        val editor = event.editor
        val pos = editor.xyToVisualPosition(event.mouseEvent.point)
        val offset = editor.visualPositionToOffset(pos)
        anchor = offset
    }

    override fun mouseReleased(event: EditorMouseEvent) {
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