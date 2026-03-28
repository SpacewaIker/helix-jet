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
        // ensure invariants
        // Ensures the selection adheres to the following invariants:
        // 1. All ranges are grapheme aligned
        // 2. All ranges are at least 1 character wide, unless at the very end of the document
        // 3. Ranges are non-overlapping
        // 4. Ranges are sorted by their position in the text
        range = newRange.minWidth1(editor.document)

        editor.caretModel.moveToOffset(range.cursor)
        // in normal mode, don't show 1-width selections underneath the blinking block cursor
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