package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Editor

class HJEditor {
    val editor: Editor
    var range: HJRange
    var lastMotion: ((HJEditor) -> Unit)? = null


    constructor(editor: Editor) {
        this.editor = editor
        this.range = HJRange(editor)
    }

    fun putRange(range: HJRange): HJRange {
        this.range = range
        return range
    }

    /**
     * Save new range to the HJState and apply it to this HJEditor.
     */
    fun saveAndApply(newRange: HJRange) {
        range = newRange

        editor.caretModel.moveToOffset(range.cursor)
        if (range.len <= 1 && HJState.mode == HJEditorMode.NORMAL) {
            editor.selectionModel.removeSelection()
        } else {
            editor.selectionModel.setSelection(range.from, range.to)
        }
    }

    fun applyMotion(motion: (HJEditor) -> Unit) {
        motion(this)
        lastMotion = motion
    }

    fun repeatLastMotion(count: Int) {
        if (lastMotion != null) {
            repeat(count) {
                lastMotion?.invoke(this)
            }
        }
    }
}