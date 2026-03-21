package spacewaiker.helixjet.editor

import com.intellij.openapi.editor.Editor

enum class HJEditorMode {
    NORMAL, INSERT, SELECT,
}

object HJEditorState {
    var mode: HJEditorMode = HJEditorMode.NORMAL
    val config: HJConfiguration = HJConfiguration()
    private var ranges: MutableMap<Editor, HJRange> = mutableMapOf()

    fun putRange(editor: Editor, range: HJRange): HJRange {
        ranges[editor] = range
        return range
    }

    fun getRange(editor: Editor): HJRange = ranges.getOrPut(editor) { HJRange(editor) }
}