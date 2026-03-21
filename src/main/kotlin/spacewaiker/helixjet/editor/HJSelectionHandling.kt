package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Editor
import kotlin.math.max
import kotlin.math.min

fun extendSelectionWithMovement(editor: Editor, movement: (Editor) -> Unit) {
    // treat selection anchors as block anchors, so subtract 1 from selection end now and add 1 at the end
    val selectionStart = editor.selectionModel.selectionStart
    val selectionEnd =
        max(editor.selectionModel.selectionEnd - 1, selectionStart) // if empty selection, don't set end before start
    val previousCaret = editor.caretModel.primaryCaret.offset

    // perform movement
    movement(editor)

    val caret = editor.caretModel.primaryCaret.offset

    val otherAnchor = if (caret >= previousCaret) { // moving forward
        if (previousCaret == selectionEnd) { // moving end
            selectionStart
        } else { // moving start
            selectionEnd
        }
    } else { // moving backward
        if (previousCaret == selectionStart) { // moving start
            selectionEnd
        } else { // moving end
            selectionStart
        }
    }

    editor.selectionModel.setSelection(min(otherAnchor, caret), max(otherAnchor, caret) + 1)
}

fun extendSelection(editor: Editor) {
    val selectionStart = editor.selectionModel.selectionStart
    val selectionEnd = editor.selectionModel.selectionEnd
    val caret = editor.caretModel.primaryCaret.offset

    if (selectionEnd == caret) {
        editor.selectionModel.setSelection(selectionStart, selectionEnd + 1)
    }
}