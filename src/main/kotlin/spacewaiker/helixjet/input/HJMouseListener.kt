package spacewaiker.helixjet.input

import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import spacewaiker.helixjet.editor.HJEditorMode
import spacewaiker.helixjet.editor.HJRange
import spacewaiker.helixjet.editor.HJState
import spacewaiker.helixjet.editor.switchToMode

class HJMouseListener : EditorMouseListener {
    var anchor: Int = 0
    var head: Int = 0

    override fun mouseClicked(event: EditorMouseEvent) {
        val editor = event.editor

        if (HJState.mode == HJEditorMode.SELECT) switchToMode(editor, HJEditorMode.NORMAL)

        // override range with block-corrected location
        val pos = editor.xyToVisualPosition(event.mouseEvent.point)
        val center = editor.visualPositionToXY(pos)
        // if clicked left of center between chars, i.e. if actually pressed on prev char
        if (event.mouseEvent.point.x < center.x) {
            HJState.getHJEditor(editor).saveAndApply(HJRange.point(head - 1))
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

        HJState.getHJEditor(editor).saveAndApply(HJRange(anchor, head))
    }

    override fun mouseEntered(event: EditorMouseEvent) {}
    override fun mouseExited(event: EditorMouseEvent) {}
}